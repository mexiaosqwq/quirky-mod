package dev.quirky.client.tooltips;

import java.util.List;

import dev.quirky.tooltips.AttributeTooltipComponent;
import dev.quirky.tooltips.AttributeTooltipComponent.AttributeLine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * 属性 tooltip 的客户端绘制组件：所有属性排成单行横排 {@code [9x9 图标][数值]}，
 * 度量与食物行统一（{@link TooltipRowMetrics}：16px 行高、垂直居中、间距 2/4），
 * 对齐 Quark AttributeTooltips 紧凑横条（9x9 图标 + 8px 文本）。16x16 原稿经
 * 最近邻缩放 9x9 仍保持全部图标可辨（像素网格逐图验证）。
 * 按住 Shift 时隐藏横条（getWidth/getHeight 返回 0 且不绘制），原版属性文本段
 * 由 AttributeTextHideMixin 在未按 Shift 时隐藏，两者互斥对齐 Quark 行为。
 * 可见性判定集中在 {@link AttributeTooltipVisibility}，此处只负责横排几何。
 */
public class ClientAttributeTooltipComponent implements ClientTooltipComponent {
	private static final int ICON_SIZE = 9;
	private static final int TEXT_COLOR = 0xFFFFFFFF;

	private final List<AttributeLine> lines;

	public ClientAttributeTooltipComponent(AttributeTooltipComponent component) {
		lines = component.lines();
	}

	@Override
	public int getWidth(Font font) {
		if (shiftHidesLines() || lines.isEmpty()) {
			return 0;
		}
		int width = 0;
		for (AttributeLine line : lines) {
			if (width > 0) {
				width += TooltipRowMetrics.CELL_GAP;
			}
			width += ICON_SIZE + TooltipRowMetrics.ICON_TEXT_GAP + font.width(line.text());
		}
		return width;
	}

	@Override
	public int getHeight(Font font) {
		return shiftHidesLines() || lines.isEmpty() ? 0 : TooltipRowMetrics.LINE_HEIGHT;
	}

	@Override
	public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
		if (shiftHidesLines()) {
			return;
		}
		int yIcon = TooltipRowMetrics.iconY(y, ICON_SIZE);
		int yText = TooltipRowMetrics.textY(y);
		int cursorX = x;
		for (AttributeLine line : lines) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, line.icon(), cursorX, yIcon, ICON_SIZE, ICON_SIZE);
			graphics.text(font, line.text(), cursorX + ICON_SIZE + TooltipRowMetrics.ICON_TEXT_GAP, yText, TEXT_COLOR);
			cursorX += ICON_SIZE + TooltipRowMetrics.ICON_TEXT_GAP + font.width(line.text()) + TooltipRowMetrics.CELL_GAP;
		}
	}

	private static boolean shiftHidesLines() {
		return AttributeTooltipVisibility.shiftHidesCompactRow(Minecraft.getInstance());
	}
}
