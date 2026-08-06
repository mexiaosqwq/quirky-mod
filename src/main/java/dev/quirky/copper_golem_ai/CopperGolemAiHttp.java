package dev.quirky.copper_golem_ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.quirky.config.QuirkyConfig;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * OpenAI 兼容 chat/completions 请求构造与响应解析（纯逻辑，可单测）。
 * 非流式（stream=false）；思考等级按配置映射（见 CopperGolemAiConfig）。
 */
public final class CopperGolemAiHttp {
	private static final Gson GSON = new Gson();

	/** 铜傀儡人设 system prompt（保持精简，<500 token）。V1 指令格式确定后更新。 */
	public static final String SYSTEM_PROMPT =
		"你是一个生活在 Minecraft 世界里的铜傀儡，勤快的物品搬运工。"
			+ "用中文简短回复（一两句话），可以开轻松的玩笑。"
			+ "听到玩家的话先回应聊天，搬运用途暂未开放。";

	private CopperGolemAiHttp() {
	}

	/** baseUrl 去尾斜杠 + /chat/completions。 */
	public static String endpoint(QuirkyConfig c) {
		String base = c.aiBaseUrl == null ? "" : c.aiBaseUrl.trim();
		while (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		return base + "/chat/completions";
	}

	/** 对话请求体：system + 历史 + 本次。 */
	public static String buildChatRequest(QuirkyConfig c, List<String> history, String userText) {
		JsonObject body = baseBody(c);
		JsonArray messages = new JsonArray();
		addMessage(messages, "system", SYSTEM_PROMPT);
		for (String h : history) {
			addMessage(messages, roleOf(h), contentOf(h));
		}
		addMessage(messages, "user", userText);
		body.add("messages", messages);
		return GSON.toJson(body);
	}

	/** 压缩请求体：system 压缩指令 + 全量历史，model 用 summaryModel（空则主 model）。 */
	public static String buildSummaryRequest(QuirkyConfig c, List<String> history) {
		JsonObject body = baseBody(c);
		String model = c.aiSummaryModel != null && !c.aiSummaryModel.isBlank() ? c.aiSummaryModel : c.aiModel;
		body.addProperty("model", model);
		body.addProperty("max_tokens", 512);
		JsonArray messages = new JsonArray();
		addMessage(messages, "system",
			"把下面的对话压缩成一段简洁的中文摘要，保留关键信息：物品名、容器位置、玩家提出但未完成的要求。只输出摘要，不要输出其他内容。");
		for (String h : history) {
			addMessage(messages, roleOf(h), contentOf(h));
		}
		body.add("messages", messages);
		return GSON.toJson(body);
	}

	/** 历史行角色：player: / system: 前缀识别，其余视为 assistant；发往 API 统一用 OpenAI 兼容名（user 非 player）。 */
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

	private static JsonObject baseBody(QuirkyConfig c) {
		JsonObject body = new JsonObject();
		body.addProperty("model", c.aiModel);
		body.addProperty("temperature", c.aiTemperature);
		body.addProperty("max_tokens", c.aiMaxTokens);
		body.addProperty("stream", false);
		String reasoning = CopperGolemAiConfig.reasoningEffort(c);
		if (reasoning != null) {
			body.addProperty("reasoning_effort", reasoning);
		}
		return body;
	}

	private static void addMessage(JsonArray messages, String role, String content) {
		JsonObject m = new JsonObject();
		m.addProperty("role", role);
		m.addProperty("content", content);
		messages.add(m);
	}

	/** 提取 choices[0].message.content；任何缺失/异常返回 null。 */
	public static @Nullable String parseReply(String responseJson) {
		try {
			JsonObject root = JsonParser.parseString(responseJson).getAsJsonObject();
			JsonArray choices = root.getAsJsonArray("choices");
			if (choices == null || choices.isEmpty()) {
				return null;
			}
			JsonElement content = choices.get(0).getAsJsonObject().getAsJsonObject("message").get("content");
			if (content == null || content.isJsonNull()) {
				return null;
			}
			String text = content.getAsString();
			return text.isBlank() ? null : text;
		} catch (RuntimeException e) {
			return null;
		}
	}
}
