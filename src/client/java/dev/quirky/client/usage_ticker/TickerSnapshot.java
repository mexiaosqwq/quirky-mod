package dev.quirky.client.usage_ticker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 背包统一快照与差异检测（纯逻辑，可单测）。
 *
 * 每 tick 一次遍历 43 槽（0..35 主背包 + 36..39 盔甲 + 40 副手 + 41 BODY + 42 SADDLE，
 * 槽位数取 {@link Inventory#getContainerSize()}），产出三类数据，按"变化类型"分发到
 * 左右两个挂件——一套检测，对所有使用量变化通用，不为具体场景单独写逻辑：
 *
 * <ul>
 *   <li><b>数量变化</b>（拾取/消耗/放置、主手切换）→ 左侧物品挂件（{@link #diffTotals}）</li>
 *   <li><b>耐久变化</b>（任何可损坏物品，含工具/副手/护甲，降或升）→ 右侧耐久挂件（{@link #diffDurability}）</li>
 *   <li><b>盔甲槽内容变化</b>（穿脱/换装）→ 右侧显示当前穿戴（{@link #diffDurability}）</li>
 * </ul>
 *
 * 数量与耐久均按物品聚合（不绑定槽位），同物品在槽位间移动（整理/交换）天然免疫；
 * 主手/副手换物品也只影响槽位不影响聚合，仅主手切换单独触发左侧。
 */
public final class TickerSnapshot {
	/** 单次遍历产出的快照。 */
	public record InventorySnapshot(
		/** (物品 → 背包总数)。 */
		Map<Item, Integer> totals,
		/** (可损坏物品 → 堆叠数 + 总耐久损耗)，按物品聚合。 */
		Map<Item, DurabilityState> durability,
		/** 4 个盔甲槽（36..39）的 (物品, 耐久)，槽位序。 */
		List<ArmorSlot> armor
	) {}

	/** 某可损坏物品的聚合耐久状态。 */
	public record DurabilityState(int count, int totalDamage) {}

	/** 单盔甲槽快照。 */
	public record ArmorSlot(Item item, int damage) {}

	/** 数量变化事件：item 为变化的物品，newCount 为变化后的背包总数，delta = 变化量（拾取正/消耗负）。 */
	public record TickerEvent(Item item, int newCount, int delta) {}

	private TickerSnapshot() {
	}

	/** 4 盔甲槽（36..39）在背包槽位中的区间。 */
	private static final int ARMOR_SLOT_START = 36;
	private static final int ARMOR_SLOT_END = 39;

	/**
	 * 一次遍历全背包槽位，产出总量/耐久/盔甲槽快照；空槽跳过，遍历顺序为槽位序（确定性）。
	 * 注意粒度：按 {@link Item} 分组（不比较数据组件），检测用粗粒度足够；
	 * 显示时的实时总数按 {@code ItemStack.isSameItemSameComponents} 更精确求和（见 UsageTickerHud.totalCount）。
	 */
	public static InventorySnapshot capture(Player player) {
		Inventory inventory = player.getInventory();
		Map<Item, Integer> totals = new LinkedHashMap<>();
		Map<Item, DurabilityState> durability = new LinkedHashMap<>();
		List<ArmorSlot> armor = new ArrayList<>(4);
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				if (slot >= ARMOR_SLOT_START && slot <= ARMOR_SLOT_END) {
					armor.add(new ArmorSlot(Items.AIR, 0));
				}
				continue;
			}
			Item item = stack.getItem();
			int count = stack.getCount();
			totals.merge(item, count, Integer::sum);
			if (stack.isDamageableItem()) {
				durability.merge(item, new DurabilityState(count, stack.getDamageValue()),
					(a, b) -> new DurabilityState(a.count() + b.count(), a.totalDamage() + b.totalDamage()));
			}
			if (slot >= ARMOR_SLOT_START && slot <= ARMOR_SLOT_END) {
				armor.add(new ArmorSlot(item, stack.isDamageableItem() ? stack.getDamageValue() : 0));
			}
		}
		return new InventorySnapshot(totals, durability, armor);
	}

	/**
	 * 对比前后两份总量快照，并考虑主手物品切换。
	 *
	 * @param before 上一 tick 的总量快照；{@code null} 表示基线未建立（玩家切换后首个 tick），返回空
	 * @return 有变化时返回事件，否则 {@link Optional#empty()}。选择规则：
	 *         主手物品若在变化集合中优先返回（对齐 Quark 手部元素）；
	 *         否则取 |delta| 最大者，平局取最先遍历到的物品。
	 *         物品总数归零（消耗最后一件）时仍返回事件（newCount == 0），由调用方过滤。
	 */
	public static Optional<TickerEvent> diffTotals(Map<Item, Integer> before, Map<Item, Integer> after,
			Item mainHandBefore, Item mainHandAfter) {
		if (before == null) {
			return Optional.empty();
		}
		Map<Item, Integer> deltas = new LinkedHashMap<>();
		for (Map.Entry<Item, Integer> entry : after.entrySet()) {
			int delta = entry.getValue() - before.getOrDefault(entry.getKey(), 0);
			if (delta != 0) {
				deltas.put(entry.getKey(), delta);
			}
		}
		for (Map.Entry<Item, Integer> entry : before.entrySet()) {
			if (!after.containsKey(entry.getKey())) {
				deltas.put(entry.getKey(), -entry.getValue());
			}
		}
		if (!Objects.equals(mainHandBefore, mainHandAfter) && after.containsKey(mainHandAfter)) {
			deltas.putIfAbsent(mainHandAfter, 0);
		}
		if (deltas.isEmpty()) {
			return Optional.empty();
		}

		Item selected;
		if (deltas.containsKey(mainHandAfter)) {
			selected = mainHandAfter;
		} else {
			Item best = null;
			int bestAbs = -1;
			for (Map.Entry<Item, Integer> entry : deltas.entrySet()) {
				int abs = Math.abs(entry.getValue());
				if (abs > bestAbs) {
					bestAbs = abs;
					best = entry.getKey();
				}
			}
			selected = best;
		}
		return Optional.of(new TickerEvent(selected, after.getOrDefault(selected, 0), deltas.get(selected)));
	}

	/**
	 * 对比前后两份耐久快照与盔甲槽快照。
	 *
	 * @return 需要右侧耐久挂件显示的物品列表（先聚合耐久变化、后盔甲槽当前穿戴，去重）；
	 *         无变化返回空列表。规则：
	 *         <ul>
	 *           <li>可损坏物品聚合 {@code totalDamage} 变化且堆叠数不变（损坏/修复，工具/副手/护甲通用）→ 显示该物品；</li>
	 *           <li>盔甲槽 (物品, 耐久) 任一变化（穿脱/换装）→ 显示当前穿戴的护甲组。</li>
	 *         </ul>
	 *         数量变化（拾取/消耗新工具等）由 {@link #diffTotals} 负责，这里不重复触发；
	 *         before 为 {@code null}（基线未建立）返回空列表。
	 */
	public static List<Item> diffDurability(Map<Item, DurabilityState> before, Map<Item, DurabilityState> after,
			List<ArmorSlot> beforeArmor, List<ArmorSlot> afterArmor) {
		if (before == null || after == null || beforeArmor == null || afterArmor == null) {
			return List.of();
		}
		List<Item> changed = new ArrayList<>();
		// 1. 聚合耐久变化（堆叠数不变才算纯耐久事件）
		for (Map.Entry<Item, DurabilityState> entry : after.entrySet()) {
			DurabilityState prev = before.get(entry.getKey());
			DurabilityState curr = entry.getValue();
			if (prev != null && prev.count() == curr.count() && prev.totalDamage() != curr.totalDamage()) {
				changed.add(entry.getKey());
			}
		}
		// 2. 盔甲槽变化 → 显示当前穿戴组（穿脱/换装）
		if (!Objects.equals(beforeArmor, afterArmor)) {
			for (ArmorSlot slot : afterArmor) {
				if (slot.item() != Items.AIR && !changed.contains(slot.item())) {
					changed.add(slot.item());
				}
			}
		}
		return changed;
	}
}
