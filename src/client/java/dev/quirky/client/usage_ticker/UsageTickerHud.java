package dev.quirky.client.usage_ticker;

import java.util.Map;
import java.util.Optional;

import dev.quirky.QuirkyMod;
import dev.quirky.client.usage_ticker.TickerSnapshot.TickerEvent;
import dev.quirky.config.QuirkyConfigHolder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 使用量挂件（对齐 Quark UsageTicker）：
 * 快捷栏左侧显示最近数量变化的物品（图标 + 背包总数），保持后滑回；
 * 快捷栏右侧显示 4 件护甲的耐久条，耐久连续不变约 3 秒后滑回。
 *
 * 检测：每 tick 按物品累加 41 槽总数并对比相邻 tick（拾取/消耗事件），
 * 主手物品切换也触发；整理背包（同物品槽位重排）总数不变不触发。
 *
 * 渲染时机：MC 26.2 的 HUD 已改为 extract-render 管线（{@link GuiGraphicsExtractor}，
 * 原 Gui.render 与 HudRenderCallback 均不存在），故通过 Fabric API 25.3 的
 * {@link HudElementRegistry} 把挂件附着到原版快捷栏元素之后——HUD 隐藏（F1）时随快捷栏一并跳过。
 * 注意：26.2 用 {@code attachElementAfter}（往已有元素后插入新元素），
 * {@code addLast} 会因原版 hotbar layer 已存在抛 "Layer with identifier minecraft:hotbar already exists"。
 */
public final class UsageTickerHud {
	/** 原版快捷栏：宽 182（半宽 91）、高 22，位于屏幕底部（见 Hud.extractItemHotbar）。 */
	private static final int HOTBAR_HALF_WIDTH = 91;
	private static final int HOTBAR_HEIGHT = 22;
	private static final int SLOT_SIZE = 16;
	/** 图标 16px 在 22px 高的快捷栏内垂直居中。 */
	private static final int ICON_Y_INSET = 3;
	/** 挂件与快捷栏之间的间隙。 */
	private static final int HOTBAR_GAP = 8;

	private static TickerElement itemElement;
	private static TickerElement armorElement;
	private static TickerEvent currentEvent;
	/** null 表示基线未建立（玩家切换后首个 tick），见 {@link TickerSnapshot#diff}。 */
	private static Map<Item, Integer> lastTotals;
	/** 主手物品走 26.2 客户端装备槽（equipment MAINHAND），热键切换有 1~2 tick 回显延迟，属正常。 */
	private static Item lastMainHand = Items.AIR;
	private static Player lastPlayer;

	private UsageTickerHud() {
	}

	public static void init() {
		var config = QuirkyConfigHolder.get();
		itemElement = new TickerElement(config.tickerAnimTicks, config.tickerHoldTicks);
		armorElement = new TickerElement(config.tickerAnimTicks, ArmorTicker.HOLD_TICKS);
		HudElementRegistry.attachElementAfter(
			VanillaHudElements.HOTBAR,
			QuirkyMod.id("usage_ticker"),
			UsageTickerHud::render
		);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player != null) {
				tick(mc.player);
			}
		});
	}

	private static void tick(Player player) {
		if (!QuirkyConfigHolder.get().usageTicker) {
			itemElement.reset();
			armorElement.reset();
			return;
		}
		if (player != lastPlayer) {
			lastPlayer = player;
			lastTotals = null;
			lastMainHand = Items.AIR;
			itemElement.reset();
			armorElement.reset();
			ArmorTicker.reset();
		}
		Item mainHand = player.getMainHandItem().getItem();
		Map<Item, Integer> totals = TickerSnapshot.captureTotals(player);
		Optional<TickerEvent> event = TickerSnapshot.diff(lastTotals, totals, lastMainHand, mainHand);
		lastTotals = totals;
		lastMainHand = mainHand;
		// 数量归零（消耗最后一件）时物品已消失，无从显示，不触发。
		boolean active = event.isPresent() && event.get().newCount() > 0;
		if (active) {
			currentEvent = event.get();
		}
		itemElement.tick(active);
		armorElement.tick(ArmorTicker.tick(player));
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!QuirkyConfigHolder.get().usageTicker) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
		int center = graphics.guiWidth() / 2;
		int hotbarY = graphics.guiHeight() - HOTBAR_HEIGHT;
		if (itemElement.isVisible() && currentEvent != null) {
			renderItemTicker(graphics, minecraft, center - HOTBAR_HALF_WIDTH, hotbarY, partialTick);
		}
		if (armorElement.isVisible()) {
			ArmorTicker.render(
				graphics, minecraft,
				center + HOTBAR_HALF_WIDTH + HOTBAR_GAP,
				animatedY(hotbarY + ICON_Y_INSET, armorElement, partialTick)
			);
		}
	}

	private static void renderItemTicker(GuiGraphicsExtractor graphics, Minecraft minecraft, int hotbarLeft, int hotbarY, float partialTick) {
		int y = animatedY(hotbarY + ICON_Y_INSET, itemElement, partialTick);
		int x = hotbarLeft - SLOT_SIZE - HOTBAR_GAP;
		graphics.fill(x - 1, y - 1, x + 17, y + 17, 0x40000000);
		TickerEvent event = currentEvent;
		ItemStack display = new ItemStack(event.item(), Math.max(1, event.newCount()));
		graphics.item(display, x, y);
		graphics.itemDecorations(minecraft.font, display, x, y, String.valueOf(totalCount(minecraft.player, event)));
	}

	/**
	 * 背包内该物品的总数：遍历背包按 isSameItemSameComponents 求和，再与事件时刻总数取较大值
	 * （带自定义组件的堆叠可能比对不上，兜底显示事件时刻的总数）。
	 */
	private static int totalCount(Player player, TickerEvent event) {
		ItemStack reference = new ItemStack(event.item());
		int total = 0;
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, reference)) {
				total += stack.getCount();
			}
		}
		return Math.max(total, event.newCount());
	}

	/**
	 * 按动画状态计算纵向位移：从下方滑入 20px，ease-out 曲线 offset = -p*(p-2)*20；
	 * 滑出为反向动画（下移 20px 后消失）。partialTick 用于帧间插值，平滑 tick 步进。
	 */
	private static int animatedY(int baseY, TickerElement element, float partialTick) {
		float p = element.progress(partialTick);
		int offset = Math.round(-p * (p - 2) * TickerElement.SLIDE_DISTANCE);
		return switch (element.state()) {
			case SLIDE_IN -> baseY + TickerElement.SLIDE_DISTANCE - offset;
			case SLIDE_OUT -> baseY + offset;
			case HOLD, IDLE -> baseY;
		};
	}
}
