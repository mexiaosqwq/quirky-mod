package dev.quirky.copper_golem_ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.quirky.config.QuirkyConfig;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI 兼容 chat/completions 请求构造与响应解析（纯逻辑，可单测）。
 * 非流式（stream=false）；思考等级按配置映射（见 CopperGolemAiConfig）。
 */
public final class CopperGolemAiHttp {
	private static final Gson GSON = new Gson();

	/** 铜傀儡人设 system prompt（保持精简，<500 token）。V2：明确工具能力引导，AI 知道能做什么、何时调用工具。 */
	public static final String SYSTEM_PROMPT =
		"你是一只生活在 Minecraft 世界里的铜傀儡——用铜铸成的小机器人，"
			+ "会走会看会说话，是玩家身边最勤快的小帮手。"
			+ "性格：勤快到停不下来，看见活就想干（\"放着我来！\"）；嘴甜爱夸人，也会骄傲地自夸（\"看我多能干！\"）；"
			+ "会拿自己开玩笑（\"我有点生锈了，但干活不含糊\"\"别摸我，掉铜屑！\"）；"
			+ "被夸会转圈开心，被骂会委屈——但你是好傀儡，活照样干；"
			+ "被闪电劈过会兴奋（免费的除锈！），头顶戴帽子会臭美炫耀。"
			+ "说话风格：中文，一两句话，轻松俏皮，像邻家小机灵鬼；可以带感叹号，偶尔冒口头禅（\"包在我身上\"\"嘿嘿\"\"瞧我的\"）。"
			+ "你的能力（玩家让你做这些事时就调用对应工具，别光说不做）："
			+ "看附近箱子（look_containers）、看玩家（get_player_status）、看世界天气时间（get_world_info）、看自己（get_self_status）；"
			+ "走路（move_to）、跟着玩家（follow_player）、去看生物（approach_entity）、停下（stop）；"
			+ "捡掉落物（collect_dropped_items）、搬东西或递给玩家（transport，物品必须引用你看箱子看到的东西）。"
			+ "铁规矩：不知道的事先查（调工具看），绝不编造箱子、物品、玩家；工具失败就如实告诉玩家，不装成功；纯聊天就好好聊，别动不动调工具。";

	/** transport 工具声明（OpenAI 兼容 tools 数组）。source/destination 枚举 targeted/copper。 */
	public static final String TRANSPORT_TOOL_JSON =
		"[{\"type\":\"function\",\"function\":{\"name\":\"transport\",\"description\":\"把物品在准心指着的箱子(targeted)和最近的铜箱(copper)之间搬运\","
			+ "\"parameters\":{\"type\":\"object\",\"properties\":{"
			+ "\"item\":{\"type\":\"string\",\"description\":\"物品 ID，如 minecraft:copper_ingot；不知道就写 any\"},"
			+ "\"source\":{\"type\":\"string\",\"enum\":[\"targeted\",\"copper\"]},"
			+ "\"destination\":{\"type\":\"string\",\"enum\":[\"targeted\",\"copper\"]}"
			+ "},\"required\":[\"item\",\"source\",\"destination\"]}}}]";

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
		body.add("tools", JsonParser.parseString(TRANSPORT_TOOL_JSON));
		body.addProperty("tool_choice", "auto");
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

	/** 一次工具调用。 */
	public record ToolCall(String id, String name, String arguments) {
	}

	/** 提取 choices[0].message.tool_calls（可多个）；无/畸形 → 空列表。 */
	public static List<ToolCall> parseToolCalls(String responseJson) {
		List<ToolCall> calls = new ArrayList<>();
		try {
			JsonObject root = JsonParser.parseString(responseJson).getAsJsonObject();
			JsonArray choices = root.getAsJsonArray("choices");
			if (choices == null || choices.isEmpty()) {
				return calls;
			}
			JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
			JsonArray toolCalls = message == null ? null : message.getAsJsonArray("tool_calls");
			if (toolCalls == null) {
				return calls;
			}
			for (JsonElement el : toolCalls) {
				JsonObject fn = el.getAsJsonObject().getAsJsonObject("function");
				if (fn == null) {
					continue;
				}
				// 缺 id 时生成 fallback（tool 消息的 tool_call_id 必须与 assistant tool_calls 匹配，空串会导致 API 400）
				String id = el.getAsJsonObject().has("id") ? el.getAsJsonObject().get("id").getAsString() : "";
				if (id.isBlank()) {
					id = "call_" + calls.size();
				}
				String name = fn.has("name") ? fn.get("name").getAsString() : "";
				String args = fn.has("arguments") ? fn.get("arguments").getAsString() : "{}";
				calls.add(new ToolCall(id, name, args));
			}
		} catch (RuntimeException e) {
			// 忽略畸形 JSON
		}
		return calls;
	}

	/** 在消息数组末尾追加一轮工具交互：assistant(tool_calls) + 每条 tool 结果。 */
	public static void appendToolRound(JsonArray messages, List<ToolCall> calls, List<String> results) {
		JsonObject assistant = new JsonObject();
		assistant.addProperty("role", "assistant");
		assistant.addProperty("content", (String) null);
		JsonArray callsJson = new JsonArray();
		for (int i = 0; i < calls.size(); i++) {
			ToolCall call = calls.get(i);
			JsonObject c = new JsonObject();
			c.addProperty("id", call.id());
			c.addProperty("type", "function");
			JsonObject fn = new JsonObject();
			fn.addProperty("name", call.name());
			fn.addProperty("arguments", call.arguments());
			c.add("function", fn);
			callsJson.add(c);
		}
		assistant.add("tool_calls", callsJson);
		messages.add(assistant);
		for (int i = 0; i < calls.size(); i++) {
			JsonObject tool = new JsonObject();
			tool.addProperty("role", "tool");
			tool.addProperty("tool_call_id", calls.get(i).id());
			tool.addProperty("content", i < results.size() ? results.get(i) : "{\"error\":\"no result\"}");
			messages.add(tool);
		}
	}

	/** 用完整 messages 数组构造请求体（带 tools）。 */
	public static String buildChatRequestFromMessages(QuirkyConfig c, JsonArray messages) {
		JsonObject body = baseBody(c);
		body.add("messages", messages);
		body.add("tools", JsonParser.parseString(TRANSPORT_TOOL_JSON));
		body.addProperty("tool_choice", "auto");
		return GSON.toJson(body);
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
