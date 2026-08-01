package dev.quirky.equip_swap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.quirky.TestBootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EquipSwapServerTest {
	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
		TestBootstrap.bindItem(Items.IRON_CHESTPLATE);
		TestBootstrap.bindItem(Items.STONE);
	}

	private static ServerPlayer creativePlayer() {
		ServerPlayer player = mock(ServerPlayer.class);
		Inventory inventory = new Inventory(player, new EntityEquipment());
		when(player.getInventory()).thenReturn(inventory);
		InventoryMenu menu = new InventoryMenu(inventory, true, player);
		try {
			Player.class.getField("containerMenu").set(player, menu);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
		return player;
	}

	@Test
	void hotbarSlotSwapsChestplate() {
		ServerPlayer player = creativePlayer();
		ItemStack chestplate = new ItemStack(Items.IRON_CHESTPLATE);
		player.getInventory().setItem(0, chestplate);
		when(player.getEquipmentSlotForItem(chestplate)).thenReturn(EquipmentSlot.CHEST);
		when(player.isEquippableInSlot(chestplate, EquipmentSlot.CHEST)).thenReturn(true);

		assertTrue(EquipSwapServer.trySwap(player, 0, 36));

		assertEquals(chestplate, player.getInventory().getItem(38));
		assertTrue(player.getInventory().getItem(0).isEmpty());
		verify(player).onEquipItem(EquipmentSlot.CHEST, ItemStack.EMPTY, chestplate);
	}

	@Test
	void backpackSlotSwapsChestplate() {
		ServerPlayer player = creativePlayer();
		ItemStack chestplate = new ItemStack(Items.IRON_CHESTPLATE);
		player.getInventory().setItem(9, chestplate);
		when(player.getEquipmentSlotForItem(chestplate)).thenReturn(EquipmentSlot.CHEST);
		when(player.isEquippableInSlot(chestplate, EquipmentSlot.CHEST)).thenReturn(true);

		assertTrue(EquipSwapServer.trySwap(player, 0, 9));

		assertEquals(chestplate, player.getInventory().getItem(38));
		assertTrue(player.getInventory().getItem(9).isEmpty());
	}

	@Test
	void wornEquipmentSlotRejected() {
		ServerPlayer player = creativePlayer();
		ItemStack chestplate = new ItemStack(Items.IRON_CHESTPLATE);
		player.getInventory().setItem(38, chestplate);
		when(player.getEquipmentSlotForItem(chestplate)).thenReturn(EquipmentSlot.CHEST);

		assertFalse(EquipSwapServer.trySwap(player, 0, 6));

		assertEquals(chestplate, player.getInventory().getItem(38));
	}

	@Test
	void nonEquippableItemRejected() {
		ServerPlayer player = creativePlayer();
		player.getInventory().setItem(0, new ItemStack(Items.STONE));

		assertFalse(EquipSwapServer.trySwap(player, 0, 36));
	}

	@Test
	void wrongContainerIdRejected() {
		ServerPlayer player = creativePlayer();
		ItemStack chestplate = new ItemStack(Items.IRON_CHESTPLATE);
		player.getInventory().setItem(0, chestplate);

		assertFalse(EquipSwapServer.trySwap(player, 1, 36));
	}
}
