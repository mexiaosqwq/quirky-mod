package dev.quirky.copper_golem_ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.quirky.config.QuirkyConfig;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 多轮 agent 循环（纯逻辑，可单测）：消息数组状态机 + 20 轮硬停。
 * API 调用与工具执行由调用方注入（Api/ToolExecutor 函数式接口），本类不碰网络/游戏。
 * 工具过程（tool_calls + 结果）只留在当次循环的消息数组，不进长期会话历史。
 */
public final class CopperGolemAgentLoop {
	/** 工程保险：单回合最多工具轮数（正常对话 1-4 轮）。 */
	public static final int MAX_ROUNDS = 20;
	/** 动作指令零工具的强制重试上限（仍零工具 → 放弃，AI 如实说）。 */
	public static final int MAX_ACTION_RETRIES = 2;
	/** 单轮对话感知工具调用上限（防反复 look 不行动；日志实锤一轮 look 18 次）。 */
	public static final int MAX_PERCEPTION_CALLS = 4;
	/** 20 轮硬停/空回复的兜底文本（心跳轮须静默，不播报）。 */
	public static final String FALLBACK_REPLY = "我有点走神了";

	@FunctionalInterface
	public interface Api {
		String call(String bodyJson) throws Exception;
	}

	@FunctionalInterface
	public interface ToolExecutor {
		String execute(String name, String argsJson) throws Exception;
	}

	private final QuirkyConfig config;
	private final JsonArray messages;
	private final String userText;
	private int rounds;
	private int actionRetries;
	private int emptyRetries; // 空响应（无 content 无 tool_calls）重试计数——模型抽风 ≠ 光说不做，先重试一次
	private int perceptionCalls; // 本轮感知工具调用计数（防反复 look 不行动）
	private boolean anyToolCalled; // 跨轮跟踪：是否调过任何行动工具（含失败）——调过 = 已动手，不算光说不做
	private @Nullable String lastReply;

	/** 初始化消息：system + 历史 + 本次用户输入。 */
	public CopperGolemAgentLoop(QuirkyConfig config, String systemPrompt, List<String> history, String userText) {
		this.config = config;
		this.userText = userText;
		this.messages = new JsonArray();
		addTextMessage("system", systemPrompt);
		for (String h : history) {
			addTextMessage(roleOf(h), contentOf(h));
		}
		addTextMessage("user", userText);
	}

	public JsonArray messages() {
		return messages;
	}

	public int rounds() {
		return rounds;
	}

	/** 跑完整循环直到纯回复或 20 轮硬停。工具执行异常 → error JSON 回传不中断。 */
	public String run(Api api, ToolExecutor executor) throws Exception {
		while (true) {
			if (rounds >= MAX_ROUNDS) {
				return lastReply != null ? lastReply : FALLBACK_REPLY;
			}
			String body = CopperGolemAiHttp.buildChatRequestFromMessages(config, messages);
			String response = api.call(body);
			List<CopperGolemAiHttp.ToolCall> calls = CopperGolemAiHttp.parseToolCalls(response);
			String reply = CopperGolemAiHttp.parseReply(response);
			if (calls.isEmpty()) {
				// 空响应兜底：模型返回既无 content 也无 tool_calls（异常/抽风）→ 重试一次再兜底，别让玩家看到"我有点走神了"
				if (reply == null && lastReply == null && emptyRetries < 1) {
					emptyRetries++;
					rounds++;
					continue;
				}
				String finalReply = reply != null ? reply : (lastReply != null ? lastReply : FALLBACK_REPLY);
				// 硬校验：玩家含动作意图 + 整个循环零工具（跨轮跟踪）+ 未宣布完成 → 追加重试轮（带工具指引），防"光说不做"
				// 调过工具（含失败）= 已动手，如实报告即可，不算光说不做（deep-fix 条款）
				if (actionRetries < MAX_ACTION_RETRIES && !anyToolCalled && CopperGolemAiIntent.hasActionIntent(userText)
					&& !CopperGolemAiIntent.isDoneStatement(finalReply)) {
					actionRetries++;
					String intent = userText.length() > 80 ? userText.substring(0, 80) + "…" : userText;
					addTextMessage("system", "玩家让你" + intent + "，但你从头到尾没调用任何工具——光说不做 = 失败。"
						+ "立即调用" + toolHintFor(userText) + "。");
					rounds++;
					continue;
				}
				return finalReply;
			}
			if (reply != null) {
				lastReply = reply;
			}
			List<String> results = new ArrayList<>();
			for (CopperGolemAiHttp.ToolCall call : calls) {
				if (CopperGolemAgentTools.isActionTool(call.name())) {
					anyToolCalled = true; // 调过行动工具（含失败）= 已动手；纯感知不算（防"说停下只 look 不 stop"漏检）
				}
				if (CopperGolemAgentTools.isPerceptionTool(call.name())) {
					perceptionCalls++;
					if (perceptionCalls > MAX_PERCEPTION_CALLS) {
						// 感知限次：重复看不会变结果，直接拦截（日志实锤：一轮 look 18 次不行动）
						results.add("{\"error\":\"你本轮已查看 " + MAX_PERCEPTION_CALLS + " 次（" + call.name()
							+ "），结果不会变——直接行动，或告诉玩家你看到了什么\"}");
						continue;
					}
				}
				try {
					results.add(executor.execute(call.name(), call.arguments()));
				} catch (Exception e) {
					results.add("{\"error\":\"" + e.getMessage() + "\"}");
				}
			}
			CopperGolemAiHttp.appendToolRound(messages, calls, results);
			rounds++;
		}
	}

	/** 动作词 → 应调用的工具指引。 */
	private static String toolHintFor(String userText) {
		if (userText.contains("整理") || userText.contains("归类") || userText.contains("收拾")) {
			return "organize_container（参数 container=容器坐标或 copper）";
		}
		if (userText.contains("捡") || userText.contains("收集") || userText.contains("打扫") || userText.contains("清理")) {
			return "collect_dropped_items（参数 range）";
		}
		if (userText.contains("跟")) {
			return "follow_player（参数 name=玩家名字）";
		}
		if (userText.contains("搬") || userText.contains("拿") || userText.contains("取") || userText.contains("给") || userText.contains("放")) {
			return "transport（参数 item/source/destination；把手上的物品放下时 source 写 hand，item 先 look_containers 查）";
		}
		return "move_to（参数 x,y,z）";
	}

	private void addTextMessage(String role, String content) {
		JsonObject m = new JsonObject();
		m.addProperty("role", role);
		m.addProperty("content", content);
		messages.add(m);
	}

	/** 历史行角色：player:/system: 前缀识别，其余 assistant；API 用 OpenAI 兼容名（user 非 player）。 */
	private static String roleOf(String historyLine) {
		if (historyLine.startsWith("player")) {
			return "user";
		}
		if (historyLine.startsWith("system")) {
			return "system";
		}
		return "assistant";
	}

	private static String contentOf(String historyLine) {
		int sep = historyLine.indexOf(": ");
		return sep > 0 ? historyLine.substring(sep + 2) : historyLine;
	}
}
