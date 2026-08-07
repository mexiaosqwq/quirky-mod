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
	void parseHandSource() {
		var req = CopperGolemAiIntent.parse("{\"item\":\"minecraft:obsidian\",\"source\":\"hand\",\"destination\":\"-226,95,-605\"}");
		assertNotNull(req);
		assertEquals("hand", req.source());
		assertEquals("-226,95,-605", req.destination());
		var give = CopperGolemAiIntent.parse("{\"item\":\"minecraft:obsidian\",\"source\":\"hand\",\"destination\":\"give\"}");
		assertNotNull(give);
		assertEquals("give", give.destination());
		// hand 只能作来源
		assertNull(CopperGolemAiIntent.parse("{\"item\":\"x\",\"source\":\"copper\",\"destination\":\"hand\"}"));
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
		assertTrue(CopperGolemAiIntent.isPlausibleTarget("hand"));
		assertTrue(CopperGolemAiIntent.isPlausibleTarget("12,64,-8"));
		assertFalse(CopperGolemAiIntent.isPlausibleTarget(""));
		assertFalse(CopperGolemAiIntent.isPlausibleTarget("12,64"));
		assertFalse(CopperGolemAiIntent.isPlausibleTarget("somewhere"));
	}

	@Test
	void normalizeItemStripsCountSuffix() {
		assertEquals("minecraft:obsidian", CopperGolemAiIntent.normalizeItem("minecraft:obsidian×29"));
		assertEquals("minecraft:obsidian", CopperGolemAiIntent.normalizeItem("minecraft:obsidian"));
		assertEquals("any", CopperGolemAiIntent.normalizeItem("any"));
		assertEquals("", CopperGolemAiIntent.normalizeItem(null));
		// isPlausibleItem 容忍 ×N 后缀（AI 从感知结果带数量）
		assertTrue(CopperGolemAiIntent.isPlausibleItem("minecraft:obsidian×29"));
		assertTrue(CopperGolemAiIntent.isPlausibleItem("quirky:rope×3"));
		assertFalse(CopperGolemAiIntent.isPlausibleItem("minecraft:obsidian×abc"));
	}

	@Test
	void knownItemCheck() {
		Set<String> known = Set.of("minecraft:copper_ingot", "minecraft:iron_ingot");
		assertTrue(CopperGolemAiIntent.isKnownItem("minecraft:copper_ingot", known));
		assertTrue(CopperGolemAiIntent.isKnownItem("any", known));
		assertFalse(CopperGolemAiIntent.isKnownItem("minecraft:diamond", known));
	}

	@Test
	void actionIntentWords() {
		assertTrue(CopperGolemAiIntent.hasActionIntent("把黑曜石搬到末影箱"));
		assertTrue(CopperGolemAiIntent.hasActionIntent("帮我捡一下地上的东西"));
		assertTrue(CopperGolemAiIntent.hasActionIntent("跟着我"));
		assertTrue(CopperGolemAiIntent.hasActionIntent("整理一下末影箱")); // 整理/收拾/归类 → PENDING_GOAL + 硬校验
		assertTrue(CopperGolemAiIntent.hasActionIntent("帮我把箱子收拾好"));
		assertTrue(CopperGolemAiIntent.hasActionIntent("归类一下铜箱"));
		assertFalse(CopperGolemAiIntent.hasActionIntent("附近有什么"));
		assertFalse(CopperGolemAiIntent.hasActionIntent("你真棒"));
	}

	@Test
	void doneStatementWords() {
		assertTrue(CopperGolemAiIntent.isDoneStatement("搬完了，放好了"));
		assertTrue(CopperGolemAiIntent.isDoneStatement("搞定！"));
		assertTrue(CopperGolemAiIntent.isDoneStatement("末影箱整理好了"));
		assertFalse(CopperGolemAiIntent.isDoneStatement("我这就去搬"));
	}
}