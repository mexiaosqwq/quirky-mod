package dev.quirky.client.tooltips;

import dev.quirky.ModItems;
import dev.quirky.config.QuirkyConfig;
import dev.quirky.config.QuirkyConfigHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * 高级 tooltip 模式（按 Shift 查看）的判定（客户端）。
 *
 * <p>{@link #shouldSuppress}：配置开关开启、未按 Shift、且物品确有受控的 mod 增强
 * tooltip 内容时返回 true（该内容应隐藏并代之以"按 Shift 查看详情"提示）。
 * 无客户端实例（服务端/单测）一律不抑制。
 *
 * <p>{@link #hasGatedContent} 与各 tooltip 来源的显示条件对齐（时钟/地图预览/箭袋/
 * 潜影盒/播种袋/鱼饵球/绳）。食物与属性 tooltip 不在受控清单内——默认始终显示。
 */
public final class TooltipShiftHelper {
	private TooltipShiftHelper() {
	}

	/** 该 stack 是否应被高级模式抑制（客户端判定，搜索索引/服务端路径恒 false）。 */
	public static boolean shouldSuppress(ItemStack stack) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.hasShiftDown()) {
			return false;
		}
		return hasGatedContent(stack);
	}

	/**
	 * 物品是否有受 Shift 控制的 mod tooltip 内容（高级模式恒开，无配置开关）。
	 * 与各 tooltip 来源的显示条件对齐（时钟/地图预览/箭袋/潜影盒/播种袋/鱼饵球/绳）。
	 */
	public static boolean hasGatedContent(ItemStack stack) {
		QuirkyConfig config = QuirkyConfigHolder.get();
		if (config.clockTooltip && stack.is(Items.CLOCK)) {
			return true;
		}
		if (config.mapPreview && stack.has(DataComponents.MAP_ID)) {
			return true;
		}
		if (config.quiverEnabled && stack.is(ModItems.QUIVER)) {
			return true;
		}
		if (config.shulkerTooltip && stack.is(ItemTags.SHULKER_BOXES)) {
			// 与 TooltipDetailsMixin 对齐：空盒无内容预览，也不提示
			ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
			return contents.nonEmptyItems().iterator().hasNext();
		}
		if (config.seedPouchEnabled && stack.is(ModItems.SEED_POUCH)) {
			return true;
		}
		if (config.fishBaitEnabled && stack.is(ModItems.FISH_BAIT)) {
			return true;
		}
		if (config.ropeEnabled && (stack.is(ModItems.ROPE) || stack.is(ModItems.ROPE_LANTERN))) {
			return true;
		}
		return false;
	}
}
