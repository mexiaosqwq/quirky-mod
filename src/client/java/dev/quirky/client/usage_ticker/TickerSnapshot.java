package dev.quirky.client.usage_ticker;

import java.util.ArrayList;
import java.util.HashMap;
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
 *   <li><b>数量变化</b>（拾取/消耗/放置）→ 左侧物品挂件（{@link #diffTotals}）；
 *       装备槽（副手/BODY/SADDLE）摆放变化在数量无变化时兑底触发左侧（{@link #diffEquipment}）</li>
 *   <li><b>盔甲槽内容变化</b>（穿脱/换装/耐久升降，逐槽）→ 右侧对应固定位（{@link #diffArmorSlots}）</li>
 *   <li><b>工具/副手耐久变化</b>（损坏/修复，降或升）→ 右侧浮动列表（{@link #diffToolDurability}）</li>
 * </ul>
 *
 * 数量与耐久均按物品聚合（不绑定槽位），同物品在槽位间移动（整理/交换）天然免疫；
 * 主手切换无数量变化不触发（2026-08-03 用户确认，见 {@link #diffTotals}）。
 */
public final class TickerSnapshot {
	/** 单次遍历产出的快照。 */
	public record InventorySnapshot(
		/** (物品 → 背包总数)。 */
		Map<Item, Integer> totals,
		/** (可损坏物品 → 堆叠数 + 总耐久损耗)，按物品聚合。 */
		Map<Item, DurabilityState> durability,
		/** 4 个盔甲槽（36..39）的 (物品, 耐久)，槽位序。 */
		List<ArmorSlot> armor,
		/** 其余装备槽（副手 40 / BODY 41 / SADDLE 42）的物品，槽位序，空槽为 AIR。 */
		List<Item> equipment
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
		List<Item> equipment = new ArrayList<>(3);
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				if (slot >= ARMOR_SLOT_START && slot <= ARMOR_SLOT_END) {
					armor.add(new ArmorSlot(Items.AIR, 0));
				} else if (slot >= Inventory.SLOT_OFFHAND && slot <= Inventory.SLOT_SADDLE) {
					equipment.add(Items.AIR);
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
			} else if (slot >= Inventory.SLOT_OFFHAND && slot <= Inventory.SLOT_SADDLE) {
				equipment.add(item);
			}
		}
		return new InventorySnapshot(totals, durability, armor, equipment);
	}

	/**
	 * 对比前后两份总量快照。
	 *
	 * @param before 上一 tick 的总量快照；{@code null} 表示基线未建立（玩家切换后首个 tick），返回空
	 * @return 有变化时返回事件，否则 {@link Optional#empty()}。选择规则：取 |delta| 最大者，
	 *         平局取最先遍历到的物品。
	 *         物品总数归零（消耗最后一件）时仍返回事件（newCount == 0），由调用方过滤。
	 *         2026-08-03：不再跟踪主手切换（纯切换无数量变化，不触发挂件）。
	 */
	public static Optional<TickerEvent> diffTotals(Map<Item, Integer> before, Map<Item, Integer> after) {
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
		if (deltas.isEmpty()) {
			return Optional.empty();
		}

		Item selected = null;
		int bestAbs = -1;
		for (Map.Entry<Item, Integer> entry : deltas.entrySet()) {
			int abs = Math.abs(entry.getValue());
			if (abs > bestAbs) {
				bestAbs = abs;
				selected = entry.getKey();
			}
		}
		return Optional.of(new TickerEvent(selected, after.getOrDefault(selected, 0), deltas.get(selected)));
	}

	/**
	 * 对比前后两份装备槽（副手/BODY/SADDLE）快照。
	 *
	 * @return 第一个发生变化的槽位中放入的物品（非 AIR）；清空槽位无物品可显示，返回空。
	 *         before 为 {@code null}（基线未建立）返回空。
	 *         数量/主手事件优先于装备槽事件，由调用方选择（见 UsageTickerHud.tick）。
	 */
	public static Optional<Item> diffEquipment(List<Item> before, List<Item> after) {
		if (before == null || after == null) {
			return Optional.empty();
		}
		int size = Math.min(before.size(), after.size());
		for (int i = 0; i < size; i++) {
			if (before.get(i) != after.get(i)) {
				Item item = after.get(i);
				if (item != Items.AIR) {
					return Optional.of(item);
				}
			}
		}
		return Optional.empty();
	}

	/**
	 * 逐槽对比前后两份盔甲槽快照（槽位序 36..39）。
	 *
	 * @return boolean[4]：该槽 (物品, 耐久) 有变化且变化后**槽位非空**（穿脱/换装/耐久升降）为 true——
	 *         槽位变空（脱装备）无物品可显示，不激活（对齐 Quark：空槽不显示）。
	 *         before 为 {@code null}（基线未建立）全 false。
	 */
	public static boolean[] diffArmorSlots(List<ArmorSlot> before, List<ArmorSlot> after) {
		boolean[] changed = new boolean[4];
		if (before == null || after == null || before.size() != 4 || after.size() != 4) {
			return changed;
		}
		for (int i = 0; i < 4; i++) {
			ArmorSlot prev = before.get(i);
			ArmorSlot curr = after.get(i);
			if (curr.item() != Items.AIR && !Objects.equals(prev, curr)) {
				changed[i] = true;
			}
		}
		return changed;
	}

	/**
	 * 对比前后两份耐久快照，收集**工具/副手**（非盔甲槽）的耐久变化。
	 *
	 * @return 需要右侧浮动位显示的物品列表（聚合遍历序，去重）；无变化返回空列表。规则：
	 *         <ul>
	 *           <li>可损坏物品聚合 {@code totalDamage} 变化且堆叠数不变（损坏/修复）→ 收集；</li>
	 *           <li>**仅当该物品的全部堆叠都在盔甲槽（全部穿戴中）**才排除（护甲走 {@link #diffArmorSlots}
	 *               固定位，不重复显示）；穿 + 备同物品时背包件修复/损坏仍走浮动列表。</li>
	 *         </ul>
	 *         数量变化（拾取/消耗新工具等）由 {@link #diffTotals} 负责，这里不重复触发；
	 *         before 为 {@code null}（基线未建立）返回空列表。
	 */
	public static List<Item> diffToolDurability(Map<Item, DurabilityState> before, Map<Item, DurabilityState> after,
			List<ArmorSlot> afterArmor) {
		if (before == null || after == null || afterArmor == null) {
			return List.of();
		}
		Map<Item, Integer> wornCounts = new HashMap<>();
		for (ArmorSlot slot : afterArmor) {
			if (slot.item() != Items.AIR) {
				wornCounts.merge(slot.item(), 1, Integer::sum);
			}
		}
		List<Item> changed = new ArrayList<>();
		for (Map.Entry<Item, DurabilityState> entry : after.entrySet()) {
			DurabilityState prev = before.get(entry.getKey());
			DurabilityState curr = entry.getValue();
			if (prev != null && prev.count() == curr.count() && prev.totalDamage() != curr.totalDamage()
					&& wornCounts.getOrDefault(entry.getKey(), 0) < curr.count()) {
				changed.add(entry.getKey());
			}
		}
		return changed;
	}
}
