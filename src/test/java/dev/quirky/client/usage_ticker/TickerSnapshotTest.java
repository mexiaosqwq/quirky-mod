package dev.quirky.client.usage_ticker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.quirky.TestBootstrap;
import dev.quirky.client.usage_ticker.TickerSnapshot.ArmorSlot;
import dev.quirky.client.usage_ticker.TickerSnapshot.DurabilityState;
import dev.quirky.client.usage_ticker.TickerSnapshot.InventorySnapshot;
import dev.quirky.client.usage_ticker.TickerSnapshot.TickerEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TickerSnapshotTest {

	@BeforeAll
	static void boot() {
		TestBootstrap.boot();
		TestBootstrap.bindItem(Items.STONE);
		TestBootstrap.bindItem(Items.COBBLESTONE);
		TestBootstrap.bindItem(Items.IRON_SWORD);
		TestBootstrap.bindItem(Items.DIAMOND_HELMET);
		TestBootstrap.bindItem(Items.DIAMOND_CHESTPLATE);
		TestBootstrap.bindItem(Items.DIAMOND_PICKAXE);
	}

	private static Map<Item, Integer> totals(Item item, int count) {
		return Map.of(item, count);
	}

	// ---------- capture ----------

	@Test
	void capture_readsTotalsDurabilityAndArmorInOnePass() {
		Inventory inventory = mock(Inventory.class);
		when(inventory.getContainerSize()).thenReturn(43);
		when(inventory.getItem(0)).thenReturn(new ItemStack(Items.STONE, 64));
		when(inventory.getItem(1)).thenReturn(new ItemStack(Items.STONE, 3));
		when(inventory.getItem(2)).thenReturn(new ItemStack(Items.COBBLESTONE, 10));
		when(inventory.getItem(36)).thenReturn(helmet(5));
		when(inventory.getItem(37)).thenReturn(ItemStack.EMPTY);
		when(inventory.getItem(38)).thenReturn(ItemStack.EMPTY);
		when(inventory.getItem(39)).thenReturn(ItemStack.EMPTY);
		for (int slot = 3; slot < 43; slot++) {
			if (slot >= 36 && slot <= 39) {
				continue;
			}
			when(inventory.getItem(slot)).thenReturn(ItemStack.EMPTY);
		}
		Player player = mock(Player.class);
		when(player.getInventory()).thenReturn(inventory);

		InventorySnapshot snapshot = TickerSnapshot.capture(player);

		assertEquals(3, snapshot.totals().size());
		assertEquals(67, snapshot.totals().get(Items.STONE));
		assertEquals(10, snapshot.totals().get(Items.COBBLESTONE));
		assertEquals(1, snapshot.totals().get(Items.DIAMOND_HELMET));
		assertEquals(Map.of(Items.DIAMOND_HELMET, new DurabilityState(1, 5)), snapshot.durability());
		assertEquals(4, snapshot.armor().size());
		assertEquals(new ArmorSlot(Items.DIAMOND_HELMET, 5), snapshot.armor().get(0));
		assertEquals(new ArmorSlot(Items.AIR, 0), snapshot.armor().get(1));
	}

	// ---------- diffTotals（数量挂件，左） ----------

	@Test
	void diffTotals_singleItemTotalDecrease_placementScenario() {
		Optional<TickerEvent> event = TickerSnapshot.diffTotals(
			totals(Items.STONE, 64), totals(Items.STONE, 63),
			Items.STONE, Items.STONE
		);

		assertTrue(event.isPresent());
		assertEquals(Items.STONE, event.get().item());
		assertEquals(63, event.get().newCount());
		assertEquals(-1, event.get().delta());
	}

	@Test
	void diffTotals_singleItemTotalIncrease() {
		Optional<TickerEvent> event = TickerSnapshot.diffTotals(
			totals(Items.COBBLESTONE, 10), totals(Items.COBBLESTONE, 13),
			Items.IRON_SWORD, Items.IRON_SWORD
		);

		assertTrue(event.isPresent());
		assertEquals(Items.COBBLESTONE, event.get().item());
		assertEquals(13, event.get().newCount());
		assertEquals(3, event.get().delta());
	}

	@Test
	void diffTotals_redistributionAcrossSlots_noEvent() {
		// 同物品在槽位间移动/整理：总数不变 → 不触发
		Map<Item, Integer> before = Map.of(Items.STONE, 67, Items.COBBLESTONE, 3);
		Map<Item, Integer> after = Map.of(Items.STONE, 67, Items.COBBLESTONE, 3);

		assertTrue(TickerSnapshot.diffTotals(before, after, Items.STONE, Items.STONE).isEmpty());
	}

	@Test
	void diffTotals_noChange_noEvent() {
		assertTrue(TickerSnapshot.diffTotals(totals(Items.STONE, 4), totals(Items.STONE, 4), Items.STONE, Items.STONE).isEmpty());
	}

	@Test
	void diffTotals_nullBaseline_noEvent() {
		assertTrue(TickerSnapshot.diffTotals(null, totals(Items.STONE, 64), Items.STONE, Items.STONE).isEmpty());
	}

	@Test
	void diffTotals_fromEmptyInventory_fires() {
		Optional<TickerEvent> event = TickerSnapshot.diffTotals(
			Map.of(), totals(Items.STONE, 64), Items.STONE, Items.STONE
		);

		assertTrue(event.isPresent());
		assertEquals(Items.STONE, event.get().item());
		assertEquals(64, event.get().newCount());
		assertEquals(64, event.get().delta());
	}

	@Test
	void diffTotals_mainHandSwitch_firesWithNewHandItem() {
		// 主手槽与另一槽交换物品：总数不变但主手物品变化 → 触发
		Map<Item, Integer> before = Map.of(Items.STONE, 64, Items.COBBLESTONE, 1);
		Map<Item, Integer> after = Map.of(Items.STONE, 64, Items.COBBLESTONE, 1);
		Optional<TickerEvent> event = TickerSnapshot.diffTotals(before, after, Items.STONE, Items.COBBLESTONE);

		assertTrue(event.isPresent());
		assertEquals(Items.COBBLESTONE, event.get().item());
		assertEquals(1, event.get().newCount());
		assertEquals(0, event.get().delta());
	}

	@Test
	void diffTotals_mainHandSwitchToEmpty_noEvent() {
		assertTrue(TickerSnapshot.diffTotals(
			totals(Items.STONE, 64), totals(Items.STONE, 64),
			Items.STONE, Items.AIR
		).isEmpty());
	}

	@Test
	void diffTotals_prefersMainHandItem_whenItChanged() {
		Map<Item, Integer> before = Map.of(Items.STONE, 64, Items.COBBLESTONE, 10);
		Map<Item, Integer> after = Map.of(Items.STONE, 63, Items.COBBLESTONE, 13);
		Optional<TickerEvent> event = TickerSnapshot.diffTotals(before, after, Items.STONE, Items.STONE);

		assertTrue(event.isPresent());
		assertEquals(Items.STONE, event.get().item());
		assertEquals(63, event.get().newCount());
	}

	@Test
	void diffTotals_fallsBackToLargestDelta() {
		Map<Item, Integer> before = Map.of(Items.STONE, 64, Items.COBBLESTONE, 10);
		Map<Item, Integer> after = Map.of(Items.STONE, 61, Items.COBBLESTONE, 10);
		Optional<TickerEvent> event = TickerSnapshot.diffTotals(before, after, Items.COBBLESTONE, Items.COBBLESTONE);

		assertTrue(event.isPresent());
		assertEquals(Items.STONE, event.get().item());
		assertEquals(61, event.get().newCount());
		assertEquals(-3, event.get().delta());
	}

	@Test
	void diffTotals_itemDepleted_reportsZeroNewCount() {
		Optional<TickerEvent> event = TickerSnapshot.diffTotals(
			totals(Items.STONE, 1), totals(Items.STONE, 0),
			Items.STONE, Items.STONE
		);

		assertTrue(event.isPresent());
		assertEquals(0, event.get().newCount());
		assertFalse(event.get().newCount() > 0);
	}

	// ---------- diffDurability（耐久挂件，右） ----------

	private static ItemStack helmet(int damage) {
		ItemStack stack = new ItemStack(Items.DIAMOND_HELMET);
		stack.setDamageValue(damage);
		return stack;
	}

	private static ItemStack pickaxe(int damage) {
		ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
		stack.setDamageValue(damage);
		return stack;
	}

	private static List<ArmorSlot> noArmor() {
		return List.of(
			new ArmorSlot(Items.AIR, 0), new ArmorSlot(Items.AIR, 0),
			new ArmorSlot(Items.AIR, 0), new ArmorSlot(Items.AIR, 0)
		);
	}

	private static List<ArmorSlot> armor(ItemStack... stacks) {
		ArmorSlot[] slots = new ArmorSlot[4];
		for (int i = 0; i < 4; i++) {
			if (i < stacks.length && !stacks[i].isEmpty()) {
				slots[i] = new ArmorSlot(stacks[i].getItem(), stacks[i].getDamageValue());
			} else {
				slots[i] = new ArmorSlot(Items.AIR, 0);
			}
		}
		return List.of(slots);
	}

	@Test
	void diffDurability_nullBaseline_noEvent() {
		assertTrue(TickerSnapshot.diffDurability(null, null, null, null).isEmpty());
	}

	@Test
	void diffDurability_noChange_noEvent() {
		Map<Item, DurabilityState> before = Map.of(Items.DIAMOND_PICKAXE, new DurabilityState(1, 5));
		assertTrue(TickerSnapshot.diffDurability(
			before, before, noArmor(), noArmor()
		).isEmpty());
	}

	@Test
	void diffDurability_toolDurabilityDecrease_mining_triggers() {
		Map<Item, DurabilityState> before = Map.of(Items.DIAMOND_PICKAXE, new DurabilityState(1, 5));
		Map<Item, DurabilityState> after = Map.of(Items.DIAMOND_PICKAXE, new DurabilityState(1, 8));

		assertEquals(List.of(Items.DIAMOND_PICKAXE),
			TickerSnapshot.diffDurability(before, after, noArmor(), noArmor()));
	}

	@Test
	void diffDurability_toolDurabilityIncrease_repair_triggers() {
		Map<Item, DurabilityState> before = Map.of(Items.DIAMOND_PICKAXE, new DurabilityState(1, 8));
		Map<Item, DurabilityState> after = Map.of(Items.DIAMOND_PICKAXE, new DurabilityState(1, 3));

		assertEquals(List.of(Items.DIAMOND_PICKAXE),
			TickerSnapshot.diffDurability(before, after, noArmor(), noArmor()));
	}

	@Test
	void diffDurability_armorDurabilityDecrease_triggers() {
		Map<Item, DurabilityState> before = Map.of(Items.DIAMOND_HELMET, new DurabilityState(1, 5));
		Map<Item, DurabilityState> after = Map.of(Items.DIAMOND_HELMET, new DurabilityState(1, 8));
		List<ArmorSlot> beforeArmor = armor(helmet(5));
		List<ArmorSlot> afterArmor = armor(helmet(8));

		assertEquals(List.of(Items.DIAMOND_HELMET),
			TickerSnapshot.diffDurability(before, after, beforeArmor, afterArmor));
	}

	@Test
	void diffDurability_equippingArmor_showsCurrentWorn() {
		// 穿装备：聚合状态不变（物品仍在 43 槽内），盔甲槽变化触发 → 显示当前穿戴
		Map<Item, DurabilityState> durability = Map.of(Items.DIAMOND_HELMET, new DurabilityState(1, 5));

		assertEquals(List.of(Items.DIAMOND_HELMET),
			TickerSnapshot.diffDurability(durability, durability, noArmor(), armor(helmet(5))));
	}

	@Test
	void diffDurability_removingArmor_showsRemainingWorn() {
		Map<Item, DurabilityState> durability = Map.of(
			Items.DIAMOND_HELMET, new DurabilityState(1, 5),
			Items.DIAMOND_CHESTPLATE, new DurabilityState(1, 3)
		);
		List<ArmorSlot> beforeArmor = armor(helmet(5), chestplate(3));
		List<ArmorSlot> afterArmor = armor(helmet(5), ItemStack.EMPTY);

		assertEquals(List.of(Items.DIAMOND_HELMET),
			TickerSnapshot.diffDurability(durability, durability, beforeArmor, afterArmor));
	}

	@Test
	void diffDurability_switchingArmorPiece_showsNewPiece() {
		// 换装：聚合 count 变化（旧甲出装备区/新甲入）由数量挂件处理，盔甲槽变化显示新装备
		Map<Item, DurabilityState> before = Map.of(
			Items.DIAMOND_HELMET, new DurabilityState(1, 5),
			Items.DIAMOND_CHESTPLATE, new DurabilityState(0, 0)
		);
		Map<Item, DurabilityState> after = Map.of(
			Items.DIAMOND_HELMET, new DurabilityState(0, 0),
			Items.DIAMOND_CHESTPLATE, new DurabilityState(1, 5)
		);

		assertEquals(List.of(Items.DIAMOND_CHESTPLATE),
			TickerSnapshot.diffDurability(before, after, armor(helmet(5)), armor(chestplate(5))));
	}

	@Test
	void diffDurability_pickingUpNewTool_noEvent() {
		// 拾取新工具：数量变化由左侧挂件处理，右侧不重复触发
		Map<Item, DurabilityState> before = Map.of(Items.DIAMOND_PICKAXE, new DurabilityState(0, 0));
		Map<Item, DurabilityState> after = Map.of(Items.DIAMOND_PICKAXE, new DurabilityState(1, 5));

		assertTrue(TickerSnapshot.diffDurability(before, after, noArmor(), noArmor()).isEmpty());
	}

	@Test
	void diffDurability_inventorySort_noEvent() {
		// 整理背包：聚合状态与盔甲槽都不变 → 不触发
		Map<Item, DurabilityState> durability = Map.of(Items.DIAMOND_PICKAXE, new DurabilityState(1, 5));

		assertTrue(TickerSnapshot.diffDurability(durability, durability, noArmor(), noArmor()).isEmpty());
	}

	private static ItemStack chestplate(int damage) {
		ItemStack stack = new ItemStack(Items.DIAMOND_CHESTPLATE);
		stack.setDamageValue(damage);
		return stack;
	}
}
