package dev.quirky.quiver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import dev.quirky.TestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 箭袋纯逻辑测试。单测环境不加载数据包 tag（Holder.tag 未绑定会抛异常），
 * 故白名单谓词注入不依赖 tag 的等价实现（is(Items.X) 直接比 Item 实例）。
 */
class QuiverLogicTest {

	/** 等价于生产白名单，但不依赖 tag 解析：原版箭（箭/光灵箭/药箭）+ 烟花。 */
	private static final Predicate<ItemStack> AMMO = stack ->
		stack.is(Items.ARROW) || stack.is(Items.SPECTRAL_ARROW) || stack.is(Items.TIPPED_ARROW) || stack.is(Items.FIREWORK_ROCKET);

	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
		TestBootstrap.bindItem(Items.ARROW);
		TestBootstrap.bindItem(Items.SPECTRAL_ARROW);
		TestBootstrap.bindItem(Items.TIPPED_ARROW);
		TestBootstrap.bindItem(Items.FIREWORK_ROCKET);
		TestBootstrap.bindItem(Items.STONE);
	}

	@Test
	void arrowMaxStackSizeIsReal() {
		assertEquals(64, new ItemStack(Items.ARROW).getMaxStackSize());
	}

	@Test
	void absorbPacksMixedAmmoIntoGroupsInOrder() {
		List<ItemStack> inventory = List.of(
			new ItemStack(Items.ARROW, 64),
			new ItemStack(Items.SPECTRAL_ARROW, 64)
		);
		QuiverLogic.AbsorbResult result = QuiverLogic.absorb(ItemContainerContents.EMPTY, inventory, 4, AMMO);
		assertEquals(2, QuiverLogic.usedSlots(result.contents()));
		List<ItemStack> stored = result.contents().allItemsCopyStream().toList();
		assertTrue(stored.get(0).is(Items.ARROW));
		assertEquals(64, stored.get(0).getCount());
		assertTrue(stored.get(1).is(Items.SPECTRAL_ARROW));
		assertEquals(64, stored.get(1).getCount());
	}

	@Test
	void absorbTruncatesAtCapacity() {
		List<ItemStack> inventory = List.of(
			new ItemStack(Items.ARROW, 64),
			new ItemStack(Items.SPECTRAL_ARROW, 64),
			new ItemStack(Items.TIPPED_ARROW, 64)
		);
		QuiverLogic.AbsorbResult result = QuiverLogic.absorb(ItemContainerContents.EMPTY, inventory, 2, AMMO);
		assertEquals(2, QuiverLogic.usedSlots(result.contents()));
		assertTrue(result.consumedBySlot().containsKey(0));
		assertTrue(result.consumedBySlot().containsKey(1));
		assertFalse(result.consumedBySlot().containsKey(2)); // 第三组超容量未吸入
		assertEquals(64, result.consumedBySlot().get(0));
		assertEquals(64, result.consumedBySlot().get(1));
	}

	@Test
	void absorbMergesIntoExistingGroup() {
		// 已有组未满（30/64）时并入；组上限 = maxStackSize(64)
		ItemContainerContents current = ItemContainerContents.fromItems(List.of(new ItemStack(Items.ARROW, 30)));
		QuiverLogic.AbsorbResult result = QuiverLogic.absorb(current, List.of(new ItemStack(Items.ARROW, 30)), 4, AMMO);
		List<ItemStack> stored = nonEmpty(result.contents());
		assertEquals(1, stored.size());
		assertEquals(60, stored.get(0).getCount());
		assertEquals(30, result.consumedBySlot().get(0));
	}

	@Test
	void absorbCapsGroupAtMaxStackSize() {
		// 已有组已满（64/64）：新箭开新组而非超上限合并
		ItemContainerContents current = ItemContainerContents.fromItems(List.of(new ItemStack(Items.ARROW, 64)));
		QuiverLogic.AbsorbResult result = QuiverLogic.absorb(current, List.of(new ItemStack(Items.ARROW, 32)), 4, AMMO);
		List<ItemStack> stored = nonEmpty(result.contents());
		assertEquals(2, stored.size());
		assertEquals(64, stored.get(0).getCount());
		assertEquals(32, stored.get(1).getCount());
		assertEquals(32, result.consumedBySlot().get(0));
	}

	@Test
	void absorbStoresPartialGroupWhenSourceSmallerThanGroup() {
		QuiverLogic.AbsorbResult result = QuiverLogic.absorb(
			ItemContainerContents.EMPTY, List.of(new ItemStack(Items.ARROW, 10)), 4, AMMO
		);
		List<ItemStack> stored = result.contents().allItemsCopyStream().toList();
		assertEquals(1, stored.size());
		assertEquals(10, stored.get(0).getCount());
		assertEquals(10, result.consumedBySlot().get(0));
	}

	@Test
	void absorbSeparatesDifferentComponents() {
		// 同名同组件才合并：两把不同药箭（不同 potion 组件）各自成组
		ItemStack tippedA = new ItemStack(Items.TIPPED_ARROW, 64);
		ItemStack tippedB = new ItemStack(Items.TIPPED_ARROW, 64);
		QuiverLogic.AbsorbResult result = QuiverLogic.absorb(ItemContainerContents.EMPTY, List.of(tippedA, tippedB), 4, AMMO);
		assertEquals(2, QuiverLogic.usedSlots(result.contents()));
	}

	@Test
	void absorbRejectsNonAmmo() {
		QuiverLogic.AbsorbResult result = QuiverLogic.absorb(
			ItemContainerContents.EMPTY, List.of(new ItemStack(Items.STONE, 32)), 4, AMMO
		);
		assertEquals(0, QuiverLogic.usedSlots(result.contents()));
		assertTrue(result.nothingConsumed());
	}

	@Test
	void absorbAcceptsFireworkRockets() {
		QuiverLogic.AbsorbResult result = QuiverLogic.absorb(
			ItemContainerContents.EMPTY, List.of(new ItemStack(Items.FIREWORK_ROCKET, 5)), 4, AMMO
		);
		assertEquals(1, QuiverLogic.usedSlots(result.contents()));
		List<ItemStack> stored = result.contents().allItemsCopyStream().toList();
		assertTrue(stored.get(0).is(Items.FIREWORK_ROCKET));
		assertEquals(5, stored.get(0).getCount());
	}

	@Test
	void absorbWhenQuiverFullConsumesNothing() {
		ItemContainerContents full = ItemContainerContents.fromItems(List.of(
			new ItemStack(Items.ARROW, 64),
			new ItemStack(Items.SPECTRAL_ARROW, 64)
		));
		QuiverLogic.AbsorbResult result = QuiverLogic.absorb(full, List.of(new ItemStack(Items.ARROW, 64)), 2, AMMO);
		assertEquals(2, QuiverLogic.usedSlots(result.contents()));
		assertTrue(result.nothingConsumed());
	}

	@Test
	void extractOneTakesFirstGroupKeepsRest() {
		ItemContainerContents contents = ItemContainerContents.fromItems(List.of(
			new ItemStack(Items.ARROW, 64),
			new ItemStack(Items.SPECTRAL_ARROW, 64)
		));
		QuiverLogic.ExtractResult result = QuiverLogic.extractOne(contents);
		assertTrue(result.extracted().is(Items.ARROW));
		assertEquals(64, result.extracted().getCount());
		List<ItemStack> remaining = nonEmpty(result.contents());
		assertEquals(1, remaining.size());
		assertTrue(remaining.get(0).is(Items.SPECTRAL_ARROW));
	}

	@Test
	void extractOneEmptyReturnsEmpty() {
		QuiverLogic.ExtractResult result = QuiverLogic.extractOne(ItemContainerContents.EMPTY);
		assertTrue(result.extracted().isEmpty());
		assertEquals(ItemContainerContents.EMPTY, result.contents());
	}

	@Test
	void extractOneEmptyForEmptySlotList() {
		QuiverLogic.ExtractResult result = QuiverLogic.extractOne(ItemContainerContents.fromItems(List.of(ItemStack.EMPTY)));
		assertTrue(result.extracted().isEmpty());
	}

	@Test
	void usedSlotsCountsGroups() {
		ItemContainerContents contents = ItemContainerContents.fromItems(List.of(
			new ItemStack(Items.ARROW, 64),
			ItemStack.EMPTY,
			new ItemStack(Items.SPECTRAL_ARROW, 64)
		));
		assertEquals(2, QuiverLogic.usedSlots(contents));
		assertEquals(0, QuiverLogic.usedSlots(ItemContainerContents.EMPTY));
	}

	@Test
	void absorbFillsFirstEmptySlotAfterExtract() {
		// 先装 2 组，取出第 1 组，再吸新箭 → 新箭应占用第 1 个空槽位
		ItemContainerContents initial = ItemContainerContents.fromItems(List.of(
			new ItemStack(Items.ARROW, 64),
			new ItemStack(Items.SPECTRAL_ARROW, 64)
		));
		QuiverLogic.ExtractResult extract = QuiverLogic.extractOne(initial);
		assertTrue(extract.extracted().is(Items.ARROW));
		QuiverLogic.AbsorbResult absorb = QuiverLogic.absorb(
			extract.contents(), List.of(new ItemStack(Items.ARROW, 16)), 4, AMMO
		);
		List<ItemStack> stored = nonEmpty(absorb.contents());
		assertEquals(2, stored.size());
		assertTrue(stored.get(0).is(Items.ARROW));
		assertEquals(16, stored.get(0).getCount());
		assertTrue(stored.get(1).is(Items.SPECTRAL_ARROW));
	}

	/** 过滤掉空槽位，仅返回非空组（保持顺序）。 */
	private static List<ItemStack> nonEmpty(ItemContainerContents contents) {
		return contents.allItemsCopyStream().filter(stack -> !stack.isEmpty()).toList();
	}
}
