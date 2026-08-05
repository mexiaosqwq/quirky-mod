package dev.quirky.boomerang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.quirky.TestBootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 连续进动弧线物理模型单测。
 * 模型:出程/返程分支。出程 precess(弧线) → converge(水平收敛) → modulateSpeed(近快远慢) → springVertical(保留投掷仰角 initialVelY);
 * 返程 precess(弧线) → converge3D(3D 同步收敛，垂直水平同向量，防「先垂直下再水平回」) → returnSpeed(近处减速)。
 * 位置 step 线性积分，AIR_DRAG 每帧衰减。不锁高度，仰投爬高/俯投下探，返程同步收敛回手。
 */
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

	// ==== 3D 同步收敛 converge3D（返程，含 180° 掉头兑底）====

	@Test
	void converge3DKeepsSpeedAndConvergesTowardOwner() {
		Vec3 vel = new Vec3(0.5, 0.0, 0.5);
		Vec3 r = BoomerangPhysics.converge3D(vel, new Vec3(0, 0, 0), new Vec3(0, -3, 8), 0.4);
		assertEquals(vel.length(), r.length(), 1e-9, "speed preserved");
		assertTrue(r.z > vel.z, "should gain +Z toward owner, got z=" + r.z);
		assertTrue(r.y < 0, "should gain downward toward owner below, got y=" + r.y);
	}

	@Test
	void converge3DReversesOppositeDirectionVerticalThrow() {
		// 竖直上抛到顶：速度朝上、玩家正下方(180°反向)。线性 blend 掉不了头，兑底必须让它朝玩家(向下)
		Vec3 vel = new Vec3(0.0, 0.7, 0.0);
		Vec3 r = BoomerangPhysics.converge3D(vel, new Vec3(0, 6, 0), new Vec3(0, 0, 0), 0.35);
		assertTrue(r.y < 0, "must reverse to point down toward owner, got y=" + r.y);
		assertEquals(vel.length(), r.length(), 1e-9, "speed preserved");
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

	// ==== returnSpeed ====

	@Test
	void returnSpeedSlowsWhenClose() {
		Vec3 vel = new Vec3(0.7, 0.0, 0.0);
		Vec3 r = BoomerangPhysics.returnSpeed(vel, 0.7, 0.0, 12.0, 0.55);
		assertEquals(0.7 * 0.55, r.length(), 1e-9, "close → minScale");
	}

	@Test
	void returnSpeedFullWhenFar() {
		Vec3 vel = new Vec3(0.7, 0.0, 0.0);
		Vec3 r = BoomerangPhysics.returnSpeed(vel, 0.7, 12.0, 12.0, 0.55);
		assertEquals(0.7, r.length(), 1e-9, "far → full speed");
	}

	@Test
	void returnSpeedIsMirrorOfModulate() {
		Vec3 vel = new Vec3(0.7, 0.0, 0.0);
		double dist = 6.0;
		double outSpeed = BoomerangPhysics.modulateSpeed(vel, 0.7, dist, 12.0, 0.55).length();
		double retSpeed = BoomerangPhysics.returnSpeed(vel, 0.7, dist, 12.0, 0.55).length();
		assertEquals(outSpeed, retSpeed, 1e-9, "mid-range is the mirror crossing point");
	}

	// ==== springVertical（出程保留仰角 + 返程弹簧追踪 ownerY，blend 用 returnRamp）====

	@Test
	void springVerticalOutboundKeepsInitialPitch() {
		// 出程 returnRamp=0：返回 initialVelY（弹簧不参与，保留投掷方向）
		double v = BoomerangPhysics.springVertical(0.0, 0.0, 0.3, 5.0, 1.0);
		assertEquals(0.3, v, 1e-9, "outbound keeps initial pitch velocity");
	}

	@Test
	void springVerticalReturnAcceleratesTowardOwnerAbove() {
		// 返程 returnRamp=1，玩家在上方(yDiff>0)，静止(vy=0)：accel=K*yDiff>0 → vel 向上
		double v = BoomerangPhysics.springVertical(0.0, 1.0, 0.0, 4.0, 1.0);
		assertTrue(v > 0, "owner above + stationary → upward velocity, got " + v);
		assertEquals(BoomerangPhysics.SPRING_K * 4.0, v, 1e-9, "accel = K*yDiff when vy=0");
	}

	@Test
	void springVerticalReturnAcceleratesTowardOwnerBelow() {
		// 返程玩家在下方(yDiff<0)：vel 向下（负）
		double v = BoomerangPhysics.springVertical(0.0, 1.0, 0.0, -4.0, 1.0);
		assertTrue(v < 0, "owner below → downward velocity, got " + v);
	}

	@Test
	void springVerticalDampingOpposesCurrentVelocity() {
		// 阻尼项 -C*velY：以高速向上(vy=2)飞、玩家就在当前位置(yDiff=0)时，应被减速
		double c = 2.0 * Math.sqrt(BoomerangPhysics.SPRING_K);
		double v = BoomerangPhysics.springVertical(2.0, 1.0, 0.0, 0.0, 1.0);
		assertEquals(2.0 - c * 2.0, v, 1e-9, "damping reduces velocity toward target");
		assertTrue(v < 2.0, "damping must oppose motion");
	}

	@Test
	void springVerticalBlendsMidFlight() {
		// 中间 returnRamp=0.5：initialVelY 与返程弹簧速度各占一半
		double initial = 0.3;
		double k = BoomerangPhysics.SPRING_K;
		double c = 2.0 * Math.sqrt(k);
		double returnVel = 0.1 + (k * 2.0 - c * 0.1) * 1.0;
		double v = BoomerangPhysics.springVertical(0.1, 0.5, initial, 2.0, 1.0);
		assertEquals(0.5 * initial + 0.5 * returnVel, v, 1e-9);
	}

	@Test
	void springVerticalCriticallyDampedNoOvershoot() {
		// 核心回归：从 y=4 自由收敛到 target y=0，临界阻尼应单调下降不越过 0（旧 P 控制器会等幅振荡）。
		double pos = 4.0;
		double vy = 0.0;
		double target = 0.0;
		double prevPos = pos;
		boolean overshot = false;
		for (int t = 0; t < 60; t++) {
			vy = BoomerangPhysics.springVertical(vy, 1.0, 0.0, target - pos, 1.0);
			pos += vy * 1.0;
			if (prevPos > target && pos < target) {
				overshot = true;
			}
			prevPos = pos;
		}
		assertTrue(!overshot, "critical damping must not overshoot target, final pos=" + pos);
		assertTrue(Math.abs(pos - target) < 0.3, "should converge near target within 60 ticks, got pos=" + pos);
	}

	@Test
	void springVerticalConvergesFromAboveNoOscillation() {
		// 仰投场景：回旋镖在 y=6（飞高），target y=0。收敛过程 pos.y 应单调递减，不反复穿越 target。
		double pos = 6.0;
		double vy = 0.0;
		int crossings = 0;
		double prevSign = 1.0;
		for (int t = 0; t < 80; t++) {
			vy = BoomerangPhysics.springVertical(vy, 1.0, 0.0, -pos, 1.0);
			pos += vy;
			double sign = Math.signum(pos);
			if (sign != prevSign && sign != 0) {
				crossings++;
				prevSign = sign;
			}
		}
		assertTrue(crossings == 0, "critical damping must not cross target at all, got " + crossings + " crossings, final pos=" + pos);
		assertTrue(Math.abs(pos) < 0.5, "should settle near target, got " + pos);
	}

	// ==== smoothstep 工具 ====

	@Test
	void smoothstepClampsAndSmooths() {
		assertEquals(0.0, BoomerangPhysics.smoothstep(-0.5), 1e-9, "x<=0 → 0");
		assertEquals(0.0, BoomerangPhysics.smoothstep(0.0), 1e-9);
		assertEquals(1.0, BoomerangPhysics.smoothstep(1.0), 1e-9);
		assertEquals(1.0, BoomerangPhysics.smoothstep(1.5), 1e-9, "x>=1 → 1");
		assertEquals(0.5, BoomerangPhysics.smoothstep(0.5), 1e-9);
		assertTrue(BoomerangPhysics.smoothstep(0.3) < BoomerangPhysics.smoothstep(0.7));
	}

	// ==== 集成:完整飞行模拟（returnRamp 垂直 blend + 投掷方向自由）====

	private static final double RETURN_RAMP_RATE = 0.15;
	private static final double AIR_DRAG = 0.99;

	/** 复刻 Entity tickMovement 的完整飞行循环，返回接住状态/峰值/最小高度/放弃。 */
	private static double[] fly(Vec3 startVel, boolean clockwise, double ownerRunSpeed, int maxTicks) {
		Vec3 ownerPos = new Vec3(0, 0, 0);
		Vec3 pos = new Vec3(0, 0, 0.5);
		Vec3 homePos = pos; // 投掷点：牵引锚点
		Vec3 vel = startVel;
		double initialVelY = vel.y;
		double rate = 0.25, strength = 0.35, minScale = 0.3, maxRange = 12.0;
		final double LEASH_RADIUS = 8.0;
		Vec3 ownerMove = new Vec3(ownerRunSpeed, 0, 0);

		double maxPeak = 0.0;
		double minY = 0.0;
		boolean caught = false;
		boolean gaveUp = false;
		boolean returning = false;
		boolean diveFirst = false;
		double peakDist = 0.0;
		double returnRamp = 0.0;
		int t;
		for (t = 0; t < maxTicks; t++) {
			double dist = pos.subtract(ownerPos).length();
			if (returning && t > 5 && pos.subtract(ownerPos).horizontalDistanceSqr() <= 4.0
				&& Math.abs(pos.y - ownerPos.y) <= 1.5) {
				caught = true;
				break;
			}
			if (!returning && (dist >= maxRange * 0.7 || (t > 5 && dist < peakDist - 0.3))) {
				returning = true;
				returnRamp = 0.0;
			}
			// 放弃判定：玩家跑出牵引半径且回旋镖已飞回投掷点附近 → 掉落放弃
			if (returning && ownerPos.distanceTo(homePos) > LEASH_RADIUS && pos.distanceTo(homePos) < 2.5) {
				gaveUp = true;
				break;
			}
			peakDist = Math.max(peakDist, dist);
			if (returning) {
				returnRamp = Math.min(1.0, returnRamp + RETURN_RAMP_RATE);
			}
			double trigger = BoomerangPhysics.smoothstep(dist / maxRange);
			if (!returning) {
				// 出程：弧线偏转 + 水平收敛 + 近快远慢 + 保留投掷仰角
				vel = BoomerangPhysics.precess(vel, rate * trigger, clockwise);
				vel = BoomerangPhysics.converge(vel, pos, ownerPos, strength * trigger);
				vel = BoomerangPhysics.modulateSpeed(vel, startVel.length(), dist, maxRange, minScale);
				vel = new Vec3(vel.x, BoomerangPhysics.springVertical(vel.y, 0.0, initialVelY, ownerPos.y - pos.y, 1.0), vel.z);
			} else {
				// 返程：牵引锚点——玩家在投掷点 LEASH_RADIUS 内则追踪接住；跑远则飞回投掷点
				Vec3 returnAim = ownerPos;
				if (ownerPos.distanceTo(homePos) > LEASH_RADIUS) {
					returnAim = homePos;
				}
				double returnDist = pos.distanceTo(returnAim);
				vel = BoomerangPhysics.precess(vel, rate * returnRamp, clockwise);
				vel = BoomerangPhysics.converge3D(vel, pos, returnAim, strength * returnRamp);
				vel = BoomerangPhysics.returnSpeed(vel, startVel.length(), returnDist, maxRange, minScale);
			}
			vel = vel.scale(AIR_DRAG);
			pos = BoomerangPhysics.step(pos, vel, 1.0);
			maxPeak = Math.max(maxPeak, pos.y);
			minY = Math.min(minY, pos.y);
			// 回归：返程不允许「垂直已贴近玩家高度(差<0.8)但水平还远(>3格)」——那正是旧弹簧的「先垂直下再水平回」
			if (returning && Math.abs(pos.y - ownerPos.y) < 0.8 && pos.subtract(ownerPos).horizontalDistance() > 3.0) {
				diveFirst = true;
			}
			ownerPos = ownerPos.add(ownerMove);
		}
		return new double[]{caught ? 1 : 0, maxPeak, minY, t, diveFirst ? 1 : 0, gaveUp ? 1 : 0};
	}

	@Test
	void fullFlightReturnsToOwnerAndHitsFrontalTarget() {
		// 平投：出程近似直线可命中前方敌人，弧线回手
		double[] r = fly(new Vec3(0, 0, 0.7), true, 0, 90);
		assertTrue(r[0] == 1, "should return to owner within 90 ticks (t=" + r[3] + ")");
		assertTrue(r[1] < 1.0, "flat throw should stay low, peak=" + r[1]);
	}

	@Test
	void fullFlightLeftHandReturnsToOwnerWithOppositeArc() {
		// 副手投掷 = 左手回旋(clockwise=false)：应同样回手
		double[] r = fly(new Vec3(0, 0, 0.7), false, 0, 90);
		assertTrue(r[0] == 1, "left-hand throw should also return to owner (t=" + r[3] + ")");
	}

	@Test
	void returningStateCatchesEvenWhenOwnerWalks() {
		// 玩家步行移动(0.1/tick，牵引半径内)时回旋镖仍能可靠回手
		double[] r = fly(new Vec3(0, 0, 0.7), true, 0.1, 100);
		assertTrue(r[0] == 1, "should catch when owner walks (t=" + r[3] + ")");
	}

	@Test
	void givesUpWhenOwnerRunsBeyondLeash() {
		// 玩家冲刺跑远(0.3/tick，超出牵引半径)时回旋镖不再追：放弃掉落，不无限跟随
		double[] r = fly(new Vec3(0, 0, 0.7), true, 0.3, 100);
		assertTrue(r[5] == 1, "should give up and drop when owner runs beyond leash");
	}

	@Test
	void upwardThrowClimbsAndReturns() {
		// 核心回归：仰投 30° 爬高（不锁高度）且能回手
		double speed = 0.7;
		double pitchRad = Math.toRadians(30.0);
		double[] r = fly(new Vec3(0, Math.sin(pitchRad) * speed, Math.cos(pitchRad) * speed), true, 0, 100);
		assertTrue(r[0] == 1, "upward throw should return to owner (t=" + r[3] + ")");
		assertTrue(r[1] > 2.0, "upward throw should climb above throw height (peak=" + r[1] + ")");
	}

	@Test
	void downwardThrowDivesAndReturns() {
		// 核心回归：俯投 30° 下探（不锁高度）且能回手
		double speed = 0.7;
		double pitchRad = Math.toRadians(-30.0);
		double[] r = fly(new Vec3(0, Math.sin(pitchRad) * speed, Math.cos(pitchRad) * speed), true, 0, 100);
		assertTrue(r[0] == 1, "downward throw should return to owner (t=" + r[3] + ")");
		assertTrue(r[2] < -2.0, "downward throw should dive below throw height (min=" + r[2] + ")");
	}

	@Test
	void returnConvergesVerticalAndHorizontalTogether() {
		// 回归：仰投返程必须垂直水平同步收敛，不能「先垂直掉到玩家高度、水平还在远处」（旧独立垂直弹簧的病）
		double speed = 0.7;
		double pitchRad = Math.toRadians(30.0);
		double[] r = fly(new Vec3(0, Math.sin(pitchRad) * speed, Math.cos(pitchRad) * speed), true, 0, 100);
		assertTrue(r[0] == 1, "upward throw should still return to owner (t=" + r[3] + ")");
		assertTrue(r[4] == 0, "return must not dive vertically before horizontal converges");
	}

	@Test
	void verticalThrowReturnsToOwner() {
		// 核心回归：竖直上抛(90°)应能到顶并回手，不飞走/不打转（旧水平距离触发 + converge3D 180° 掉头失效的病）
		double speed = 0.7;
		double[] r = fly(new Vec3(0, speed, 0), true, 0, 140);
		assertTrue(r[0] == 1, "vertical throw should return to owner (t=" + r[3] + ")");
		assertTrue(r[1] > 2.0, "vertical throw should climb (peak=" + r[1] + ")");
	}

	@Test
	void steepThrowReturnsToOwner() {
		// 核心回归：大仰角(60°)也应回手（用户报告的「角比较大不回来」场景）
		double speed = 0.7;
		double pitchRad = Math.toRadians(60.0);
		double[] r = fly(new Vec3(0, Math.sin(pitchRad) * speed, Math.cos(pitchRad) * speed), true, 0, 140);
		assertTrue(r[0] == 1, "steep throw should return to owner (t=" + r[3] + ")");
	}
}
