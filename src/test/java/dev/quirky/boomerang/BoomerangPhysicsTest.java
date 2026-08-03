package dev.quirky.boomerang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.quirky.TestBootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BoomerangPhysicsTest {

	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	// ==== 线性步进 ====

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

	// ==== 出程右偏弧线 ====

	@Test
	void applyArcKeepsSpeed() {
		Vec3 vel = new Vec3(1.0, 0.0, 0.0);
		Vec3 curved = BoomerangPhysics.applyArc(vel, 0.03);
		assertEquals(1.0, curved.length(), 1e-9);
	}

	@Test
	void applyArcChangesDirectionWithoutVerticalDrift() {
		Vec3 vel = new Vec3(1.0, 0.0, 0.0);
		Vec3 curved = BoomerangPhysics.applyArc(vel, 0.03);
		assertTrue(curved.x < 1.0);
		assertTrue(Math.abs(curved.z) > 1e-6);
		assertEquals(0.0, curved.y, 1e-9);
	}

	@Test
	void zeroArcIsIdentity() {
		Vec3 vel = new Vec3(0.0, 1.0, 2.0);
		assertEquals(vel, BoomerangPhysics.applyArc(vel, 0.0));
	}

	// ==== 返程转向 ====

	@Test
	void returnVectorTurnsTowardTargetWithoutOvershoot() {
		// 速度朝 +X，目标在 +Z：每步最多转 turnRate，方向单调收敛
		Vec3 pos = new Vec3(0.0, 0.0, 0.0);
		Vec3 target = new Vec3(0.0, 0.0, 100.0);
		Vec3 vel = new Vec3(1.0, 0.0, 0.0);
		double turnRate = 0.1;
		double prevAngle = Double.MAX_VALUE;
		Vec3 current = vel;
		for (int i = 0; i < 40; i++) {
			current = BoomerangPhysics.returnVector(pos, target, current, turnRate);
			Vec3 toTarget = target.subtract(pos).normalize();
			double angle = Math.acos(Math.max(-1.0, Math.min(1.0, current.normalize().dot(toTarget))));
			assertTrue(angle <= prevAngle + 1e-9, "step " + i + " must not turn away: " + angle + " > " + prevAngle);
			prevAngle = angle;
		}
		// 收敛后基本指向目标
		assertTrue(prevAngle < 0.1);
	}

	@Test
	void returnVectorKeepsSpeed() {
		Vec3 pos = new Vec3(0.0, 0.0, 0.0);
		Vec3 target = new Vec3(1.0, 0.0, 1.0);
		Vec3 vel = new Vec3(1.0, 0.0, 0.0);
		Vec3 turned = BoomerangPhysics.returnVector(pos, target, vel, 0.3);
		assertEquals(1.0, turned.length(), 1e-9);
	}

	@Test
	void returnVectorIsNoopWhenAlreadyAligned() {
		Vec3 pos = new Vec3(0.0, 0.0, 0.0);
		Vec3 target = new Vec3(5.0, 0.0, 0.0);
		Vec3 vel = new Vec3(1.0, 0.0, 0.0);
		assertEquals(vel, BoomerangPhysics.returnVector(pos, target, vel, 0.3));
	}

	@Test
	void returnVectorHandlesOppositeDirectionByFullTurn() {
		// 180° 掉头：单步最多转 turnRate，多步后可完全反向
		Vec3 pos = new Vec3(0.0, 0.0, 0.0);
		Vec3 target = new Vec3(-5.0, 0.0, 0.0);
		Vec3 vel = new Vec3(1.0, 0.0, 0.0);
		Vec3 current = vel;
		for (int i = 0; i < 40; i++) {
			current = BoomerangPhysics.returnVector(pos, target, current, 0.1);
		}
		assertTrue(current.x < -0.9, "should have turned around, got " + current);
	}
}
