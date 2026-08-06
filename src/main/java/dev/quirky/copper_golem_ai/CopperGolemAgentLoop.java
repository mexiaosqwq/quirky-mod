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
	private static final String FALLBACK_REPLY = "我有点走神了";

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
	private int rounds;
	private @Nullable String lastReply;

	/** 初始化消息：system + 历史 + 本次用户输入。 */
	public CopperGolemAgentLoop(QuirkyConfig config, String systemPrompt, List<String> history, String userText) {
		this.config = config;
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
				return reply != null ? reply : (lastReply != null ? lastReply : FALLBACK_REPLY);
			}
			if (reply != null) {
				lastReply = reply;
			}
			List<String> results = new ArrayList<>();
			for (CopperGolemAiHttp.ToolCall call : calls) {
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
