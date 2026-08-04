package dev.quirky.client.tooltips;

import dev.quirky.tooltips.AttributeLineCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * 属性 tooltip 横条与原版属性文本段的可见性互斥判定（对齐 Quark AttributeTooltips）：
 * 未按 Shift 且横条有内容 → 原版属性文本段隐藏、横条显示；按 Shift → 原版文本段显示、横条隐藏。
 * 单测环境无客户端实例（Minecraft.getInstance() == null）一律视为未按 Shift。
 */
public final class AttributeTooltipVisibility {
	private AttributeTooltipVisibility() {
	}

	/** 按住 Shift 时隐藏紧凑横条（原版文本段对照查看） */
	public static boolean shiftHidesCompactRow(@Nullable Minecraft minecraft) {
		return minecraft != null && minecraft.hasShiftDown();
	}

	/**
	 * 原版属性文本段是否应隐藏：未按 Shift 且横条确有可替代内容
	 * （AttributeLineCollector 只渲染 6 类属性；携带其他修饰符的物品横条为空时不隐藏，
	 * 避免属性信息静默丢失）。
	 */
	public static boolean vanillaTextHidden(@Nullable Minecraft minecraft, ItemStack stack) {
		if (shiftHidesCompactRow(minecraft)) {
			return false;
		}
		return !AttributeLineCollector.collect(stack, RegistryAccess.EMPTY).isEmpty();
	}
}
