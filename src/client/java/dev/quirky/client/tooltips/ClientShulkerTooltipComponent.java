package dev.quirky.client.tooltips;

import dev.quirky.tooltips.ShulkerTooltipComponent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

/**
 * 潜影盒内容 tooltip 的客户端绘制组件：9x3 网格逐格绘制物品与数量。
 */
public class ClientShulkerTooltipComponent implements ClientTooltipComponent {
	private static final int SLOT_SIZE = 16;
	private static final int PADDING = 4;
	private static final int COLS = 9;
	private static final int ROWS = 3;

	private final NonNullList<ItemStack> items;

	public ClientShulkerTooltipComponent(ShulkerTooltipComponent component) {
		items = NonNullList.withSize(COLS * ROWS, ItemStack.EMPTY);
		component.contents().copyInto(items);
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
		int startX = x + PADDING;
		int startY = y + PADDING;
		for (int slot = 0; slot < items.size(); slot++) {
			ItemStack stack = items.get(slot);
			if (stack.isEmpty()) {
				continue;
			}
			int sx = startX + (slot % COLS) * SLOT_SIZE;
			int sy = startY + (slot / COLS) * SLOT_SIZE;
			graphics.item(stack, sx, sy);
			graphics.itemDecorations(font, stack, sx, sy);
		}
	}
}
