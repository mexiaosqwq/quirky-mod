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
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.math.Fraction;

/**
 * 箭袋内容 tooltip 的客户端绘制组件（对齐原版收纳袋 Bundle 样式，2026-08-05 用户要求）：
 * 4 列 24px 槽位网格 + 底部容量进度条；有内容时显示各组的物品图标与数量，
 * 空箭袋显示"空"字样 + 空进度条。容量按"组"计（与 {@code BundleContents.weight} 的
 * 槽位重量语义不同），进度条填充 = 已用组数 / 容量。
 */
public class ClientQuiverTooltipComponent implements ClientTooltipComponent {
	private static final Identifier SLOT_BACKGROUND_SPRITE = Identifier.withDefaultNamespace("container/bundle/slot_background");
	private static final Identifier PROGRESSBAR_BORDER_SPRITE = Identifier.withDefaultNamespace("container/bundle/bundle_progressbar_border");
	private static final Identifier PROGRESSBAR_FILL_SPRITE = Identifier.withDefaultNamespace("container/bundle/bundle_progressbar_fill");
	private static final Identifier PROGRESSBAR_FULL_SPRITE = Identifier.withDefaultNamespace("container/bundle/bundle_progressbar_full");
	private static final int SLOT_SIZE = 24;
	private static final int ICON_INSET = 4;
	private static final int COLS = 4;
	private static final int GRID_WIDTH = 96;
	private static final int PROGRESSBAR_HEIGHT = 13;
	private static final int PROGRESSBAR_FILL_MAX = 94;
	private static final int MARGIN_Y = 4;

	private final List<ItemStack> items;
	private final int usedGroups;
	private final int capacity;

	public ClientQuiverTooltipComponent(QuiverTooltipComponent component) {
		items = new ArrayList<>();
		component.contents().allItemsCopyStream().forEach(items::add);
		usedGroups = (int) items.stream().filter(stack -> !stack.isEmpty()).count();
		capacity = Math.max(1, component.capacity());
	}

	@Override
	public int getWidth(Font font) {
		return GRID_WIDTH;
	}

	@Override
	public int getHeight(Font font) {
		return gridHeight() + MARGIN_Y + PROGRESSBAR_HEIGHT + MARGIN_Y;
	}

	private int gridHeight() {
		return gridRows() * SLOT_SIZE;
	}

	private int gridRows() {
		return Math.max(1, (usedGroups + COLS - 1) / COLS);
	}

	@Override
	public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
		int left = x + (w - GRID_WIDTH) / 2;
		if (usedGroups > 0) {
			extractGrid(font, left, y, graphics);
		} else {
			graphics.text(font, Component.translatable("tooltip.quirky.quiver.empty"),
				left + 2, y + MARGIN_Y, 0xFF808080);
		}
		Fraction weight = Fraction.getFraction(usedGroups, capacity);
		extractProgressbar(font, left, y + gridHeight() + MARGIN_Y, graphics, weight);
	}

	private void extractGrid(Font font, int x, int y, GuiGraphicsExtractor graphics) {
		for (int slot = 0; slot < items.size(); slot++) {
			int sx = x + (slot % COLS) * SLOT_SIZE;
			int sy = y + (slot / COLS) * SLOT_SIZE;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_BACKGROUND_SPRITE, sx, sy, SLOT_SIZE, SLOT_SIZE);
			ItemStack stack = items.get(slot);
			if (!stack.isEmpty()) {
				graphics.item(stack, sx + ICON_INSET, sy + ICON_INSET, slot);
				graphics.itemDecorations(font, stack, sx + ICON_INSET, sy + ICON_INSET);
			}
		}
	}

	private static void extractProgressbar(Font font, int x, int y, GuiGraphicsExtractor graphics, Fraction weight) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
			weight.compareTo(Fraction.ONE) >= 0 ? PROGRESSBAR_FULL_SPRITE : PROGRESSBAR_FILL_SPRITE,
			x + 1, y, Mth.clamp(Mth.mulAndTruncate(weight, PROGRESSBAR_FILL_MAX), 0, PROGRESSBAR_FILL_MAX), PROGRESSBAR_HEIGHT);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PROGRESSBAR_BORDER_SPRITE, x, y, GRID_WIDTH, PROGRESSBAR_HEIGHT);
		Component fillText = weight.compareTo(Fraction.ZERO) == 0
			? Component.translatable("tooltip.quirky.quiver.empty")
			: weight.compareTo(Fraction.ONE) >= 0 ? Component.translatable("item.minecraft.bundle.full") : null;
		if (fillText != null) {
			graphics.centeredText(font, fillText, x + GRID_WIDTH / 2, y + 3, -1);
		}
	}
}
