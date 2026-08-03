package dev.quirky.tooltips;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * 箭袋内容 tooltip 的服务端组件：携带容器内容与容量（组），客户端转换为绘制组件。
 */
public record QuiverTooltipComponent(ItemContainerContents contents, int capacity) implements TooltipComponent {
}
