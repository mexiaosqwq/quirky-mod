package dev.quirky.client.deathcam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DeathCamTimelineTest {
	private static final double EPS = 1e-4;

	@Test
	void timelineCirclesDeathPoint() {
		DeathCamTimeline timeline = new DeathCamTimeline(50);
		Vec3 start = timeline.position(0.0F);
		Vec3 end = timeline.position(1.0F);
		assertEquals(2.0, start.horizontalDistance(), EPS);
		assertEquals(6.0, end.horizontalDistance(), EPS);
		assertTrue(end.y > start.y, "镜头应随 t 上升");
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
	void pitchTiltsDownMonotonically() {
		DeathCamTimeline timeline = new DeathCamTimeline(50);
		assertEquals(DeathCamTimeline.START_PITCH, timeline.pitchDegrees(0.0F), EPS);
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
