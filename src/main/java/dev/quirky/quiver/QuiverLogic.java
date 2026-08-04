package dev.quirky.quiver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * 箭袋核心纯逻辑：吸入/取出/容量截断/白名单。
 *
 * <p>与 Minecraft 世界完全解耦，全部可单测。容量按"组"计（每组为该物品自身的
 * maxStackSize，箭类 64）；同名同组件（如不同药箭）各自成组，互不合并。</p>
 */
public final class QuiverLogic {

	private QuiverLogic() {
	}

	/** 白名单：原版箭 tag（箭/光灵箭/药箭/火把箭）与烟花火箭（弩弹药）。 */
	public static boolean isAmmo(ItemStack stack) {
		return stack.is(ItemTags.ARROWS) || stack.is(Items.FIREWORK_ROCKET);
	}

	/** 吸入结果：新内容 + 各来源槽位被消耗的数量（槽位索引 → 数量）。 */
	public record AbsorbResult(ItemContainerContents contents, Map<Integer, Integer> consumedBySlot) {
		public boolean nothingConsumed() {
			return consumedBySlot.isEmpty();
		}
	}

	public static AbsorbResult absorb(ItemContainerContents current, List<ItemStack> inventory, int capacity) {
		return absorb(current, inventory, capacity, QuiverLogic::isAmmo);
	}

	/**
	 * 把 inventory 中所有弹药尽量吸入箭袋。
	 *
	 * @param capacity 容量（组数上限）
	 * @param isAmmo   白名单谓词（生产用 {@link #isAmmo}；测试可注入不依赖 tag 的谓词）
	 */
	public static AbsorbResult absorb(ItemContainerContents current, List<ItemStack> inventory, int capacity, Predicate<ItemStack> isAmmo) {
		if (capacity <= 0) {
			return new AbsorbResult(ItemContainerContents.EMPTY, Map.of());
		}
		List<ItemStack> slots = new ArrayList<>(current.allItemsCopyStream().toList());
		Map<Integer, Integer> consumed = new LinkedHashMap<>();

		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack stack = inventory.get(slot);
			if (stack.isEmpty() || !isAmmo.test(stack)) {
				continue;
			}
			int remaining = stack.getCount();
			int groupSize = Math.max(1, stack.getMaxStackSize());
			// 先并入同种且未满的已有组（保持既有顺序稳定）
			for (int i = 0; i < slots.size() && remaining > 0; i++) {
				ItemStack group = slots.get(i);
				if (!group.isEmpty() && ItemStack.isSameItemSameComponents(group, stack) && group.getCount() < groupSize) {
					int take = Math.min(groupSize - group.getCount(), remaining);
					group.grow(take);
					remaining -= take;
					consumed.merge(slot, take, Integer::sum);
				}
			}
			// 再占新组，直到容量上限
			while (remaining > 0) {
				int empty = firstEmptySlot(slots, capacity);
				if (empty == -1) {
					break; // 已满
				}
				int take = Math.min(groupSize, remaining);
				if (empty == slots.size()) {
					slots.add(stack.copyWithCount(take));
				} else {
					slots.set(empty, stack.copyWithCount(take));
				}
				remaining -= take;
				consumed.merge(slot, take, Integer::sum);
			}
		}
		return new AbsorbResult(ItemContainerContents.fromItems(slots), consumed);
	}

	/** 取出结果：取出的一组 + 剩余内容。空袋时 extracted 为 EMPTY。 */
	public record ExtractResult(ItemStack extracted, ItemContainerContents contents) {
	}

	public static ExtractResult extractOne(ItemContainerContents current) {
		if (current == null || current == ItemContainerContents.EMPTY) {
			return new ExtractResult(ItemStack.EMPTY, ItemContainerContents.EMPTY);
		}
		List<ItemStack> slots = new ArrayList<>(current.allItemsCopyStream().toList());
		for (int i = 0; i < slots.size(); i++) {
			if (!slots.get(i).isEmpty()) {
				ItemStack extracted = slots.get(i);
				slots.set(i, ItemStack.EMPTY);
				return new ExtractResult(extracted, ItemContainerContents.fromItems(slots));
			}
		}
		return new ExtractResult(ItemStack.EMPTY, current);
	}

	/** 已占用组数。 */
	public static int usedSlots(ItemContainerContents contents) {
		if (contents == null) {
			return 0;
		}
		int used = 0;
		for (ItemStack stack : contents.allItemsCopyStream().toList()) {
			if (!stack.isEmpty()) {
				used++;
			}
		}
		return used;
	}

	/** 返回第一个空槽位索引（只查容量范围内）；无空位返回 -1。 */
	private static int firstEmptySlot(List<ItemStack> slots, int capacity) {
		for (int i = 0; i < slots.size() && i < capacity; i++) {
			if (slots.get(i).isEmpty()) {
				return i;
			}
		}
		return slots.size() < capacity ? slots.size() : -1;
	}

	// ===== 光标点击装取（对齐播种袋）+ 自动抽箭供给 =====

	/** 装入结果：新内容 + 实际装入数量（可能小于请求，受组上限/容量截断）。 */
	public record InsertResult(ItemContainerContents contents, int inserted) {
	}

	/**
	 * 把单栈弹药装入箭袋（光标左键点箭袋 / 箭袋点弹药堆）。
	 * 先并入同种未满组，再占新空组，直到容量上限或装完。非弹药拒绝。
	 *
	 * @param toInsert 待装栈（不会被修改，调用方根据 inserted 退回余量到光标）
	 * @param capacity 组数上限
	 * @param isAmmo   白名单谓词
	 */
	public static InsertResult insert(ItemContainerContents current, ItemStack toInsert, int capacity, Predicate<ItemStack> isAmmo) {
		if (toInsert.isEmpty() || !isAmmo.test(toInsert) || capacity <= 0) {
			return new InsertResult(current == null ? ItemContainerContents.EMPTY : current, 0);
		}
		List<ItemStack> slots = new ArrayList<>(current.allItemsCopyStream().toList());
		int groupSize = Math.max(1, toInsert.getMaxStackSize());
		int remaining = toInsert.getCount();
		boolean hasSameKind = false;
		// 先并入同种未满组
		for (int i = 0; i < slots.size() && remaining > 0; i++) {
			ItemStack group = slots.get(i);
			if (!group.isEmpty() && ItemStack.isSameItemSameComponents(group, toInsert)) {
				hasSameKind = true;
				if (group.getCount() < groupSize) {
					int take = Math.min(groupSize - group.getCount(), remaining);
					group.grow(take);
					remaining -= take;
				}
			}
		}
		// 同种已存在但已满 → 余量退回光标，不开新组（避免一种箭占两个半组）
		// 仅当无同种组时才占新空组
		if (!hasSameKind) {
			while (remaining > 0) {
				int empty = firstEmptySlot(slots, capacity);
				if (empty == -1) {
					break;
				}
				int take = Math.min(groupSize, remaining);
				if (empty == slots.size()) {
					slots.add(toInsert.copyWithCount(take));
				} else {
					slots.set(empty, toInsert.copyWithCount(take));
				}
				remaining -= take;
			}
		}
		int inserted = toInsert.getCount() - remaining;
		return new InsertResult(ItemContainerContents.fromItems(slots), inserted);
	}

	/** 弹药匹配结果：是否命中 + 匹配组的副本 + 组索引（供 decrementGroup 定位）。 */
	public record AmmoMatch(boolean found, ItemStack stack, int groupIndex) {
		public static final AmmoMatch EMPTY = new AmmoMatch(false, ItemStack.EMPTY, -1);
	}

	/**
	 * 按武器弹药谓词找第一个匹配的非空组（自动抽箭用）。
	 * 返回该组的副本（count=该组全量）+ 组索引；未命中返回 AmmoMatch.EMPTY。
	 */
	public static AmmoMatch findAmmo(ItemContainerContents contents, Predicate<ItemStack> isAmmo) {
		if (contents == null) {
			return AmmoMatch.EMPTY;
		}
		List<ItemStack> slots = contents.allItemsCopyStream().toList();
		for (int i = 0; i < slots.size(); i++) {
			ItemStack group = slots.get(i);
			if (!group.isEmpty() && isAmmo.test(group)) {
				return new AmmoMatch(true, group.copy(), i);
			}
		}
		return AmmoMatch.EMPTY;
	}

	/**
	 * 扣减指定组（自动抽箭 useAmmo 用）：减 amount，clamp 在该组可用量。
	 * 减到 0 的组置空（保留槽位结构，下次装入可复用）。
	 */
	public static ItemContainerContents decrementGroup(ItemContainerContents contents, int groupIndex, int amount) {
		if (contents == null || amount <= 0 || groupIndex < 0) {
			return contents == null ? ItemContainerContents.EMPTY : contents;
		}
		List<ItemStack> slots = new ArrayList<>(contents.allItemsCopyStream().toList());
		if (groupIndex >= slots.size()) {
			return contents;
		}
		ItemStack group = slots.get(groupIndex);
		if (group.isEmpty()) {
			return contents;
		}
		int take = Math.min(amount, group.getCount());
		group.shrink(take);
		if (group.isEmpty()) {
			slots.set(groupIndex, ItemStack.EMPTY);
		}
		return ItemContainerContents.fromItems(slots);
	}
}
