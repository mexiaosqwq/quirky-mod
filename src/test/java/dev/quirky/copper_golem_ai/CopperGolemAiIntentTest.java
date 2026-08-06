package dev.quirky.copper_golem_ai;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CopperGolemAiIntentTest {

	@Test
	void parseValidRequest() {
		var req = CopperGolemAiIntent.parse("{\"item\":\"minecraft:copper_ingot\",\"source\":\"copper\",\"destination\":\"12,64,-8\"}");
		assertNotNull(req);
		assertEquals("minecraft:copper_ingot", req.item());
		assertEquals("copper", req.source());
		assertEquals("12,64,-8", req.destination());
	}

	@Test
	void parseGiveDestination() {
		var req = CopperGolemAiIntent.parse("{\"item\":\"minecraft:copper_ingot\",\"source\":\"copper\",\"destination\":\"give\"}");
		assertNotNull(req);
		assertEquals("give", req.destination());
	}

	@Test
	void parseInvalidReturnsNull() {
		assertNull(CopperGolemAiIntent.parse("{\"item\":\"minecraft:copper_ingot\",\"source\":\"bogus\",\"destination\":\"copper\"}"));
		assertNull(CopperGolemAiIntent.parse("{\"item\":\"minecraft:copper_ingot\"}"));
		assertNull(CopperGolemAiIntent.parse("not json"));
		assertNull(CopperGolemAiIntent.parse("{\"item\":\"x\",\"source\":\"copper\",\"destination\":\"copper\"}")); // 相同
		assertNull(CopperGolemAiIntent.parse("{\"item\":\"x\",\"source\":\"give\",\"destination\":\"copper\"}")); // give 不能是来源
	}

	@Test
	void anyItemIsPlausible() {
		assertTrue(CopperGolemAiIntent.isPlausibleItem("any"));
		assertTrue(CopperGolemAiIntent.isPlausibleItem("minecraft:copper_ingot"));
		assertTrue(CopperGolemAiIntent.isPlausibleItem("quirky:rope"));
		assertFalse(CopperGolemAiIntent.isPlausibleItem("铜锭"));
		assertFalse(CopperGolemAiIntent.isPlausibleItem(""));
	}

	@Test
	void targetsValidate() {
		assertTrue(CopperGolemAiIntent.isPlausibleTarget("copper"));
		assertTrue(CopperGolemAiIntent.isPlausibleTarget("give"));
		assertTrue(CopperGolemAiIntent.isPlausibleTarget("12,64,-8"));
		assertFalse(CopperGolemAiIntent.isPlausibleTarget(""));
		assertFalse(CopperGolemAiIntent.isPlausibleTarget("12,64"));
		assertFalse(CopperGolemAiIntent.isPlausibleTarget("somewhere"));
	}

	@Test
	void knownItemCheck() {
		Set<String> known = Set.of("minecraft:copper_ingot", "minecraft:iron_ingot");
		assertTrue(CopperGolemAiIntent.isKnownItem("minecraft:copper_ingot", known));
		assertTrue(CopperGolemAiIntent.isKnownItem("any", known));
		assertFalse(CopperGolemAiIntent.isKnownItem("minecraft:diamond", known));
	}
}
