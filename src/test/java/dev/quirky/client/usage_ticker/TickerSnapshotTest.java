package dev.quirky.client.usage_ticker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import dev.quirky.TestBootstrap;
import dev.quirky.client.usage_ticker.TickerSnapshot.SlotSnapshot;
import dev.quirky.client.usage_ticker.TickerSnapshot.TickerEvent;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
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
	}

	@Test
	void diff_singleSlotIncrease() {
		List<SlotSnapshot> before = List.of(new SlotSnapshot(0, Items.COBBLESTONE, 10));
		List<SlotSnapshot> after = List.of(new SlotSnapshot(0, Items.COBBLESTONE, 13));

		Optional<TickerEvent> event = TickerSnapshot.diff(before, after);

		assertTrue(event.isPresent());
		assertEquals(Items.COBBLESTONE, event.get().item());
		assertEquals(13, event.get().newCount());
		assertEquals(3, event.get().delta());
	}

	@Test
	void diff_singleSlotDecrease() {
		List<SlotSnapshot> before = List.of(new SlotSnapshot(2, Items.STONE, 8));
		List<SlotSnapshot> after = List.of(new SlotSnapshot(2, Items.STONE, 5));

		Optional<TickerEvent> event = TickerSnapshot.diff(before, after);

		assertTrue(event.isPresent());
		assertEquals(Items.STONE, event.get().item());
		assertEquals(5, event.get().newCount());
		assertEquals(-3, event.get().delta());
	}

	@Test
	void diff_twoSlotsChanged_returnsEmpty() {
		List<SlotSnapshot> before = List.of(
			new SlotSnapshot(0, Items.STONE, 10),
			new SlotSnapshot(1, Items.COBBLESTONE, 3)
		);
		List<SlotSnapshot> after = List.of(
			new SlotSnapshot(1, Items.STONE, 10),
			new SlotSnapshot(0, Items.COBBLESTONE, 3)
		);

		assertTrue(TickerSnapshot.diff(before, after).isEmpty());
	}

	@Test
	void diff_emptyLists_returnsEmpty() {
		assertTrue(TickerSnapshot.diff(List.of(), List.of()).isEmpty());
	}

	@Test
	void diff_noChange_returnsEmpty() {
		List<SlotSnapshot> snapshots = List.of(new SlotSnapshot(0, Items.STONE, 4));
		assertTrue(TickerSnapshot.diff(snapshots, snapshots).isEmpty());
	}

	@Test
	void capture_has41SlotsWithItemAndCount() {
		Player player = mock(Player.class);
		Inventory inventory = new Inventory(player, new EntityEquipment());
		when(player.getInventory()).thenReturn(inventory);
		inventory.setItem(0, new ItemStack(Items.STONE, 5));
		inventory.setItem(40, new ItemStack(Items.COBBLESTONE, 2));

		List<SlotSnapshot> snapshots = TickerSnapshot.capture(player);

		assertEquals(41, snapshots.size());
		assertEquals(new SlotSnapshot(0, Items.STONE, 5), snapshots.get(0));
		assertEquals(new SlotSnapshot(36, Items.AIR, 0), snapshots.get(36));
		assertEquals(new SlotSnapshot(40, Items.COBBLESTONE, 2), snapshots.get(40));
	}

	@Test
	void captureAndDiff_detectsPickupAndIgnoreSorting() {
		Player player = mock(Player.class);
		Inventory inventory = new Inventory(player, new EntityEquipment());
		when(player.getInventory()).thenReturn(inventory);
		inventory.setItem(0, new ItemStack(Items.STONE, 10));

		List<SlotSnapshot> before = TickerSnapshot.capture(player);
		inventory.setItem(0, new ItemStack(Items.STONE, 13));
		Optional<TickerEvent> pickup = TickerSnapshot.diff(before, TickerSnapshot.capture(player));
		assertTrue(pickup.isPresent());
		assertEquals(3, pickup.get().delta());

		// 同帧多槽变化（整理）不触发
		inventory.setItem(0, new ItemStack(Items.COBBLESTONE, 1));
		inventory.setItem(1, new ItemStack(Items.STONE, 12));
		assertTrue(TickerSnapshot.diff(before, TickerSnapshot.capture(player)).isEmpty());
	}
}
