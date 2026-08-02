package dev.quirky.client.usage_ticker;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 背包快照与差异检测（纯逻辑，可单测）。
 *
 * 每 tick 捕获 41 槽（0..35 主背包 + 36..39 盔甲 + 40 副手）的 (槽位, 物品, 数量) 并对比：
 * 同帧恰好 1 个槽变化视为拾取/消耗事件；多个槽同时变化视为整理/交换，不触发。
 */
public final class TickerSnapshot {
	/** 快照槽位数：36 背包 + 4 盔甲 + 副手 40，共 41。 */
	public static final int SLOT_COUNT = Inventory.SLOT_OFFHAND + 1;

	/** 单槽快照。 */
	public record SlotSnapshot(int slot, Item item, int count) {}

	/** 数量变化事件：item 为变化后的物品，newCount 为变化后的数量，delta = 变化量（拾取正/消耗负）。 */
	public record TickerEvent(Item item, int newCount, int delta) {}

	private TickerSnapshot() {
	}

	public static List<SlotSnapshot> capture(Player player) {
		Inventory inventory = player.getInventory();
		List<SlotSnapshot> snapshots = new ArrayList<>(SLOT_COUNT);
		for (int slot = 0; slot < SLOT_COUNT; slot++) {
			ItemStack stack = inventory.getItem(slot);
			snapshots.add(new SlotSnapshot(slot, stack.getItem(), stack.getCount()));
		}
		return snapshots;
	}

	/**
	 * 对比前后两份快照。
	 *
	 * @return 恰好 1 个槽发生变化时返回该槽的事件（item 取 after 的 item，数量取 after 的数量），
	 *         否则 {@link Optional#empty()}
	 */
	public static Optional<TickerEvent> diff(List<SlotSnapshot> before, List<SlotSnapshot> after) {
		if (before.size() != after.size()) {
			return Optional.empty();
		}
		int changedIndex = -1;
		for (int i = 0; i < before.size(); i++) {
			SlotSnapshot b = before.get(i);
			SlotSnapshot a = after.get(i);
			if (b.slot() != a.slot()) {
				return Optional.empty();
			}
			if (Objects.equals(b.item(), a.item()) && b.count() == a.count()) {
				continue;
			}
			if (changedIndex != -1) {
				return Optional.empty();
			}
			changedIndex = i;
		}
		if (changedIndex == -1) {
			return Optional.empty();
		}
		SlotSnapshot b = before.get(changedIndex);
		SlotSnapshot a = after.get(changedIndex);
		return Optional.of(new TickerEvent(a.item(), a.count(), a.count() - b.count()));
	}
}
