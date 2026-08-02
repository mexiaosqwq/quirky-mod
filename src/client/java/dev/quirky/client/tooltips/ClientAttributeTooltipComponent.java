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
 * 属性图标 tooltip 的客户端绘制组件：每行 16x16 图标 + 数值文本，行高 14px 紧凑排布。
 * 按住 Shift 时隐藏自定义行（getWidth/getHeight 返回 0 且不绘制），原版文本行始终保留。
 */
public class ClientAttributeTooltipComponent implements ClientTooltipComponent {
	private static final int ICON_SIZE = 16;
	private static final int ICON_TEXT_GAP = 2;
	private static final int LINE_HEIGHT = 14;
	private static final int TEXT_COLOR = 0xFFFFFFFF;

	private final List<AttributeLine> lines;

	public ClientAttributeTooltipComponent(AttributeTooltipComponent component) {
		lines = component.lines();
	}

	@Override
	public int getWidth(Font font) {
		if (shiftHidesLines()) {
			return 0;
		}
		int width = 0;
		for (AttributeLine line : lines) {
			width = Math.max(width, ICON_SIZE + ICON_TEXT_GAP + font.width(line.text()));
		}
		return width;
	}

	@Override
	public int getHeight(Font font) {
		return shiftHidesLines() ? 0 : lines.size() * LINE_HEIGHT;
	}

	@Override
	public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
		if (shiftHidesLines()) {
			return;
		}
		int cursorY = y;
		for (AttributeLine line : lines) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, line.icon(), x, cursorY, ICON_SIZE, ICON_SIZE);
			graphics.text(font, line.text(), x + ICON_SIZE + ICON_TEXT_GAP, cursorY + (LINE_HEIGHT - 8) / 2, TEXT_COLOR);
			cursorY += LINE_HEIGHT;
		}
	}

	private static boolean shiftHidesLines() {
		Minecraft minecraft = Minecraft.getInstance();
		// 单测环境无客户端实例，视为未按 Shift
		return minecraft != null && minecraft.hasShiftDown();
	}
}
