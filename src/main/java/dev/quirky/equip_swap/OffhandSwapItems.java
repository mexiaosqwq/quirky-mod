package dev.quirky.equip_swap;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 副手交换适用物品判定（服务端与客户端共享；客户端不直接依赖服务端类，review S3）。
 */
public final class OffhandSwapItems {
	private OffhandSwapItems() {
	}

	/** 这些物品右键可直接换入副手槽（仅当 offhandSwap 开关开启时）。 */
	public static boolean isOffhandSwapItem(ItemStack stack) {
		return stack.is(Items.SHIELD)
			|| stack.is(Items.TORCH)
			|| stack.is(Items.WIND_CHARGE)
			|| stack.is(Items.FIREWORK_ROCKET);
	}
}
