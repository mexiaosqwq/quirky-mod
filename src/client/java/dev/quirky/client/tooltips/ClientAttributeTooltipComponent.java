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
 * 属性 tooltip 的客户端绘制组件：所有属性排成单行横排 {@code [16x16 图标][数值]}，
 * 对齐 Quark AttributeTooltips 的紧凑横条样式（Quark 为 9x9 图标 + 每格 text+20 步进，
 * 此处沿用已定稿的 16x16 图标，单元格步进 = 18 + 文本宽 + 8）。
 * 按住 Shift 时隐藏横条（getWidth/getHeight 返回 0 且不绘制），原版属性文本段
 * 由 AttributeTextHideMixin 在未按 Shift 时隐藏，两者互斥对齐 Quark 行为。
 */
public class ClientAttributeTooltipComponent implements ClientTooltipComponent {
	private static final int ICON_SIZE = 16;
	private static final int ICON_TEXT_GAP = 2;
	private static final int CELL_GAP = 8;
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
				width += CELL_GAP;
			}
			width += ICON_SIZE + ICON_TEXT_GAP + font.width(line.text());
		}
		return width;
	}

	@Override
	public int getHeight(Font font) {
		return shiftHidesLines() || lines.isEmpty() ? 0 : ICON_SIZE;
	}

	@Override
	public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
		if (shiftHidesLines()) {
			return;
		}
		int cursorX = x;
		for (AttributeLine line : lines) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, line.icon(), cursorX, y, ICON_SIZE, ICON_SIZE);
			graphics.text(font, line.text(), cursorX + ICON_SIZE + ICON_TEXT_GAP, y + (ICON_SIZE - 8) / 2, TEXT_COLOR);
			cursorX += ICON_SIZE + ICON_TEXT_GAP + font.width(line.text()) + CELL_GAP;
		}
	}

	private static boolean shiftHidesLines() {
		Minecraft minecraft = Minecraft.getInstance();
		// 单测环境无客户端实例，视为未按 Shift
		return minecraft != null && minecraft.hasShiftDown();
	}
}
