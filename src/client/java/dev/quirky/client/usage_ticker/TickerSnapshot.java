package dev.quirky.client.usage_ticker;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 背包按物品统计快照与差异检测（纯逻辑，可单测）。
 *
 * 每 tick 捕获 41 槽（0..35 主背包 + 36..39 盔甲 + 40 副手）并**按物品累加总数**，
 * 对比相邻两 tick 的总数变化：物品总数增减视为拾取/消耗事件（对齐 Quark 按槽位
 * 元素 + 背包总和的检测方式，对放置/拾取/网络同步时序都稳健——不依赖"恰好单槽
 * 变化"，服务器批量同步、跨槽汇总都不会漏）；同物品在槽位间移动（整理/交换）时
 * 总数不变，不触发。主手物品切换（快捷栏滚动）同样触发，显示新主手物品。
 */
public final class TickerSnapshot {
	/** 总数变化事件：item 为变化的物品，newCount 为变化后的背包总数，delta = 变化量（拾取正/消耗负）。 */
	public record TickerEvent(Item item, int newCount, int delta) {}

	private TickerSnapshot() {
	}

	/**
	 * 按物品累加全背包槽位数量，得到 (物品 → 背包总数) 映射；空槽跳过，遍历顺序为槽位序（确定性）。
	 * 槽位数取 {@link Inventory#getContainerSize()}（26.2 为 43：36 背包 + 4 盔甲 + 副手 + BODY + SADDLE），
	 * 与 {@code UsageTickerHud.totalCount} 的统计口径一致。
	 * 注意粒度：这里按 {@link Item} 分组（不比较数据组件），检测用粗粒度足够；
	 * 显示时的实时总数按 {@code ItemStack.isSameItemSameComponents} 更精确求和（见 totalCount）。
	 */
	public static Map<Item, Integer> captureTotals(Player player) {
		Inventory inventory = player.getInventory();
		Map<Item, Integer> totals = new LinkedHashMap<>();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty()) {
				totals.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
		return totals;
	}

	/**
	 * 对比前后两份总数快照，并考虑主手物品切换。
	 *
	 * @param before 上一 tick 的总数快照；{@code null} 表示基线未建立（玩家切换后首个 tick），返回空
	 * @return 有变化时返回事件，否则 {@link Optional#empty()}。选择规则：
	 *         主手物品若在变化集合中优先返回（对齐 Quark 手部元素）；
	 *         否则取 |delta| 最大者，平局取最先遍历到的物品。
	 *         物品总数归零（消耗最后一件）时仍返回事件（newCount == 0），由调用方过滤。
	 */
	public static Optional<TickerEvent> diff(Map<Item, Integer> before, Map<Item, Integer> after,
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
}
