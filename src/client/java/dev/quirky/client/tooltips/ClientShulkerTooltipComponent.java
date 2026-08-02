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
 * <p>整体背景 + 每格深色底槽 + 淡色边框，像打开的潜影盒 UI：
 * 普通盒用经典紫色调（深紫背景、淡紫边框）；16 色盒用盒色调（盒色 30% 背景、70% 边框）。
 */
public class ClientShulkerTooltipComponent implements ClientTooltipComponent {
	/** 槽尺寸：16 图标 + 2px 边距 */
	private static final int SLOT_SIZE = 18;
	private static final int ICON_SIZE = 16;
	private static final int ICON_OFFSET = 1;
	private static final int PADDING = 4;
	private static final int COLS = 9;
	private static final int ROWS = 3;

	/** 普通盒：经典紫色调（深紫罗兰背景 / 黑色半透明槽底 / 淡紫边框） */
	private static final int DEFAULT_BACKGROUND = 0xE03A2A5E;
	private static final int DEFAULT_SLOT = 0x4A000000;
	private static final int DEFAULT_BORDER = 0x8A8060C8;
	/** 盒色背景亮度比例（30%）与边框亮度比例（70%） */
	private static final int BG_RATIO = 30;
	private static final int BORDER_RATIO = 70;

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
		int background = background();
		int border = border();
		graphics.fill(x, y, x + getWidth(font), y + getHeight(font), background);
		int startX = x + PADDING;
		int startY = y + PADDING;
		for (int slot = 0; slot < items.size(); slot++) {
			int sx = startX + (slot % COLS) * SLOT_SIZE;
			int sy = startY + (slot / COLS) * SLOT_SIZE;
			drawSlot(graphics, sx, sy, border);
			ItemStack stack = items.get(slot);
			if (!stack.isEmpty()) {
				graphics.item(stack, sx + ICON_OFFSET, sy + ICON_OFFSET);
				graphics.itemDecorations(font, stack, sx + ICON_OFFSET, sy + ICON_OFFSET);
			}
		}
	}

	/** 深色槽底 + 1px 淡色边框。 */
	private static void drawSlot(GuiGraphicsExtractor graphics, int x, int y, int borderColor) {
		graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, DEFAULT_SLOT);
		graphics.horizontalLine(x, x + SLOT_SIZE - 1, y, borderColor);
		graphics.horizontalLine(x, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, borderColor);
		graphics.verticalLine(x, y, y + SLOT_SIZE - 1, borderColor);
		graphics.verticalLine(x + SLOT_SIZE - 1, y, y + SLOT_SIZE - 1, borderColor);
	}

	/** 整体背景：普通盒紫色调；16 色盒用盒色 30% 亮度。 */
	private int background() {
		if (color == null) {
			return DEFAULT_BACKGROUND;
		}
		return mix(color.getTextureDiffuseColor(), BG_RATIO, 0xE0);
	}

	/** 边框色：普通盒淡紫；16 色盒用盒色 70% 亮度。 */
	private int border() {
		if (color == null) {
			return DEFAULT_BORDER;
		}
		return mix(color.getTextureDiffuseColor(), BORDER_RATIO, 0x8A);
	}

	private static int mix(int rgb, int ratio, int alpha) {
		int r = ((rgb >> 16) & 0xFF) * ratio / 100;
		int g = ((rgb >> 8) & 0xFF) * ratio / 100;
		int b = (rgb & 0xFF) * ratio / 100;
		return alpha << 24 | r << 16 | g << 8 | b;
	}
}
