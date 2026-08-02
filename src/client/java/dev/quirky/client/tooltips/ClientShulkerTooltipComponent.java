package dev.quirky.client.tooltips;

import dev.quirky.tooltips.ShulkerTooltipComponent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * 潜影盒内容 tooltip 的客户端绘制组件：9x3 网格逐格绘制物品与数量。
 *
 * <p>每格先画盒色暗化的底槽 + 盒色边框（普通盒用深灰），再画物品图标与数量，
 * 一眼可辨是打开的潜影盒 UI。
 */
public class ClientShulkerTooltipComponent implements ClientTooltipComponent {
	private static final int SLOT_SIZE = 16;
	private static final int PADDING = 4;
	private static final int COLS = 9;
	private static final int ROWS = 3;
	/** 底槽填充：盒色 25% 亮度，alpha 0xE0 */
	private static final int SLOT_FILL_ALPHA = 0xE0;
	/** 底槽边框：盒色 60% 亮度，alpha 0xFF */
	private static final int SLOT_BORDER_ALPHA = 0xFF;
	private static final int FILL_RATIO = 25;
	private static final int BORDER_RATIO = 60;
	/** 普通（无盒色）潜影盒的底槽颜色 */
	private static final int DEFAULT_SLOT_FILL = 0xE01B1B1B;
	private static final int DEFAULT_SLOT_BORDER = 0xFF4A4A4A;

	private final NonNullList<ItemStack> items;
	private final @Nullable DyeColor color;

	public ClientShulkerTooltipComponent(ShulkerTooltipComponent component) {
		items = NonNullList.withSize(COLS * ROWS, ItemStack.EMPTY);
		component.contents().copyInto(items);
		color = component.color();
	}

	@Override
	public int getWidth(Font font) {
		return COLS * SLOT_SIZE + 2 * PADDING;
	}

	@Override
	public int getHeight(Font font) {
		return ROWS * SLOT_SIZE + 2 * PADDING;
	}

	@Override
	public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
		int fillColor = slotFillColor();
		int borderColor = slotBorderColor();
		int startX = x + PADDING;
		int startY = y + PADDING;
		for (int slot = 0; slot < items.size(); slot++) {
			int sx = startX + (slot % COLS) * SLOT_SIZE;
			int sy = startY + (slot / COLS) * SLOT_SIZE;
			drawSlot(graphics, sx, sy, fillColor, borderColor);
			ItemStack stack = items.get(slot);
			if (!stack.isEmpty()) {
				graphics.item(stack, sx, sy);
				graphics.itemDecorations(font, stack, sx, sy);
			}
		}
	}

	/** 底槽 + 边框（1px 线），再画物品。 */
	private static void drawSlot(GuiGraphicsExtractor graphics, int x, int y, int fillColor, int borderColor) {
		graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, fillColor);
		graphics.horizontalLine(x, x + SLOT_SIZE - 1, y, borderColor);
		graphics.horizontalLine(x, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, borderColor);
		graphics.verticalLine(x, y, y + SLOT_SIZE - 1, borderColor);
		graphics.verticalLine(x + SLOT_SIZE - 1, y, y + SLOT_SIZE - 1, borderColor);
	}

	/** 底槽填充色：盒色 25% 亮度（ARGB），无盒色用深灰。 */
	private int slotFillColor() {
		if (color == null) {
			return DEFAULT_SLOT_FILL;
		}
		return mix(color.getTextureDiffuseColor(), FILL_RATIO, SLOT_FILL_ALPHA);
	}

	/** 底槽边框色：盒色 60% 亮度（ARGB），无盒色用灰。 */
	private int slotBorderColor() {
		if (color == null) {
			return DEFAULT_SLOT_BORDER;
		}
		return mix(color.getTextureDiffuseColor(), BORDER_RATIO, SLOT_BORDER_ALPHA);
	}

	private static int mix(int rgb, int ratio, int alpha) {
		int r = ((rgb >> 16) & 0xFF) * ratio / 100;
		int g = ((rgb >> 8) & 0xFF) * ratio / 100;
		int b = (rgb & 0xFF) * ratio / 100;
		return alpha << 24 | r << 16 | g << 8 | b;
	}
}
