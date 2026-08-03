package dev.quirky.client.tooltips;

import dev.quirky.tooltips.ShulkerTooltipComponent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * 潜影盒内容 tooltip 的客户端绘制组件：9x3 网格逐格绘制原版槽位、物品与数量。
 *
 * <p>外层背景和边框由 Minecraft tooltip 渲染器提供，槽位使用原版
 * {@code minecraft:container/slot} GUI sprite。
 */
public class ClientShulkerTooltipComponent implements ClientTooltipComponent {
	/** 原版容器槽位贴图，18px 槽位内含 16px 物品绘制区。 */
	private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
	private static final int SLOT_SIZE = 18;
	private static final int ICON_OFFSET = 1;
	private static final int COLS = 9;
	private static final int ROWS = 3;

	private final NonNullList<ItemStack> items;

	public ClientShulkerTooltipComponent(ShulkerTooltipComponent component) {
		items = NonNullList.withSize(COLS * ROWS, ItemStack.EMPTY);
		component.contents().copyInto(items);
	}

	@Override
	public int getWidth(Font font) {
		return COLS * SLOT_SIZE;
	}

	@Override
	public int getHeight(Font font) {
		return ROWS * SLOT_SIZE;
	}

	@Override
	public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
		boolean empty = true;
		for (int slot = 0; slot < items.size(); slot++) {
			int sx = x + (slot % COLS) * SLOT_SIZE;
			int sy = y + (slot / COLS) * SLOT_SIZE;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, sx, sy, SLOT_SIZE, SLOT_SIZE);
			ItemStack stack = items.get(slot);
			if (!stack.isEmpty()) {
				empty = false;
				graphics.item(stack, sx + ICON_OFFSET, sy + ICON_OFFSET);
				graphics.itemDecorations(font, stack, sx + ICON_OFFSET, sy + ICON_OFFSET);
			}
		}
		// 空盒：网格不变，中央显示空状态文案
		if (empty) {
			Component label = Component.translatable("tooltip.quirky.shulker.empty");
			int textX = x + (getWidth(font) - font.width(label)) / 2;
			int textY = y + (getHeight(font) - TooltipRowMetrics.TEXT_HEIGHT) / 2;
			graphics.text(font, label, textX, textY, 0x8AFFFFFF);
		}
	}
}
