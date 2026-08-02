package dev.quirky.client.pick_range;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.quirky.TestBootstrap;
import dev.quirky.config.QuirkyConfig;
import dev.quirky.config.QuirkyConfigHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PickRangeHelperTest {
	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	@Test
	void creativeUsesPickRangeCreative() {
		assertEquals(100, PickRangeHelper.rangeFor(true));
	}

	@Test
	void survivalUsesPickRangeSurvival() {
		assertEquals(12, PickRangeHelper.rangeFor(false));
	}

	@Test
	void defaultsExtendBeyondVanilla() {
		// 原版：生存 4.5、创造 5.0
		assertTrue(PickRangeHelper.rangeFor(true) > PickRangeHelper.VANILLA_CREATIVE_RANGE);
		assertTrue(PickRangeHelper.rangeFor(false) > PickRangeHelper.VANILLA_SURVIVAL_RANGE);
		assertTrue(PickRangeHelper.isEnabled(true));
		assertTrue(PickRangeHelper.isEnabled(false));
	}

	@Test
	void rangeAtOrBelowVanillaDisables() {
		QuirkyConfig config = new QuirkyConfig();
		config.pickRangeCreative = 16; // 允许下限，仍大于创造原版 5.0
		config.pickRangeSurvival = 4; // 允许下限，小于生存原版 4.5
		QuirkyConfigHolder.set(config);
		try {
			assertTrue(PickRangeHelper.isEnabled(true));
			assertFalse(PickRangeHelper.isEnabled(false));
		} finally {
			QuirkyConfigHolder.set(new QuirkyConfig());
		}
	}
}
