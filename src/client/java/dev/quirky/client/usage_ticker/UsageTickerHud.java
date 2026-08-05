package dev.quirky.client.usage_ticker;

import java.util.List;
import java.util.Optional;

import dev.quirky.QuirkyMod;
import dev.quirky.client.usage_ticker.TickerSnapshot.InventorySnapshot;
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

/**
 * 使用量挂件（对齐 Quark UsageTicker）：
 * 快捷栏左侧显示最近数量变化的物品（图标 + 背包总数），保持后滑回；
 * 快捷栏右侧显示耐久变化——护甲按槽位独立显示在 4 个固定位置（哪件变化哪件弹，
 * 对齐 Quark 槽位元素），工具/副手耐久变化显示在护甲位之后的浮动列表。
 *
 * 检测（通用，见 {@link TickerSnapshot}）：每 tick 一次遍历全背包快照并对比——
 * 数量变化（拾取/消耗/放置）→ 左侧；装备槽摆放变化兑底 → 左侧；护甲槽变化（穿脱/换装/耐久升降）→
 * 对应固定位；工具/副手耐久变化（损坏/修复）→ 浮动列表。同物品槽位重排（整理背包）聚合状态不变，不触发。
 * 主手切换不触发（2026-08-03 用户确认：纯切换无数量变化）。
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
	/** 盔甲槽位区间（36..39，FEET→HEAD）。 */
	private static final int ARMOR_SLOT_START = 36;
	private static final int ARMOR_SLOT_COUNT = 4;

	private static TickerElement itemElement;
	private static final TickerElement[] armorElements = new TickerElement[ARMOR_SLOT_COUNT];
	private static TickerElement toolElement;
	private static TickerEvent currentEvent;
	private static List<Item> toolItems = List.of();
	/** null 表示基线未建立（玩家切换后首个 tick），见 {@link TickerSnapshot#diffTotals}。 */
	private static InventorySnapshot lastSnapshot;
	private static Player lastPlayer;

	private UsageTickerHud() {
	}

	public static void init() {
		var config = QuirkyConfigHolder.get();
		itemElement = new TickerElement(config.tickerAnimTicks, config.tickerHoldTicks);
		for (int i = 0; i < ARMOR_SLOT_COUNT; i++) {
			armorElements[i] = new TickerElement(config.tickerAnimTicks, DurabilityTicker.HOLD_TICKS);
		}
		toolElement = new TickerElement(config.tickerAnimTicks, DurabilityTicker.HOLD_TICKS);
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
		if (player != lastPlayer) {
			lastPlayer = player;
			lastSnapshot = null;
			toolItems = List.of();
			itemElement.reset();
			for (TickerElement element : armorElements) {
				element.reset();
			}
			toolElement.reset();
		}
		InventorySnapshot snapshot = TickerSnapshot.capture(player);
		Optional<TickerEvent> event = TickerSnapshot.diffTotals(
			lastSnapshot == null ? null : lastSnapshot.totals(), snapshot.totals()
		);
		if (event.isEmpty()) {
			// 数量/主手无事件时，装备槽（副手/BODY/SADDLE）摆放变化兜底触发
			Optional<Item> equipChanged = TickerSnapshot.diffEquipment(
				lastSnapshot == null ? null : lastSnapshot.equipment(), snapshot.equipment()
			);
			event = equipChanged.map(item -> new TickerEvent(item, 1, 0));
		}
		boolean[] armorChanged = TickerSnapshot.diffArmorSlots(
			lastSnapshot == null ? null : lastSnapshot.armor(), snapshot.armor()
		);
		List<Item> durability = TickerSnapshot.diffToolDurability(
			lastSnapshot == null ? null : lastSnapshot.durability(), snapshot.durability(), snapshot.armor()
		);
		lastSnapshot = snapshot;
		// 数量归零（消耗最后一件）时物品已消失，无从显示，不触发。
		boolean itemActive = event.isPresent() && event.get().newCount() > 0;
		if (itemActive) {
			currentEvent = event.get();
		}
		if (!durability.isEmpty()) {
			toolItems = durability;
		}
		itemElement.tick(itemActive);
		for (int i = 0; i < ARMOR_SLOT_COUNT; i++) {
			armorElements[i].tick(armorChanged[i]);
		}
		toolElement.tick(!durability.isEmpty());
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
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
		int rightX = center + HOTBAR_HALF_WIDTH + HOTBAR_GAP;
		int baseY = hotbarY + ICON_Y_INSET;
		// 护甲 4 个固定位，各元素独立动画（对齐 Quark：哪件变化哪件弹）
		for (int i = 0; i < ARMOR_SLOT_COUNT; i++) {
			if (armorElements[i].isVisible()) {
				DurabilityTicker.renderSlot(graphics, minecraft,
					rightX + i * (DurabilityTicker.SLOT_SIZE + DurabilityTicker.SLOT_GAP),
					animatedY(baseY, armorElements[i], partialTick),
					ARMOR_SLOT_START + i);
			}
		}
		// 工具/副手耐久浮动列表（护甲位之后；窄屏时左移钳制，避免超出屏幕右缘）
		if (toolElement.isVisible() && !toolItems.isEmpty()) {
			int toolX = rightX + ARMOR_SLOT_COUNT * (DurabilityTicker.SLOT_SIZE + DurabilityTicker.SLOT_GAP);
			int listWidth = toolItems.size() * (DurabilityTicker.SLOT_SIZE + DurabilityTicker.SLOT_GAP)
				- DurabilityTicker.SLOT_GAP;
			toolX = Math.min(toolX, graphics.guiWidth() - listWidth - HOTBAR_GAP);
			DurabilityTicker.renderToolList(graphics, minecraft, toolX,
				animatedY(baseY, toolElement, partialTick), toolItems);
		}
	}

	private static void renderItemTicker(GuiGraphicsExtractor graphics, Minecraft minecraft, int hotbarLeft, int hotbarY, float partialTick) {
		int y = animatedY(hotbarY + ICON_Y_INSET, itemElement, partialTick);
		int x = hotbarLeft - SLOT_SIZE - HOTBAR_GAP;
		graphics.fill(x - 1, y - 1, x + 17, y + 17, 0x40000000);
		TickerEvent event = currentEvent;
		// 用背包中的实际堆叠渲染（保留数据组件：药水种类/附魔/染色等），缺失时兑底默认实例
		ItemStack display = findStackInInventory(minecraft.player.getInventory(), event.item());
		if (display == null) {
			display = new ItemStack(event.item());
		}
		graphics.item(display, x, y);
		graphics.itemDecorations(minecraft.font, display, x, y, String.valueOf(totalCount(minecraft.player, event)));
	}

	/** 在背包中找该物品的第一个堆叠（保留组件）；空返回 null。 */
	private static ItemStack findStackInInventory(Inventory inventory, Item item) {
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && stack.is(item)) {
				return stack;
			}
		}
		return null;
	}

	/**
	 * 背包内该物品的总数：以背包实际堆叠为基准按 isSameItemSameComponents 求和（药水按种类精确），
	 * 再与事件时刻总数取较大值（带自定义组件的堆叠可能比对不上，兜底显示事件时刻的总数）。
	 */
	private static int totalCount(Player player, TickerEvent event) {
		ItemStack reference = findStackInInventory(player.getInventory(), event.item());
		if (reference == null) {
			return event.newCount();
		}
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
