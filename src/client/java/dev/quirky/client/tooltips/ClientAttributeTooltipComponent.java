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
	/** 横排行最大宽度：超过则换行，避免六个属性挤爆 tooltip 边界。 */
	private static final int MAX_ROW_WIDTH = 128;

	private final List<AttributeLine> lines;

	public ClientAttributeTooltipComponent(AttributeTooltipComponent component) {
		lines = component.lines();
	}

	@Override
	public int getWidth(Font font) {
		if (shiftHidesLines() || lines.isEmpty()) {
			return 0;
		}
		int maxRowWidth = 0;
		int rowWidth = 0;
		for (AttributeLine line : lines) {
			int cell = cellWidth(font, line);
			if (rowWidth > 0 && rowWidth + TooltipRowMetrics.CELL_GAP + cell > MAX_ROW_WIDTH) {
				maxRowWidth = Math.max(maxRowWidth, rowWidth);
				rowWidth = cell;
			} else {
				rowWidth = rowWidth == 0 ? cell : rowWidth + TooltipRowMetrics.CELL_GAP + cell;
			}
		}
		return Math.max(maxRowWidth, rowWidth);
	}

	@Override
	public int getHeight(Font font) {
		if (shiftHidesLines() || lines.isEmpty()) {
			return 0;
		}
		int rows = 1;
		int rowWidth = 0;
		for (AttributeLine line : lines) {
			int cell = cellWidth(font, line);
			if (rowWidth > 0 && rowWidth + TooltipRowMetrics.CELL_GAP + cell > MAX_ROW_WIDTH) {
				rows++;
				rowWidth = cell;
			} else {
				rowWidth = rowWidth == 0 ? cell : rowWidth + TooltipRowMetrics.CELL_GAP + cell;
			}
		}
		return rows * TooltipRowMetrics.LINE_HEIGHT;
	}

	@Override
	public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
		if (shiftHidesLines()) {
			return;
		}
		int row = 0;
		int cursorX = x;
		for (AttributeLine line : lines) {
			int cell = cellWidth(font, line);
			if (cursorX > x && cursorX + cell - x > MAX_ROW_WIDTH) {
				row++;
				cursorX = x;
			}
			int rowY = y + row * TooltipRowMetrics.LINE_HEIGHT;
			int yIcon = TooltipRowMetrics.iconY(rowY, ICON_SIZE);
			int yText = TooltipRowMetrics.textY(rowY);
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, line.icon(), cursorX, yIcon, ICON_SIZE, ICON_SIZE);
			graphics.text(font, line.text(), cursorX + ICON_SIZE + TooltipRowMetrics.ICON_TEXT_GAP, yText, TEXT_COLOR);
			cursorX += cell + TooltipRowMetrics.CELL_GAP;
		}
	}

	private static int cellWidth(Font font, AttributeLine line) {
		return ICON_SIZE + TooltipRowMetrics.ICON_TEXT_GAP + font.width(line.text());
	}

	private static boolean shiftHidesLines() {
		return AttributeTooltipVisibility.shiftHidesCompactRow(Minecraft.getInstance());
	}
}
