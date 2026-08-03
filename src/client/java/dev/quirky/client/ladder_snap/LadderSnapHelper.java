package dev.quirky.client.ladder_snap;

/**
 * 自动上梯（spec 5.9，基岩版式）垂直速度计算（纯逻辑，可单测）：
 * 爬梯子/藤蔓时未按手动键，抬头自动上升、低头下降、平视悬停。
 */
public final class LadderSnapHelper {
	/** 自动攀爬：抬头/低头触发阈值（度）——±15° 更跟手，轻仰（看远处）不误触发 */
	public static final float CLIMB_PITCH_UP = -15.0F;
	public static final float CLIMB_PITCH_DOWN = 15.0F;
	/**
	 * 自动攀爬 delta.y 初值。注意：travelInAir 每 tick 施加重力（-0.08）与竖直摩擦（×0.98），
	 * 注入 0.2 后净上升 ≈ 0.2×0.98 − 0.08 ≈ 0.116 b/t，对齐原版 W 爬梯净速度（≈0.12）。
	 */
	public static final double CLIMB_SPEED = 0.2;
	/**
	 * 平视速度：0.05（小于重力 0.08）→ 净速度 ≈ −0.03 b/t 缓慢下滑。
	 * 用户验收：平视应"不爬"——宁可极慢下滑也绝不自动上升。
	 */
	public static final double HOVER_SPEED = 0.05;

	private LadderSnapHelper() {
	}

	/**
	 * 自动攀爬垂直速度：抬头（pitch &lt; -15°）上升、低头（pitch &gt; 15°）下降、
	 * 平视注入 0.05 缓慢下滑（不自动爬）；手动按键时返回 NaN（不干预）。
	 */
	public static double climbVelocity(float pitch, boolean manualInput) {
		if (manualInput) {
			return Double.NaN;
		}
		if (pitch < CLIMB_PITCH_UP) {
			return CLIMB_SPEED;
		}
		if (pitch > CLIMB_PITCH_DOWN) {
			return -0.15;
		}
		return HOVER_SPEED;
	}
}
