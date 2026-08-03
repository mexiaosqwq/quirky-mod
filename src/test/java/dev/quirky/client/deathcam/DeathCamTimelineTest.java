package dev.quirky.client.deathcam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DeathCamTimelineTest {
	private static final double EPS = 1e-4;

	@Test
	void timelineStartsNearPlayerAndPullsBack() {
		// 基岩版式：起始相机贴近玩家（身后 0.8 格、眼睛高度），平滑拉出到 6 格展示位
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
	void yawStaysAtStartYaw() {
		// 基岩版死亡镜头不环绕：镜头朝向全程保持玩家死亡时的朝向，杜绝"雷霆运镜"式旋转
		DeathCamTimeline timeline = new DeathCamTimeline(50, 90.0F);
		assertEquals(90.0, timeline.yawDegrees(0.0F), EPS);
		assertEquals(90.0, timeline.yawDegrees(0.5F), EPS);
		assertEquals(90.0, timeline.yawDegrees(1.0F), EPS);
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
	void cameraStaysBehindPlayer() {
		// 相机始终位于玩家正后方（朝向的反方向），拉出过程中方向恒定。
		// 用 yaw=90（玩家朝西 -X）验证镜像符号：MC 朝向向量 = (-sin, cos)，
		// 位置在后方 = (-sin(yaw+180), cos(yaw+180)) = (+X, 0)（东）。
		DeathCamTimeline timeline = new DeathCamTimeline(50, 90.0F);
		for (int i = 0; i <= 20; i++) {
			float t = i / 20.0F;
			Vec3 offset = timeline.position(t);
			assertTrue(offset.x > 0.5, "相机应在玩家身后（+X 东侧） at t=" + t + " offset=" + offset);
			assertEquals(0.0, offset.z, EPS);
		}
	}

	@Test
	void cameraRisesVisibly() {
		// 高度应从眼睛高度明显升高（拉出展示位），2.5s 内 +1.4 格肉眼可辨
		DeathCamTimeline timeline = new DeathCamTimeline(50);
		double startY = timeline.position(0.0F).y;
		double endY = timeline.position(1.0F).y;
		assertTrue(endY - startY >= 1.2, "结束高度应明显高于起始: " + (endY - startY));
	}
}
