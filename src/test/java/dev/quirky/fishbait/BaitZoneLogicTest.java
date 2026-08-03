package dev.quirky.fishbait;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class BaitZoneLogicTest {

	@Test
	void insideWhenWithinRadius() {
		Vec3 zone = new Vec3(10.0, 63.0, 20.0);
		assertTrue(BaitZoneLogic.isInside(new Vec3(12.0, 63.5, 21.0), zone, 4.0));
		assertTrue(BaitZoneLogic.isInside(new Vec3(6.0, 63.2, 16.0), zone, 4.0));
	}

	@Test
	void outsideWhenBeyondRadius() {
		Vec3 zone = new Vec3(10.0, 63.0, 20.0);
		assertFalse(BaitZoneLogic.isInside(new Vec3(15.0, 63.5, 20.0), zone, 4.0));
		assertFalse(BaitZoneLogic.isInside(new Vec3(10.0, 63.5, 25.0), zone, 4.0));
	}

	@Test
	void exactlyAtBoundaryCountsInside() {
		Vec3 zone = new Vec3(10.0, 63.0, 20.0);
		assertTrue(BaitZoneLogic.isInside(new Vec3(14.0, 63.5, 20.0), zone, 4.0));
		assertTrue(BaitZoneLogic.isInside(new Vec3(10.0, 63.5, 24.0), zone, 4.0));
	}

	@Test
	void baseDurationIsSecondsTimesTwenty() {
		assertEquals(1800, BaitZoneLogic.durationTicks(90, false, true));
		assertEquals(1800, BaitZoneLogic.durationTicks(90, true, false));
		assertEquals(400, BaitZoneLogic.durationTicks(20, false, false));
	}

	@Test
	void rainingWithBonusBoostsByFiveThirds() {
		// 90s → 150s → 3000 tick；整数运算精确无浮点误差
		assertEquals(3000, BaitZoneLogic.durationTicks(90, true, true));
		assertEquals(666, BaitZoneLogic.durationTicks(20, true, true)); // 20*100/3 = 666
	}
}
