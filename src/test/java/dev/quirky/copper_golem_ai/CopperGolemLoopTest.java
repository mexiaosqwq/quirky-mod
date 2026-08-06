package dev.quirky.copper_golem_ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.quirky.config.QuirkyConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CopperGolemLoopTest {

	private static final String TOOL_RESPONSE = "{\"choices\":[{\"message\":{\"content\":null,\"tool_calls\":["
		+ "{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"get_world_info\",\"arguments\":\"{}\"}},"
		+ "{\"id\":\"call_2\",\"type\":\"function\",\"function\":{\"name\":\"get_self_status\",\"arguments\":\"{}\"}}"
		+ "]}}]}";

	private static final String REPLY_RESPONSE = "{\"choices\":[{\"message\":{\"content\":\"好了，我去看看\"}}]}";

	@Test
	void singleRoundReplyFinishesImmediately() throws Exception {
		var loop = new CopperGolemAgentLoop(new QuirkyConfig(), "你是铜傀儡", List.of(), "附近有什么");
		String reply = loop.run(body -> REPLY_RESPONSE, (name, args) -> "{}");
		assertEquals("好了，我去看看", reply);
		assertEquals(0, loop.rounds());
	}

	@Test
	void toolRoundThenReplyAppendsToolMessages() throws Exception {
		var loop = new CopperGolemAgentLoop(new QuirkyConfig(), "你是铜傀儡", List.of(), "附近有什么");
		String reply = loop.run(
			body -> {
				// 第二次请求应包含 assistant tool_calls + tool 结果消息
				JsonObject req = JsonParser.parseString(body).getAsJsonObject();
				JsonArray messages = req.getAsJsonArray("messages");
				int size = messages.size();
				if (size > 3) {
					JsonObject assistant = null;
					for (JsonElement el : messages) {
						JsonObject m = el.getAsJsonObject();
						if ("assistant".equals(m.get("role").getAsString()) && m.has("tool_calls")) {
							assistant = m;
							break;
						}
					}
					assertNotNull(assistant, "缺少 assistant tool_calls 消息");
					assertEquals(2, assistant.getAsJsonArray("tool_calls").size());
					JsonObject tool = messages.get(size - 1).getAsJsonObject();
					assertEquals("tool", tool.get("role").getAsString());
					assertEquals("call_2", tool.get("tool_call_id").getAsString());
				}
				return size > 3 ? REPLY_RESPONSE : TOOL_RESPONSE;
			},
			(name, args) -> "{\"time\":\"day\"}"
		);
		assertEquals("好了，我去看看", reply);
		assertEquals(1, loop.rounds());
	}

	@Test
	void twentyRoundsOfToolsHitsSafetyLimit() throws Exception {
		var loop = new CopperGolemAgentLoop(new QuirkyConfig(), "你是铜傀儡", List.of(), "一直查");
		String reply = loop.run(body -> TOOL_RESPONSE, (name, args) -> "{}");
		assertTrue(reply.contains("走神"));
		assertEquals(20, loop.rounds());
	}

	@Test
	void executorExceptionBecomesErrorResultAndLoopContinues() throws Exception {
		var loop = new CopperGolemAgentLoop(new QuirkyConfig(), "你是铜傀儡", List.of(), "查一下");
		String reply = loop.run(
			body -> {
				JsonArray messages = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("messages");
				if (messages.size() > 3) {
					// tool 结果应为 error JSON，循环继续
					JsonObject tool = messages.get(messages.size() - 1).getAsJsonObject();
					assertTrue(tool.get("content").getAsString().contains("error"));
					return REPLY_RESPONSE;
				}
				return TOOL_RESPONSE;
			},
			(name, args) -> { throw new RuntimeException("箱子被拆了"); }
		);
		assertEquals("好了，我去看看", reply);
		assertEquals(1, loop.rounds());
	}

	@Test
	void emptyReplyAndNoToolsUsesFallback() throws Exception {
		var loop = new CopperGolemAgentLoop(new QuirkyConfig(), "你是铜傀儡", List.of(), "说话");
		String reply = loop.run(body -> "{\"choices\":[{\"message\":{\"content\":null}}]}", (name, args) -> "{}");
		assertFalse(reply.isBlank());
	}
}
