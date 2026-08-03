package dev.quirky.tooltips;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.component.Consumable;
import org.jspecify.annotations.Nullable;

/**
 * 食物 tooltip 的服务端组件：携带 FoodProperties 与可选 Consumable，
 * 客户端转换为「鸡腿 + 饱和度」图标数值行及条件食用信息。
 */
public record FoodTooltipComponent(FoodProperties food, @Nullable Consumable consumable) implements TooltipComponent {
	public FoodTooltipComponent(FoodProperties food) {
		this(food, null);
	}
}
