package dev.quirky.client.usage_ticker;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 耐久挂件（通用）：右侧横排显示耐久变化的物品（工具/副手/护甲通用，
 * 由 {@link TickerSnapshot#diffDurability} 提供变化列表），每件图标 + 耐久条。
 * 变化检测与动画状态都在挂件框架里，本类只负责绘制。
 *
 * 对齐 Quark：任何可损坏物品的耐久变化（损坏/修复/更换）都触发，
 * 不是护甲专属。变化停止约 3 秒（60 tick，hold 阶段由 {@link TickerElement} 负责）后滑回。
 */
public final class DurabilityTicker {
	/** 耐久不变后收回的保持时长（tick），约 3 秒。 */
	public static final int HOLD_TICKS = 60;

	private static final int SLOT_SIZE = 16;
	private static final int SLOT_GAP = 4;
	private static final int BAR_HEIGHT = 2;

	private DurabilityTicker() {
	}

	/**
	 * 在 (x, y) 起横排绘制变化的耐久物品（图标 + 下方耐久条），每件 16px 宽 + 4px 间隔；
	 * 物品已从背包消失（如换装移出）则跳过，不占位。
	 */
	public static void render(GuiGraphicsExtractor graphics, Minecraft minecraft, int x, int y, List<Item> items) {
		Player player = minecraft.player;
		if (player == null) {
			return;
		}
		Inventory inventory = player.getInventory();
		for (Item item : items) {
			ItemStack stack = findStack(inventory, item);
			if (stack == null) {
				continue;
			}
			graphics.item(stack, x, y);
			if (stack.isDamageableItem()) {
				drawDurabilityBar(graphics, x, y, stack);
			}
			x += SLOT_SIZE + SLOT_GAP;
		}
	}

	/**
	 * 在背包中找该物品的堆叠用于渲染：优先取耐久损耗最大（最接近损坏）的堆叠——
	 * 手中损坏的镐比备用满耐镐更值得展示；不可损坏物品（如南瓜头）直接取第一个。
	 * 实时读取，换装/修复后显示最新状态。
	 */
	private static ItemStack findStack(Inventory inventory, Item item) {
		ItemStack best = null;
		int bestDamage = -1;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || !stack.is(item)) {
				continue;
			}
			int damage = stack.isDamageableItem() ? stack.getDamageValue() : 0;
			if (damage > bestDamage) {
				bestDamage = damage;
				best = stack;
			}
		}
		return best;
	}

	private static void drawDurabilityBar(GuiGraphicsExtractor graphics, int x, int y, ItemStack stack) {
		int barY = y + SLOT_SIZE + 1;
		graphics.fill(x, barY, x + SLOT_SIZE, barY + BAR_HEIGHT, 0x60000000);
		float fraction = 1.0F - (float) stack.getDamageValue() / (float) stack.getMaxDamage();
		int width = Math.round(SLOT_SIZE * fraction);
		if (width > 0) {
			graphics.fill(x, barY, x + width, barY + BAR_HEIGHT - 1, barColor(fraction));
		}
	}

	private static int barColor(float fraction) {
		if (fraction > 0.6F) {
			return 0xFF55FF55;
		}
		if (fraction > 0.25F) {
			return 0xFFFFFF55;
		}
		return 0xFFFF5555;
	}
}
