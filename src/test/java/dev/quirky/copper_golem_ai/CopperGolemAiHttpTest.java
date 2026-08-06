package dev.quirky.copper_golem_ai;

import com.google.gson.JsonParser;
import dev.quirky.config.QuirkyConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CopperGolemAiHttpTest {

	@Test
	void endpointNormalizesTrailingSlash() {
		QuirkyConfig c = new QuirkyConfig();
		c.aiBaseUrl = "https://api.example.com/v1/";
		assertEquals("https://api.example.com/v1/chat/completions", CopperGolemAiHttp.endpoint(c));
		c.aiBaseUrl = "https://api.example.com/v1";
		assertEquals("https://api.example.com/v1/chat/completions", CopperGolemAiHttp.endpoint(c));
	}

	@Test
	void chatRequestCarriesConfigAndThinking() {
		QuirkyConfig c = new QuirkyConfig();
		c.aiModel = "deepseek-chat";
		c.aiTemperature = 0.5F;
		c.aiMaxTokens = 128;
		c.aiThinking = "high";
		var root = JsonParser.parseString(
			CopperGolemAiHttp.buildChatRequest(c, List.of("player: 你好", "golem: 你好呀"), "今天天气如何")
		).getAsJsonObject();
		assertEquals("deepseek-chat", root.get("model").getAsString());
		assertEquals(0.5, root.get("temperature").getAsDouble());
		assertEquals(128, root.get("max_tokens").getAsInt());
		assertEquals(false, root.get("stream").getAsBoolean());
		assertEquals("high", root.get("reasoning_effort").getAsString());
		var messages = root.getAsJsonArray("messages");
		assertEquals(4, messages.size());
		assertEquals("system", messages.get(0).getAsJsonObject().get("role").getAsString());
		assertEquals("user", messages.get(3).getAsJsonObject().get("role").getAsString());
		assertEquals("今天天气如何", messages.get(3).getAsJsonObject().get("content").getAsString());
	}

	@Test
	void thinkingOffOmitsParam() {
		QuirkyConfig c = new QuirkyConfig();
		c.aiThinking = "off";
		var root = JsonParser.parseString(
			CopperGolemAiHttp.buildChatRequest(c, List.of(), "hi")
		).getAsJsonObject();
		assertFalse(root.has("reasoning_effort"));
	}

	@Test
	void summaryRequestUsesSummaryModel() {
		QuirkyConfig c = new QuirkyConfig();
		c.aiModel = "main-model";
		c.aiSummaryModel = "tiny-summarizer";
		var root = JsonParser.parseString(
			CopperGolemAiHttp.buildSummaryRequest(c, List.of("player: a", "golem: b"))
		).getAsJsonObject();
		assertEquals("tiny-summarizer", root.get("model").getAsString());
		assertEquals("system", root.getAsJsonArray("messages").get(0).getAsJsonObject().get("role").getAsString());
		assertTrue(root.get("max_tokens").getAsInt() >= 256);
	}

	@Test
	void summaryRequestFallsBackToMainModel() {
		QuirkyConfig c = new QuirkyConfig();
		c.aiModel = "main-model";
		c.aiSummaryModel = "";
		var root = JsonParser.parseString(
			CopperGolemAiHttp.buildSummaryRequest(c, List.of("player: a"))
		).getAsJsonObject();
		assertEquals("main-model", root.get("model").getAsString());
	}

	@Test
	void systemSummaryLineKeepsSystemRole() {
		QuirkyConfig c = new QuirkyConfig();
		var root = JsonParser.parseString(
			CopperGolemAiHttp.buildChatRequest(c, List.of("system: 摘要内容", "player: 你好"), "在吗")
		).getAsJsonObject();
		var messages = root.getAsJsonArray("messages");
		assertEquals("system", messages.get(0).getAsJsonObject().get("role").getAsString());
		assertEquals("system", messages.get(1).getAsJsonObject().get("role").getAsString());
	}

	@Test
	void parseReplyExtractsContent() {
		String json = "{\"choices\":[{\"message\":{\"content\":\"你好呀\"}}]}";
		assertEquals("你好呀", CopperGolemAiHttp.parseReply(json));
	}

	@Test
	void parseReplyHandlesMissingContent() {
		assertNull(CopperGolemAiHttp.parseReply("{\"choices\":[]}"));
		assertNull(CopperGolemAiHttp.parseReply("{\"error\":\"boom\"}"));
		assertNull(CopperGolemAiHttp.parseReply("not json"));
	}
}
