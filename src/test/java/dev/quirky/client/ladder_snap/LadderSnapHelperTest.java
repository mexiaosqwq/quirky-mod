package dev.quirky.client.ladder_snap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LadderSnapHelperTest {

	@Test
	void lookUpClimbsUp() {
		assertEquals(LadderSnapHelper.CLIMB_SPEED, LadderSnapHelper.climbVelocity(-45.0F, false), 1e-9);
		assertEquals(LadderSnapHelper.CLIMB_SPEED, LadderSnapHelper.climbVelocity(-31.0F, false), 1e-9);
	}

	@Test
	void lookDownSlidesDown() {
		assertEquals(-0.15, LadderSnapHelper.climbVelocity(45.0F, false), 1e-9);
		assertEquals(-0.15, LadderSnapHelper.climbVelocity(31.0F, false), 1e-9);
	}

	@Test
	void levelViewHovers() {
		// 平视注入 0.08 抵消重力（travelInAir 每 tick -0.08 重力 × 0.98 摩擦 ≈ 0），
		// 否则会以 ≈0.078 b/t 滑落
		assertEquals(LadderSnapHelper.HOVER_SPEED, LadderSnapHelper.climbVelocity(0.0F, false), 1e-9);
		assertEquals(LadderSnapHelper.HOVER_SPEED, LadderSnapHelper.climbVelocity(-20.0F, false), 1e-9);
		assertEquals(LadderSnapHelper.HOVER_SPEED, LadderSnapHelper.climbVelocity(20.0F, false), 1e-9);
	}

	@Test
	void manualInputTakesPriority() {
		// 按了 W/S/空格/Shift 时返回 NaN，不干预手动爬梯
		assertTrue(Double.isNaN(LadderSnapHelper.climbVelocity(-45.0F, true)));
	}
}
