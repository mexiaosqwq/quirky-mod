package dev.quirky.client.ladder_snap;

/**
 * 自动上梯（spec 5.9，基岩版式）垂直速度计算（纯逻辑，可单测）：
 * 爬梯子/藤蔓时未按手动键，抬头自动上升、低头下降、平视悬停。
 */
public final class LadderSnapHelper {
	/** 自动攀爬：抬头/低头触发阈值（度），平视区间内悬停 */
	public static final float CLIMB_PITCH_UP = -30.0F;
	public static final float CLIMB_PITCH_DOWN = 30.0F;
	/** 自动攀爬垂直速度（对齐原版爬梯最大速度 0.15） */
	public static final double CLIMB_SPEED = 0.15;

	private LadderSnapHelper() {
	}

	/**
	 * 自动攀爬垂直速度：抬头（pitch &lt; -30°）上升、低头（pitch &gt; 30°）下降、平视悬停；
	 * 手动按键时返回 NaN（不干预）。
	 */
	public static double climbVelocity(float pitch, boolean manualInput) {
		if (manualInput) {
			return Double.NaN;
		}
		if (pitch < CLIMB_PITCH_UP) {
			return CLIMB_SPEED;
		}
		if (pitch > CLIMB_PITCH_DOWN) {
			return -CLIMB_SPEED;
		}
		return 0.0;
	}
}
