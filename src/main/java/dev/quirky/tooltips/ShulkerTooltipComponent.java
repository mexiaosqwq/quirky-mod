package dev.quirky.tooltips;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jspecify.annotations.Nullable;

/**
 * 潜影盒内容 tooltip 的服务端组件：携带容器内容与盒子外表颜色（16 色，普通盒为 null），
 * 客户端转换为绘制组件（底槽配色随盒色）。
 */
public record ShulkerTooltipComponent(ItemContainerContents contents, @Nullable DyeColor color) implements TooltipComponent {
}
