package dev.quirky.tooltips;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * 潜影盒内容 tooltip 的服务端组件：携带容器内容，客户端转换为绘制组件。
 */
public record ShulkerTooltipComponent(ItemContainerContents contents) implements TooltipComponent {
}
