package dev.quirky.client.deathcam;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 死亡电影镜头插值时间轴（纯逻辑，可单测）。
 *
 * t ∈ [0,1]：镜头以死亡锚点为中心环绕 360°（yaw 0→360°），水平半径 2→6 格缓慢拉远，
 * 高度 0.5→2.0 上升，俯视角 10°→25° 单调加深。
 *
 * {@link #position(float)} 返回相对死亡锚点的偏移；相机绝对位置 = 锚点 + 偏移，
 * 且镜头始终朝向锚点（环绕角与朝向角相差 180°）。
 */
public final class DeathCamTimeline {
	public static final float START_RADIUS = 2.0F;
	public static final float END_RADIUS = 6.0F;
	public static final float START_HEIGHT = 0.5F;
	public static final float END_HEIGHT = 2.0F;
	public static final float START_PITCH = 10.0F;
	public static final float END_PITCH = 25.0F;
	private static final float FULL_TURN_DEGREES = 360.0F;

	private final int durationTicks;
	private final float startYaw;

	public DeathCamTimeline(int durationTicks) {
		this(durationTicks, 0.0F);
	}

	/** @param startYaw 环绕起始角（相机朝向），通常取玩家死亡时的 yRot */
	public DeathCamTimeline(int durationTicks, float startYaw) {
		this.durationTicks = durationTicks;
		this.startYaw = startYaw;
	}

	public int durationTicks() {
		return durationTicks;
	}

	/**
	 * 相机位置相对死亡锚点的偏移：环绕角 = 朝向 + 180°（镜头看向锚点），
	 * 水平半径 2→6 格，高度 0.5→2.0 上升。
	 */
	public Vec3 position(float t) {
		double rad = Math.toRadians(yawDegrees(t) + FULL_TURN_DEGREES / 2.0F);
		float radius = Mth.lerp(t, START_RADIUS, END_RADIUS);
		return new Vec3(Math.sin(rad) * radius, Mth.lerp(t, START_HEIGHT, END_HEIGHT), Math.cos(rad) * radius);
	}

	/** 相机朝向（yaw）：起始朝向 + 0→360° 环绕一周。 */
	public float yawDegrees(float t) {
		return startYaw + FULL_TURN_DEGREES * t;
	}

	/**
	 * 相机俯视角（xRot）：10°→25° 单调加深。
	 * 注意：MC 相机约定正值 = 向下看（Camera.setRotation 与玩家实体 xRot 同约定），
	 * 设计稿写作 "-10°→-25°" 与「俯视展示尸体」语义冲突，按语义取正值。
	 */
	public float pitchDegrees(float t) {
		return Mth.lerp(t, START_PITCH, END_PITCH);
	}
}
