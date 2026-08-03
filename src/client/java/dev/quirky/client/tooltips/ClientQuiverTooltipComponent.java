package dev.quirky.client.tooltips;

import java.util.ArrayList;
import java.util.List;

import dev.quirky.tooltips.QuiverTooltipComponent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * 箭袋内容 tooltip 的客户端绘制组件：每格一个 18px 槽位 + 物品图标与数量。
 * 有内容时最多 4 列（容量 5-8 组折成第二行）；空箭袋只显示"空"字样，不画空格子。
 */
public class ClientQuiverTooltipComponent implements ClientTooltipComponent {
	private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
	private static final int SLOT_SIZE = 18;
	private static final int ICON_OFFSET = 1;
	private static final int PADDING = 4;
	private static final int COLS = 4;
	private static final int TEXT_COLOR = 0xFF808080;

	private final List<ItemStack> items;
	private final boolean empty;

	public ClientQuiverTooltipComponent(QuiverTooltipComponent component) {
		items = new ArrayList<>();
		component.contents().allItemsCopyStream().forEach(items::add);
		empty = items.stream().noneMatch(stack -> !stack.isEmpty());
	}

	@Override
	public int getWidth(Font font) {
		if (empty) {
			return font.width(emptyText()) + 2 * PADDING;
		}
		int cols = Math.min(items.size(), COLS);
		return cols * SLOT_SIZE + 2 * PADDING;
	}

	@Override
	public int getHeight(Font font) {
		if (empty) {
			return font.lineHeight + 2 * PADDING;
		}
		int rows = (items.size() + COLS - 1) / COLS;
		return rows * SLOT_SIZE + 2 * PADDING;
	}

	@Override
	public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
		if (empty) {
			graphics.text(font, emptyText(), x + PADDING, y + PADDING, TEXT_COLOR);
			return;
		}
		for (int slot = 0; slot < items.size(); slot++) {
			int sx = x + PADDING + (slot % COLS) * SLOT_SIZE;
			int sy = y + PADDING + (slot / COLS) * SLOT_SIZE;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, sx, sy, SLOT_SIZE, SLOT_SIZE);
			ItemStack stack = items.get(slot);
			if (!stack.isEmpty()) {
				graphics.item(stack, sx + ICON_OFFSET, sy + ICON_OFFSET);
				graphics.itemDecorations(font, stack, sx + ICON_OFFSET, sy + ICON_OFFSET);
			}
		}
	}

	private static Component emptyText() {
		return Component.translatable("tooltip.quirky.quiver.empty");
	}
}
