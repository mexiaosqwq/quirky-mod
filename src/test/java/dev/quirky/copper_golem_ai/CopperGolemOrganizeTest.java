package dev.quirky.copper_golem_ai;

import dev.quirky.TestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** organizeSlots 纯函数测试：同类合并 + 归类聚拢。 */
class CopperGolemOrganizeTest {

	@BeforeAll
	static void boot() {
		TestBootstrap.boot();
		TestBootstrap.bindItem(Items.OAK_LOG);
		TestBootstrap.bindItem(Items.STONE);
	}

	@Test
	void mergesSameItemAcrossSlots() {
		List<ItemStack> slots = new ArrayList<>(List.of(
			new ItemStack(Items.OAK_LOG, 32), new ItemStack(Items.OAK_LOG, 32),
			ItemStack.EMPTY, new ItemStack(Items.STONE, 5)));
		CopperGolemAiService.OrganizeResult r = CopperGolemAiService.organizeSlots(slots);
		assertEquals(1, r.merged());
		assertEquals(2, r.kinds());
		assertEquals(64, slots.get(0).getCount()); // 合并成满组
		assertEquals(Items.STONE, slots.get(1).getItem()); // 聚拢：同类相邻
		assertTrue(slots.get(2).isEmpty());
		assertTrue(slots.get(3).isEmpty());
	}

	@Test
	void mergesAcrossThreeSlots() {
		List<ItemStack> slots = new ArrayList<>(List.of(
			new ItemStack(Items.OAK_LOG, 20), new ItemStack(Items.OAK_LOG, 30), new ItemStack(Items.OAK_LOG, 30)));
		CopperGolemAiService.OrganizeResult r = CopperGolemAiService.organizeSlots(slots);
		assertEquals(2, r.merged());
		assertEquals(64, slots.get(0).getCount());
		assertEquals(16, slots.get(1).getCount());
		assertTrue(slots.get(2).isEmpty());
	}

	@Test
	void groupsByItemKind() {
		List<ItemStack> slots = new ArrayList<>(List.of(
			new ItemStack(Items.STONE, 3), new ItemStack(Items.OAK_LOG, 3)));
		CopperGolemAiService.organizeSlots(slots);
		// 稳定排序：oak_log 字典序在 stone 前 → 同类相邻
		assertEquals(Items.OAK_LOG, slots.get(0).getItem());
		assertEquals(Items.STONE, slots.get(1).getItem());
	}

	@Test
	void noMergeWhenAlreadyTidy() {
		List<ItemStack> slots = new ArrayList<>(List.of(
			new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.STONE, 3)));
		CopperGolemAiService.OrganizeResult r = CopperGolemAiService.organizeSlots(slots);
		assertEquals(0, r.merged());
		assertEquals(2, r.kinds());
	}

	@Test
	void emptyContainer() {
		List<ItemStack> slots = new ArrayList<>(List.of(ItemStack.EMPTY, ItemStack.EMPTY));
		CopperGolemAiService.OrganizeResult r = CopperGolemAiService.organizeSlots(slots);
		assertEquals(0, r.merged());
		assertEquals(0, r.kinds());
	}
}
