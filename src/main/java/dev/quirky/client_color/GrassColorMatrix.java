package dev.quirky.client_color;

/**
 * 草地增绿颜色矩阵（对齐 Quark GreenerGrass）。
 * <p>
 * 3x3 对角矩阵：R×0.89、G×1.11、B×0.89（压红蓝、提绿），对角项再乘强度
 * {@code multiplier}（1.0 = Quark 默认效果）。纯颜色变换，只影响渲染着色，不改数据。
 */
public final class GrassColorMatrix {
	/** Quark 默认矩阵对角项 */
	private static final float RED_FACTOR = 0.89F;
	private static final float GREEN_FACTOR = 1.11F;
	private static final float BLUE_FACTOR = 0.89F;

	private final float redScale;
	private final float greenScale;
	private final float blueScale;

	public GrassColorMatrix(float multiplier) {
		this.redScale = RED_FACTOR * multiplier;
		this.greenScale = GREEN_FACTOR * multiplier;
		this.blueScale = BLUE_FACTOR * multiplier;
	}

	/**
	 * 对 ARGB 颜色做矩阵卷积：R、G、B 分别乘对角项，每通道 clamp 0~255，alpha 保持不变。
	 */
	public int convolve(int argb) {
		int alpha = (argb >> 24) & 0xFF;
		int red = clamp(Math.round(((argb >> 16) & 0xFF) * this.redScale));
		int green = clamp(Math.round(((argb >> 8) & 0xFF) * this.greenScale));
		int blue = clamp(Math.round((argb & 0xFF) * this.blueScale));
		return alpha << 24 | red << 16 | green << 8 | blue;
	}

	private static int clamp(int value) {
		return Math.max(0, Math.min(255, value));
	}
}
