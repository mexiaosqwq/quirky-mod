package dev.quirky.boomerang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.quirky.TestBootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 连续进动弧线物理模型单测。
 * 模型:每帧 precess(水平偏转) → converge(朝投掷者收敛) → modulateSpeed(远端减速) → heightVelocity(高度起伏),
 * 位置 step 线性积分。全程连续无硬切换,返程由进动+收敛自然产生。
 */
class BoomerangPhysicsTest {

	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	// ==== 线性步进(保留) ====

	@Test
	void stepIntegratesPositionLinearly() {
		Vec3 pos = new Vec3(1.0, 2.0, 3.0);
		Vec3 vel = new Vec3(1.0, 0.0, 0.0);
		Vec3 next = BoomerangPhysics.step(pos, vel, 1.0);
		assertEquals(2.0, next.x, 1e-9);
		assertEquals(2.0, next.y, 1e-9);
		assertEquals(3.0, next.z, 1e-9);
	}

	@Test
	void stepScalesByDt() {
		Vec3 pos = new Vec3(0.0, 0.0, 0.0);
		Vec3 vel = new Vec3(2.0, 0.0, 1.0);
		Vec3 next = BoomerangPhysics.step(pos, vel, 0.5);
		assertEquals(1.0, next.x, 1e-9);
		assertEquals(0.5, next.z, 1e-9);
	}

	// ==== 进动偏转 precess ====

	@Test
	void precessKeepsSpeedAndVerticalComponent() {
		Vec3 vel = new Vec3(1.0, 0.5, 0.0);
		Vec3 r = BoomerangPhysics.precess(vel, 0.12, true);
		assertEquals(vel.length(), r.length(), 1e-9, "speed must be preserved");
		assertEquals(0.5, r.y, 1e-9, "vertical component unchanged");
	}

	@Test
	void precessClockwiseTurnsTowardRightHandSide() {
		// 朝 +Z 速度,顺时针(俯视)= 投掷者右手边 → 应产生 -X 分量(面南右手是西)
		Vec3 vel = new Vec3(0.0, 0.0, 1.0);
		Vec3 r = BoomerangPhysics.precess(vel, 0.12, true);
		assertTrue(r.x < -1e-6, "clockwise should turn +Z toward -X (right hand), got x=" + r.x);
		assertTrue(r.z > 0, "should still mostly face +Z");
	}

	@Test
	void precessCounterClockwiseIsMirror() {
		Vec3 vel = new Vec3(0.0, 0.0, 1.0);
		Vec3 cw = BoomerangPhysics.precess(vel, 0.12, true);
		Vec3 ccw = BoomerangPhysics.precess(vel, 0.12, false);
		assertTrue(cw.x * ccw.x < 0, "opposite directions must produce opposite lateral x");
	}

	@Test
	void precessZeroRateIsIdentity() {
		Vec3 vel = new Vec3(0.3, -0.2, 0.7);
		assertEquals(vel, BoomerangPhysics.precess(vel, 0.0, true));
	}

	// ==== 朝投掷者收敛 converge ====

	@Test
	void convergeKeepsSpeedAndVerticalComponent() {
		Vec3 vel = new Vec3(1.0, 0.3, 0.0);
		Vec3 r = BoomerangPhysics.converge(vel, new Vec3(0, 0, 0), new Vec3(5, 0, 5), 0.1);
		assertEquals(vel.length(), r.length(), 1e-9, "speed preserved");
		assertEquals(0.3, r.y, 1e-9, "vertical unchanged");
	}

	@Test
	void convergeTurnsHorizontalTowardOwner() {
		// 朝 +X 飞,主人在 +Z:收敛后水平方向应朝 +Z 偏转
		Vec3 vel = new Vec3(1.0, 0.0, 0.0);
		Vec3 r = BoomerangPhysics.converge(vel, new Vec3(0, 0, 0), new Vec3(0, 0, 10), 0.5);
		assertTrue(r.z > 1e-6, "should turn toward +Z owner, got z=" + r.z);
	}

	@Test
	void convergeNoopWhenAlreadyAligned() {
		Vec3 vel = new Vec3(1.0, 0.0, 0.0);
		Vec3 r = BoomerangPhysics.converge(vel, new Vec3(0, 0, 0), new Vec3(10, 0, 0), 0.3);
		assertEquals(vel, r, "no change when velocity already points at owner");
	}

	@Test
	void convergeNoopWhenOwnerOverlapping() {
		Vec3 vel = new Vec3(1.0, 0.0, 0.0);
		Vec3 r = BoomerangPhysics.converge(vel, new Vec3(0, 0, 0), new Vec3(0, 0, 0), 0.3);
		assertEquals(vel, r, "no change when owner overlaps projectile");
	}

	// ==== 速度调制 modulateSpeed ====

	@Test
	void modulateSpeedFullSpeedWhenClose() {
		Vec3 vel = new Vec3(0.7, 0.0, 0.0);
		Vec3 r = BoomerangPhysics.modulateSpeed(vel, 0.7, 0.0, 16.0, 0.55);
		assertEquals(0.7, r.length(), 1e-9, "dist=0 → full speed");
	}

	@Test
	void modulateSpeedMinAtMaxRange() {
		Vec3 vel = new Vec3(0.7, 0.0, 0.0);
		Vec3 r = BoomerangPhysics.modulateSpeed(vel, 0.7, 16.0, 16.0, 0.55);
		assertEquals(0.7 * 0.55, r.length(), 1e-9, "dist=maxRange → minScale");
	}

	@Test
	void modulateSpeedClampsBeyondRange() {
		Vec3 vel = new Vec3(0.7, 0.0, 0.0);
		Vec3 r = BoomerangPhysics.modulateSpeed(vel, 0.7, 30.0, 16.0, 0.55);
		assertEquals(0.7 * 0.55, r.length(), 1e-9, "never below minScale");
	}

	// ==== 高度起伏 heightVelocity ====

	@Test
	void heightVelocityMaxUpwardAtStart() {
		// progress=0:正弦起点,垂直速度最大向上(开始抬升去远端)
		double v = BoomerangPhysics.heightVelocity(0.0, 0.4, 50);
		assertEquals(0.4 * Math.PI / 50.0, v, 1e-9);
	}

	@Test
	void heightVelocityZeroAtApex() {
		// progress=0.5:远端峰顶,垂直速度 0
		double v = BoomerangPhysics.heightVelocity(0.5, 0.4, 50);
		assertEquals(0.0, v, 1e-9);
	}

	@Test
	void heightVelocityMaxDownwardAtEnd() {
		// progress=1:回落到底,垂直速度最大向下
		double v = BoomerangPhysics.heightVelocity(1.0, 0.4, 50);
		assertEquals(-0.4 * Math.PI / 50.0, v, 1e-9);
	}

	// ==== 集成:完整飞行模拟 ====

	@Test
	void fullFlightReturnsToOwnerWithArcAndSlowdown() {
		// 投掷者固定在原点,回旋镖朝 +Z 投出,右手回旋(clockwise)
		Vec3 ownerPos = new Vec3(0, 0, 0);
		Vec3 pos = new Vec3(0, 0, 0.5);
		Vec3 vel = new Vec3(0, 0, 0.7);
		double rate = 0.08, strength = 0.05, minScale = 0.55, amp = 0.4, maxRange = 16.0;
		int total = 60;
		boolean clockwise = true;

		double maxLateral = 0.0;   // 横向(x)位移 → 证明有弧线
		double minHorizSpeed = Double.MAX_VALUE;

		boolean caught = false;
		int t;
		for (t = 0; t < 90; t++) {
			double progress = Math.min(1.0, (double) t / total);
			double dist = pos.subtract(ownerPos).horizontalDistance();
			maxLateral = Math.max(maxLateral, Math.abs(pos.x - ownerPos.x));
			minHorizSpeed = Math.min(minHorizSpeed, vel.horizontalDistance());

			if (dist <= 1.8 && t > 5) {
				caught = true;
				break;
			}

			vel = BoomerangPhysics.precess(vel, rate, clockwise);
			vel = BoomerangPhysics.converge(vel, pos, ownerPos, strength);
			vel = BoomerangPhysics.modulateSpeed(vel, 0.7, dist, maxRange, minScale);
			vel = new Vec3(vel.x, BoomerangPhysics.heightVelocity(progress, amp, total), vel.z);
			pos = BoomerangPhysics.step(pos, vel, 1.0);
		}

		assertTrue(caught, "should return to owner within 90 ticks (got t=" + t + ")");
		assertTrue(maxLateral > 1.0, "should have lateral arc displacement > 1.0, got " + maxLateral);
		assertTrue(minHorizSpeed < 0.7, "should slow down at far end, min=" + minHorizSpeed);
	}
}
