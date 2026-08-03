package dev.quirky.client.usage_ticker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;

import dev.quirky.TestBootstrap;
import dev.quirky.client.usage_ticker.TickerSnapshot.TickerEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TickerSnapshotTest {

	@BeforeAll
	static void boot() {
		TestBootstrap.boot();
		TestBootstrap.bindItem(Items.STONE);
		TestBootstrap.bindItem(Items.COBBLESTONE);
		TestBootstrap.bindItem(Items.IRON_SWORD);
	}

	private static Map<Item, Integer> totals(Item item, int count) {
		return Map.of(item, count);
	}

	@Test
	void captureTotals_sumsSameItemAcrossSlots() {
		Inventory inventory = mock(Inventory.class);
		when(inventory.getContainerSize()).thenReturn(43);
		when(inventory.getItem(0)).thenReturn(new ItemStack(Items.STONE, 64));
		when(inventory.getItem(1)).thenReturn(new ItemStack(Items.STONE, 3));
		when(inventory.getItem(2)).thenReturn(new ItemStack(Items.COBBLESTONE, 10));
		for (int slot = 3; slot < 43; slot++) {
			when(inventory.getItem(slot)).thenReturn(ItemStack.EMPTY);
		}
		Player player = mock(Player.class);
		when(player.getInventory()).thenReturn(inventory);

		Map<Item, Integer> totals = TickerSnapshot.captureTotals(player);

		assertEquals(2, totals.size());
		assertEquals(67, totals.get(Items.STONE));
		assertEquals(10, totals.get(Items.COBBLESTONE));
	}

	@Test
	void diff_singleItemTotalDecrease_placementScenario() {
		Optional<TickerEvent> event = TickerSnapshot.diff(
			totals(Items.STONE, 64), totals(Items.STONE, 63),
			Items.STONE, Items.STONE
		);

		assertTrue(event.isPresent());
		assertEquals(Items.STONE, event.get().item());
		assertEquals(63, event.get().newCount());
		assertEquals(-1, event.get().delta());
	}

	@Test
	void diff_singleItemTotalIncrease() {
		Optional<TickerEvent> event = TickerSnapshot.diff(
			totals(Items.COBBLESTONE, 10), totals(Items.COBBLESTONE, 13),
			Items.IRON_SWORD, Items.IRON_SWORD
		);

		assertTrue(event.isPresent());
		assertEquals(Items.COBBLESTONE, event.get().item());
		assertEquals(13, event.get().newCount());
		assertEquals(3, event.get().delta());
	}

	@Test
	void diff_redistributionAcrossSlots_noEvent() {
		// 同物品在槽位间移动/整理：总数不变 → 不触发（对应背包整理场景）
		Map<Item, Integer> before = Map.of(Items.STONE, 67, Items.COBBLESTONE, 3);
		Map<Item, Integer> after = Map.of(Items.STONE, 67, Items.COBBLESTONE, 3);

		assertTrue(TickerSnapshot.diff(before, after, Items.STONE, Items.STONE).isEmpty());
	}

	@Test
	void diff_noChange_noEvent() {
		assertTrue(TickerSnapshot.diff(totals(Items.STONE, 4), totals(Items.STONE, 4), Items.STONE, Items.STONE).isEmpty());
	}

	@Test
	void diff_nullBaseline_noEvent() {
		// 玩家切换后的首个 tick：基线为 null，不把整包当作变化
		assertTrue(TickerSnapshot.diff(null, totals(Items.STONE, 64), Items.STONE, Items.STONE).isEmpty());
	}

	@Test
	void diff_fromEmptyInventory_fires() {
		// 真空背包拾取第一件物品：空基线（已建立）也应触发
		Optional<TickerEvent> event = TickerSnapshot.diff(
			Map.of(), totals(Items.STONE, 64), Items.STONE, Items.STONE
		);

		assertTrue(event.isPresent());
		assertEquals(Items.STONE, event.get().item());
		assertEquals(64, event.get().newCount());
		assertEquals(64, event.get().delta());
	}

	@Test
	void diff_mainHandSwitch_firesWithNewHandItem() {
		// 主手槽与另一槽交换物品：总数不变但主手物品变化 → 触发（对齐 Quark 手部元素）
		Map<Item, Integer> before = Map.of(Items.STONE, 64, Items.COBBLESTONE, 1);
		Map<Item, Integer> after = Map.of(Items.STONE, 64, Items.COBBLESTONE, 1);
		Optional<TickerEvent> event = TickerSnapshot.diff(before, after, Items.STONE, Items.COBBLESTONE);

		assertTrue(event.isPresent());
		assertEquals(Items.COBBLESTONE, event.get().item());
		assertEquals(1, event.get().newCount());
		assertEquals(0, event.get().delta());
	}

	@Test
	void diff_mainHandSwitchToEmpty_noEvent() {
		assertTrue(TickerSnapshot.diff(
			totals(Items.STONE, 64), totals(Items.STONE, 64),
			Items.STONE, Items.AIR
		).isEmpty());
	}

	@Test
	void diff_prefersMainHandItem_whenItChanged() {
		// 主手方块被消耗 + 其他槽拾取圆石 同 tick：主手物品优先
		Map<Item, Integer> before = Map.of(Items.STONE, 64, Items.COBBLESTONE, 10);
		Map<Item, Integer> after = Map.of(Items.STONE, 63, Items.COBBLESTONE, 13);
		Optional<TickerEvent> event = TickerSnapshot.diff(before, after, Items.STONE, Items.STONE);

		assertTrue(event.isPresent());
		assertEquals(Items.STONE, event.get().item());
		assertEquals(63, event.get().newCount());
	}

	@Test
	void diff_fallsBackToLargestDelta() {
		Map<Item, Integer> before = Map.of(Items.STONE, 64, Items.COBBLESTONE, 10);
		Map<Item, Integer> after = Map.of(Items.STONE, 61, Items.COBBLESTONE, 10);
		// 主手物品（圆石）总数没变 → 取 |delta| 最大的石头
		Optional<TickerEvent> event = TickerSnapshot.diff(before, after, Items.COBBLESTONE, Items.COBBLESTONE);

		assertTrue(event.isPresent());
		assertEquals(Items.STONE, event.get().item());
		assertEquals(61, event.get().newCount());
		assertEquals(-3, event.get().delta());
	}

	@Test
	void diff_itemDepleted_reportsZeroNewCount() {
		Optional<TickerEvent> event = TickerSnapshot.diff(
			totals(Items.STONE, 1), totals(Items.STONE, 0),
			Items.STONE, Items.STONE
		);

		assertTrue(event.isPresent());
		assertEquals(0, event.get().newCount());
		// 调用方据此过滤：数量归零无从显示
		assertFalse(event.get().newCount() > 0);
	}
}
