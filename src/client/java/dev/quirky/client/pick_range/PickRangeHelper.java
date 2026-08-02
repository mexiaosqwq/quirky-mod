package dev.quirky.client.pick_range;

import dev.quirky.config.QuirkyConfigHolder;
import net.minecraft.world.entity.player.Player;

/**
 * 远距中键拾取的距离决策（纯逻辑，可单测）。
 *
 * <p>26.2 原版交互距离来自 {@link Player#blockInteractionRange()}（
 * {@code Attributes.BLOCK_INTERACTION_RANGE}，默认 4.5）；创造模式由服务端
 * 附加 +0.5 的瞬态修饰符（{@code ServerPlayer.CREATIVE_BLOCK_INTERACTION_RANGE_MODIFIER}），
 * 即 5.0。
 */
public final class PickRangeHelper {
	/** 26.2 原版生存方块交互距离。 */
	public static final double VANILLA_SURVIVAL_RANGE = Player.DEFAULT_BLOCK_INTERACTION_RANGE;

	/** 26.2 原版创造方块交互距离：默认 4.5 + 创造修饰符 0.5。 */
	public static final double VANILLA_CREATIVE_RANGE = Player.DEFAULT_BLOCK_INTERACTION_RANGE + 0.5;

	private PickRangeHelper() {
	}

	public static int rangeFor(boolean creative) {
		return creative ? QuirkyConfigHolder.get().pickRangeCreative : QuirkyConfigHolder.get().pickRangeSurvival;
	}

	/**
	 * 扩展拾取是否生效：配置距离大于原版距离才启用，
	 * 否则（如把生存距离调到 4）保持原版行为。
	 */
	public static boolean isEnabled(boolean creative) {
		return rangeFor(creative) > (creative ? VANILLA_CREATIVE_RANGE : VANILLA_SURVIVAL_RANGE);
	}
}
