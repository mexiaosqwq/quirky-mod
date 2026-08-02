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
		assertEquals(-LadderSnapHelper.CLIMB_SPEED, LadderSnapHelper.climbVelocity(45.0F, false), 1e-9);
		assertEquals(-LadderSnapHelper.CLIMB_SPEED, LadderSnapHelper.climbVelocity(31.0F, false), 1e-9);
	}

	@Test
	void levelViewHovers() {
		assertEquals(0.0, LadderSnapHelper.climbVelocity(0.0F, false), 1e-9);
		assertEquals(0.0, LadderSnapHelper.climbVelocity(-20.0F, false), 1e-9);
		assertEquals(0.0, LadderSnapHelper.climbVelocity(20.0F, false), 1e-9);
	}

	@Test
	void manualInputTakesPriority() {
		// 按了 W/S/空格/Shift 时返回 NaN，不干预手动爬梯
		assertTrue(Double.isNaN(LadderSnapHelper.climbVelocity(-45.0F, true)));
	}
}
