package dev.quirky.tooltips;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * 食物 tooltip 的服务端组件：携带 FoodProperties，客户端转换为「鸡腿 + 饱和度」图标数值行。
 */
public record FoodTooltipComponent(FoodProperties food) implements TooltipComponent {
}
