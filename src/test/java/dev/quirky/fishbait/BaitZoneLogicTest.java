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

	@Test
	void extraLureDecrementKeepsFloorAtOne() {
		// clamp ≥1：值 1/2/3 一律压到 1，不让原版看到 0（0 会触发 else 重掷、进度丢失）
		assertEquals(1, BaitZoneLogic.extraLureDecrement(1));
		assertEquals(1, BaitZoneLogic.extraLureDecrement(2));
		assertEquals(1, BaitZoneLogic.extraLureDecrement(3));
		assertEquals(5, BaitZoneLogic.extraLureDecrement(7));
	}

	@Test
	void lureSequenceAcceleratesToThirdWithoutReroll() {
		// 真实原版语义模拟：值≤0 时 catchingFish 走 else 分支重掷（进度丢失）。
		// 修复前（clamp 0）：≡0/≡2 (mod 3) 的起始值会被额外递减打成 0 → 下一 tick 原版重掷，
		// 期望时长≈原版（无加速，即用户实测的"钓鱼速率没变"）。
		// 修复后（clamp ≥1）：任何起始值都经原版 -1 恰好落到 0 触发咬钩转换，无重掷，≈3× 加速。
		for (int start = 300; start <= 600; start++) {
			int value = start;
			int ticks = 0;
			int rerolls = 0;
			while (ticks < 10000) {
				ticks++;
				if (value <= 0) {
					value = 300; // 原版 else 重掷（等价语义：进度丢失）
					rerolls++;
					continue;
				}
				value--; // 原版 -1
				if (value <= 0) {
					break; // 转换到咬钩阶段，完成
				}
				value = BaitZoneLogic.extraLureDecrement(value); // 区内额外递减
			}
			assertEquals(0, rerolls, "start=" + start + " 不应重掷");
			assertTrue(ticks <= Math.ceil(start / 3.0) + 2, "start=" + start + " ticks=" + ticks);
		}
	}
}
