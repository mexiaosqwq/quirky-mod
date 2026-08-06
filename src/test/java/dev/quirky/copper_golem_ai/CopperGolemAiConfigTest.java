package dev.quirky.copper_golem_ai;

import dev.quirky.config.QuirkyConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CopperGolemAiConfigTest {

	@Test
	void disabledWhenApiKeyOrModelEmpty() {
		QuirkyConfig c = new QuirkyConfig();
		c.aiApiKey = "";
		c.aiModel = "deepseek-chat";
		assertFalse(CopperGolemAiConfig.enabled(c));
		c.aiApiKey = "sk-123";
		c.aiModel = "";
		assertFalse(CopperGolemAiConfig.enabled(c));
		c.aiApiKey = "sk-123";
		c.aiModel = "deepseek-chat";
		assertTrue(CopperGolemAiConfig.enabled(c));
	}

	@Test
	void thinkingMapping() {
		QuirkyConfig c = new QuirkyConfig();
		c.aiThinking = "off";
		assertNull(CopperGolemAiConfig.reasoningEffort(c));
		c.aiThinking = "low";
		assertEquals("low", CopperGolemAiConfig.reasoningEffort(c));
		c.aiThinking = "medium";
		assertEquals("medium", CopperGolemAiConfig.reasoningEffort(c));
		c.aiThinking = "high";
		assertEquals("high", CopperGolemAiConfig.reasoningEffort(c));
		c.aiThinking = "xhigh";
		assertEquals("high", CopperGolemAiConfig.reasoningEffort(c));
		c.aiThinking = "max";
		assertEquals("high", CopperGolemAiConfig.reasoningEffort(c));
		c.aiThinking = "garbage";
		assertNull(CopperGolemAiConfig.reasoningEffort(c));
	}
}
