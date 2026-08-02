package dev.quirky.client.usage_ticker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 护甲耐久挂件：每 tick 读取 4 个盔甲槽（36..39）的 damage，耐久下降时刷新右侧挂件；
 * 耐久连续不变约 3 秒（60 tick，hold 阶段由 {@link TickerElement} 负责）后滑回。
 */
public final class ArmorTicker {
	/** 耐久不变后收回的保持时长（tick），约 3 秒。 */
	public static final int HOLD_TICKS = 60;

	private static final int ARMOR_SLOT_START = 36;
	private static final int ARMOR_SLOT_END = 39;
	private static final int SLOT_SIZE = 16;
	private static final int SLOT_GAP = 4;
	private static final int BAR_HEIGHT = 2;

	private static final int[] prevDamage = new int[ARMOR_SLOT_END - ARMOR_SLOT_START + 1];
	private static boolean hasPrevious;

	private ArmorTicker() {
	}

	/**
	 * 每 tick 调用一次。
	 *
	 * @return 是否需要刷新挂件（耐久下降——耐久不变则返回 false，由元素自行进入保持/滑出阶段）
	 */
	public static boolean tick(Player player) {
		int[] current = readDamage(player);
		boolean increased = false;
		if (hasPrevious) {
			for (int i = 0; i < prevDamage.length; i++) {
				if (current[i] > prevDamage[i]) {
					increased = true;
				}
			}
		}
		System.arraycopy(current, 0, prevDamage, 0, prevDamage.length);
		hasPrevious = true;
		return increased;
	}

	/** 玩家切换（重进世界）后丢弃上次的耐久基线，避免跨世界误触发。 */
	public static void reset() {
		hasPrevious = false;
		java.util.Arrays.fill(prevDamage, 0);
	}

	/**
	 * 在 (x, y) 起绘制横排 4 件护甲（图标 + 下方耐久条），每件 16px 宽 + 4px 间隔；
	 * 未穿戴的槽位跳过（不占位）。
	 */
	public static void render(GuiGraphicsExtractor graphics, Minecraft minecraft, int x, int y) {
		Player player = minecraft.player;
		if (player == null) {
			return;
		}
		Inventory inventory = player.getInventory();
		for (int slot = ARMOR_SLOT_START; slot <= ARMOR_SLOT_END; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty()) {
				graphics.item(stack, x, y);
				if (stack.isDamageableItem()) {
					drawDurabilityBar(graphics, x, y, stack);
				}
			}
			x += SLOT_SIZE + SLOT_GAP;
		}
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

	private static int[] readDamage(Player player) {
		int[] damage = new int[prevDamage.length];
		Inventory inventory = player.getInventory();
		for (int i = 0; i < damage.length; i++) {
			ItemStack stack = inventory.getItem(ARMOR_SLOT_START + i);
			damage[i] = stack.isEmpty() || !stack.isDamageableItem() ? 0 : stack.getDamageValue();
		}
		return damage;
	}
}
