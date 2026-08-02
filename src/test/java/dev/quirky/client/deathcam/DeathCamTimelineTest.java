package dev.quirky.client.deathcam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DeathCamTimelineTest {
	private static final double EPS = 1e-4;

	@Test
	void timelineStartsNearPlayerAndCirclesDeathPoint() {
		// 基岩版式：起始相机贴近玩家（身后 0.8 格、眼睛高度），缓慢拉远环绕
		DeathCamTimeline timeline = new DeathCamTimeline(50);
		Vec3 start = timeline.position(0.0F);
		Vec3 end = timeline.position(1.0F);
		assertEquals(0.8, start.horizontalDistance(), EPS);
		assertEquals(6.0, end.horizontalDistance(), EPS);
		assertTrue(end.y > start.y, "镜头应随 t 上升");
	}

	@Test
	void timelineStartsAtEyeHeight() {
		// 起始高度 = 玩家眼睛高度附近，避免从第一人称瞬间跳到脚下
		DeathCamTimeline timeline = new DeathCamTimeline(50);
		assertEquals(1.6, timeline.position(0.0F).y, EPS);
	}

	@Test
	void timelinePullsOutQuicklyThenSlowly() {
		// 前 25% 快速从贴脸拉到 2.5 格（拉出阶段），之后缓慢拉远
		DeathCamTimeline timeline = new DeathCamTimeline(50);
		double radiusAtQuarter = timeline.position(0.25F).horizontalDistance();
		double radiusAtHalf = timeline.position(0.5F).horizontalDistance();
		// 前 25% 平均速度（0→0.25）应快于次 25%（0.25→0.5）
		double pulloutSpeed = (radiusAtQuarter - timeline.position(0.0F).horizontalDistance()) / 0.25;
		double cruiseSpeed = (radiusAtHalf - radiusAtQuarter) / 0.25;
		assertTrue(radiusAtQuarter >= 2.0, "前段应快速拉出，t=0.25 时已到 " + radiusAtQuarter);
		assertTrue(pulloutSpeed > cruiseSpeed, "前段拉远速度应快于后段: " + pulloutSpeed + " vs " + cruiseSpeed);
	}

	@Test
	void yawSweepsFullCircle() {
		DeathCamTimeline timeline = new DeathCamTimeline(50);
		assertEquals(0.0, timeline.yawDegrees(0.0F), EPS);
		assertEquals(360.0, timeline.yawDegrees(1.0F), EPS);
	}

	@Test
	void yawSweepStartsAtGivenYaw() {
		DeathCamTimeline timeline = new DeathCamTimeline(50, 90.0F);
		assertEquals(90.0, timeline.yawDegrees(0.0F), EPS);
		assertEquals(450.0, timeline.yawDegrees(1.0F), EPS);
	}

	@Test
	void pitchTiltsDownMonotonicallyFromLevel() {
		DeathCamTimeline timeline = new DeathCamTimeline(50);
		assertEquals(0.0, timeline.pitchDegrees(0.0F), EPS);
		assertEquals(DeathCamTimeline.END_PITCH, timeline.pitchDegrees(1.0F), EPS);
		float previous = timeline.pitchDegrees(0.0F);
		for (int i = 1; i <= 20; i++) {
			float current = timeline.pitchDegrees(i / 20.0F);
			assertTrue(current > previous, "俯角应单调加深 at t=" + i / 20.0F);
			previous = current;
		}
	}

	@Test
	void cameraOrbitsFacingAnchorAtEveryStep() {
		DeathCamTimeline timeline = new DeathCamTimeline(50);
		for (int i = 0; i <= 20; i++) {
			float t = i / 20.0F;
			Vec3 offset = timeline.position(t);
			// 相机偏移始终与朝向相反（指向锚点）：yaw(t)+180° 方向的单位向量 ∝ -offset
			double lookX = Math.sin(Math.toRadians(timeline.yawDegrees(t)));
			double lookZ = Math.cos(Math.toRadians(timeline.yawDegrees(t)));
			double dot = lookX * offset.x + lookZ * offset.z;
			assertTrue(dot < 0, "镜头应朝向锚点 at t=" + t);
		}
	}
}
