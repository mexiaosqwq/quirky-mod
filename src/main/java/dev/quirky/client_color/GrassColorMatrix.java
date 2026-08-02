package dev.quirky.client_color;

/**
 * 草地增绿颜色矩阵（对齐 Quark GreenerGrass）。
 * <p>
 * 3x3 对角矩阵：R×0.89、G×1.11、B×0.89（压红蓝、提绿）。强度 {@code multiplier}
 * 是插值参数：1.0 = Quark 默认效果，向 0.5 趋近恒等矩阵（接近原版），向 1.5 外推
 * 更强（review D1：低强度是"趋近原版"而非整体变暗）。纯颜色变换，只影响渲染着色，不改数据。
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
		// config 输入无边界校验（26.2 cloth-config 无 float 边界注解），运行时 clamp 到 0.5~1.5。
		// 插值：scale = 1 + multiplier × (factor − 1)；multiplier=1 时为 Quark 默认矩阵。
		float clamped = Math.max(0.5F, Math.min(1.5F, multiplier));
		this.redScale = 1.0F + clamped * (RED_FACTOR - 1.0F);
		this.greenScale = 1.0F + clamped * (GREEN_FACTOR - 1.0F);
		this.blueScale = 1.0F + clamped * (BLUE_FACTOR - 1.0F);
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
