package dev.quirky.copper_golem_ai;

import dev.quirky.copper_golem_ai.CopperGolemAiIntent.Target;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CopperGolemAiIntentTest {

	@Test
	void parseValidRequest() {
		var req = CopperGolemAiIntent.parse("{\"item\":\"minecraft:copper_ingot\",\"source\":\"targeted\",\"destination\":\"copper\"}");
		assertNotNull(req);
		assertEquals("minecraft:copper_ingot", req.item());
		assertEquals(Target.TARGETED, req.source());
		assertEquals(Target.COPPER, req.destination());
	}

	@Test
	void parseInvalidReturnsNull() {
		assertNull(CopperGolemAiIntent.parse("{\"item\":\"minecraft:copper_ingot\",\"source\":\"bogus\",\"destination\":\"copper\"}"));
		assertNull(CopperGolemAiIntent.parse("{\"item\":\"minecraft:copper_ingot\"}"));
		assertNull(CopperGolemAiIntent.parse("not json"));
		assertNull(CopperGolemAiIntent.parse("{\"item\":\"x\",\"source\":\"copper\",\"destination\":\"copper\"}")); // source==destination
	}

	@Test
	void anyItemIsPlausible() {
		assertTrue(CopperGolemAiIntent.isPlausibleItem("any"));
		assertTrue(CopperGolemAiIntent.isPlausibleItem("minecraft:copper_ingot"));
		assertTrue(CopperGolemAiIntent.isPlausibleItem("quirky:rope"));
		assertFalse(CopperGolemAiIntent.isPlausibleItem("铜锭"));
		assertFalse(CopperGolemAiIntent.isPlausibleItem(""));
	}
}
