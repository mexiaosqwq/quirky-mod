package dev.quirky.client.tooltips;

import dev.quirky.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 高级 tooltip 模式（按 Shift 查看）的判定（客户端）。
 *
 * <p>{@link #shouldSuppress}：未按 Shift 且物品确有受控的**描述性长文字** tooltip
 * 内容时返回 true（该长文字被隐藏，代之以"按 Shift 查看详情"提示行）。
 * 无客户端实例（服务端/单测）一律不抑制。
 *
 * <p>受控清单只含带长文字描述的 mod 物品：绳（用法说明）、播种袋、鱼饵球、箭袋
 * （用法行）。信息类提示不受控、始终显示：时钟行、地图预览、箭袋内容网格、
 * 潜影盒内容网格，以及食物与属性 tooltip。
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

	/** 物品是否有受 Shift 控制的描述性长文字 tooltip 行。 */
	public static boolean hasGatedContent(ItemStack stack) {
		if (stack.is(ModItems.QUIVER)) {
			return true;
		}
		if (stack.is(ModItems.SEED_POUCH)) {
			return true;
		}
		if (stack.is(ModItems.FISH_BAIT)) {
			return true;
		}
		if (stack.is(ModItems.PET_WHISTLE)) {
			return true;
		}
		if (stack.is(ModItems.ROPE) || stack.is(ModItems.ROPE_LANTERN)) {
			return true;
		}
		return false;
	}
}
