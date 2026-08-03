package dev.quirky.client.ladder_snap;

/**
 * 自动上梯（spec 5.9，基岩版式）垂直速度计算（纯逻辑，可单测）：
 * 爬梯子/藤蔓时未按手动键，抬头自动上升、低头下降、平视悬停。
 */
public final class LadderSnapHelper {
	/** 自动攀爬：抬头/低头触发阈值（度），平视区间内悬停 */
	public static final float CLIMB_PITCH_UP = -30.0F;
	public static final float CLIMB_PITCH_DOWN = 30.0F;
	/**
	 * 自动攀爬 delta.y 初值。注意：travelInAir 每 tick 施加重力（-0.08）与竖直摩擦（×0.98），
	 * 注入 0.2 后净上升 ≈ 0.2×0.98 − 0.08 ≈ 0.116 b/t，对齐原版 W 爬梯净速度（≈0.12）。
	 */
	public static final double CLIMB_SPEED = 0.2;
	/** 平视悬停：注入 0.08 抵消重力（0.08×0.98 − 0.08 ≈ 0），否则会以 ≈0.078 b/t 滑落 */
	public static final double HOVER_SPEED = 0.08;

	private LadderSnapHelper() {
	}

	/**
	 * 自动攀爬垂直速度：抬头（pitch &lt; -30°）上升、低头（pitch &gt; 30°）下降、
	 * 平视注入 0.08 抵消重力实现悬停；手动按键时返回 NaN（不干预）。
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
