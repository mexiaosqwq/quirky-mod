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
		TestBootstrap.bindItem(Items.APPLE);
		TestBootstrap.bindItem(Items.IRON_PICKAXE);
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
		CopperGolemAiService.OrganizeResult r = CopperGolemAiService.organizeSlots(slots);
		// 同类（方块组）内按 id 字典序：oak_log 在 stone 前 → 同类相邻
		assertEquals(Items.OAK_LOG, slots.get(0).getItem());
		assertEquals(Items.STONE, slots.get(1).getItem());
		assertTrue(r.reordered()); // 顺序从 stone→oak_log 变成 oak_log→stone
	}

	@Test
	void groupsByCategoryThenId() {
		// 方块组 0（原木/石头）→ 食物组 1（苹果）→ 工具组 2（铁镐）：跨类别聚拢，组内字典序
		List<ItemStack> slots = new ArrayList<>(List.of(
			new ItemStack(Items.APPLE, 1),
			new ItemStack(Items.IRON_PICKAXE, 1),
			new ItemStack(Items.STONE, 3),
			new ItemStack(Items.OAK_LOG, 3)));
		CopperGolemAiService.organizeSlots(slots);
		assertEquals(Items.OAK_LOG, slots.get(0).getItem());
		assertEquals(Items.STONE, slots.get(1).getItem());
		assertEquals(Items.APPLE, slots.get(2).getItem());
		assertEquals(Items.IRON_PICKAXE, slots.get(3).getItem());
	}

	@Test
	void notReorderedWhenAlreadyInOrder() {
		List<ItemStack> slots = new ArrayList<>(List.of(
			new ItemStack(Items.OAK_LOG, 3), new ItemStack(Items.STONE, 3)));
		CopperGolemAiService.OrganizeResult r = CopperGolemAiService.organizeSlots(slots);
		assertEquals(0, r.merged());
		assertFalse(r.reordered()); // 已在正确顺序 → 不误报"重新归位"
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
