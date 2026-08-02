package dev.quirky.tooltips;

import java.util.List;

import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * 属性图标 tooltip 的服务端组件：携带图标 + 数值文本行列表，客户端转换为图标行绘制。
 */
public record AttributeTooltipComponent(List<AttributeLine> lines) implements TooltipComponent {
	public record AttributeLine(Identifier icon, String text) {
	}
}
