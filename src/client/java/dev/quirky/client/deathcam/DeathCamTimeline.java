package dev.quirky.client.deathcam;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 死亡电影镜头插值时间轴（纯逻辑，可单测）。
 *
 * <p>基岩版式过渡：t=0 时相机贴近玩家（身后 0.8 格、眼睛高度 1.6、水平视角），
 * 前 25% 时间快速拉出到 2.5 格（第一人称 → 第三人称的平滑过渡感），
 * 之后缓慢拉远到 6 格并环绕 360°（yaw 0→360°）、高度升至 2.0、俯视角 0°→25° 单调加深。
 *
 * {@link #position(float)} 返回相对死亡锚点的偏移；相机绝对位置 = 锚点 + 偏移，
 * 且镜头始终朝向锚点（环绕角与朝向角相差 180°）。
 */
public final class DeathCamTimeline {
	/** 起始水平半径：贴近玩家身后（玩家碰撞箱宽 0.6，0.8 在箱外不穿模） */
	public static final float START_RADIUS = 0.8F;
	/** 拉出阶段结束时的半径：前 25% 时间从 0.8 快速拉到 2.5 */
	public static final float PULLOUT_RADIUS = 2.5F;
	public static final float END_RADIUS = 6.0F;
	/** 起始高度：玩家眼睛高度，避免从第一人称瞬间跳到脚下 */
	public static final float START_HEIGHT = 1.6F;
	public static final float END_HEIGHT = 2.0F;
	/** 起始俯视角：水平，逐渐加深俯视尸体 */
	public static final float START_PITCH = 0.0F;
	public static final float END_PITCH = 25.0F;
	/** 拉出阶段占全程比例 */
	private static final float PULLOUT_FRACTION = 0.25F;
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
	 * 水平半径按"先快后慢"分段插值，高度 1.6→2.0 线性上升。
	 */
	public Vec3 position(float t) {
		double rad = Math.toRadians(yawDegrees(t) + FULL_TURN_DEGREES / 2.0F);
		float radius = radiusAt(t);
		return new Vec3(Math.sin(rad) * radius, Mth.lerp(t, START_HEIGHT, END_HEIGHT), Math.cos(rad) * radius);
	}

	/** 相机朝向（yaw）：起始朝向 + 0→360° 环绕一周。 */
	public float yawDegrees(float t) {
		return startYaw + FULL_TURN_DEGREES * t;
	}

	/**
	 * 相机俯视角（xRot）：0°→25° 单调加深。
	 * 注意：MC 相机约定正值 = 向下看（Camera.setRotation 与玩家实体 xRot 同约定），
	 * 设计稿写作 "-10°→-25°" 与「俯视展示尸体」语义冲突，按语义取正值。
	 */
	public float pitchDegrees(float t) {
		return Mth.lerp(t, START_PITCH, END_PITCH);
	}

	/** 水平半径：前 25% 快速拉出（0.8→2.5），后 75% 缓慢拉远（2.5→6.0）。 */
	private static float radiusAt(float t) {
		if (t < PULLOUT_FRACTION) {
			return Mth.lerp(t / PULLOUT_FRACTION, START_RADIUS, PULLOUT_RADIUS);
		}
		return Mth.lerp((t - PULLOUT_FRACTION) / (1.0F - PULLOUT_FRACTION), PULLOUT_RADIUS, END_RADIUS);
	}
}
