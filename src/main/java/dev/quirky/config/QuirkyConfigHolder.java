package dev.quirky.config;

/**
 * 轻量静态容器：测试环境直接 set 默认实例（不碰 AutoConfig/文件系统）；
 * 生产环境在 QuirkyMod.onInitialize 注入 AutoConfig 实例。
 */
public final class QuirkyConfigHolder {
	private static QuirkyConfig config = new QuirkyConfig();

	private QuirkyConfigHolder() {
	}

	public static QuirkyConfig get() {
		return config;
	}

	public static void set(QuirkyConfig config) {
		QuirkyConfigHolder.config = config;
	}
}
