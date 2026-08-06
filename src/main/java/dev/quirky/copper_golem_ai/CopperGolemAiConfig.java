package dev.quirky.copper_golem_ai;

import dev.quirky.config.QuirkyConfig;
import org.jspecify.annotations.Nullable;

/**
 * AI 对话配置纯逻辑：启用判定与思考等级映射。与 Minecraft 解耦，全部可单测。
 */
public final class CopperGolemAiConfig {

	private CopperGolemAiConfig() {
	}

	/** 未配置 apiKey 或 model → AI 静默禁用（傀儡保持原版行为）。 */
	public static boolean enabled(QuirkyConfig c) {
		return c.aiApiKey != null && !c.aiApiKey.isBlank()
			&& c.aiModel != null && !c.aiModel.isBlank();
	}

	/**
	 * 思考等级映射：off/未知 → null（请求体不传 reasoning 参数）；
	 * low/medium/high 原样；xhigh/max 折叠为 high（OpenAI 兼容无更高档）。
	 */
	public static @Nullable String reasoningEffort(QuirkyConfig c) {
		String level = c.aiThinking;
		if (level == null) {
			return null;
		}
		return switch (level) {
			case "off" -> null;
			case "low", "medium", "high" -> level;
			case "xhigh", "max" -> "high";
			default -> null;
		};
	}
}
