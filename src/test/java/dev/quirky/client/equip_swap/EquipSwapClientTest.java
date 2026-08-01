package dev.quirky.client.equip_swap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.quirky.TestBootstrap;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EquipSwapClientTest {
	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	@Test
	void creativeInventoryTabUsesSlotIndexDirectly() {
		CreativeModeInventoryScreen screen = mock(CreativeModeInventoryScreen.class);
		when(screen.isInventoryOpen()).thenReturn(true);
		Slot slot = new Slot(mock(Container.class), 7, 0, 0);
		slot.index = 7;

		assertEquals(7, EquipSwapClient.serverSlotIndex(slot, screen, null));
	}

	@Test
	void creativeOtherTabMapsHotbarSlotToInventoryMenuIndex() {
		CreativeModeInventoryScreen screen = mock(CreativeModeInventoryScreen.class);
		when(screen.isInventoryOpen()).thenReturn(false);
		Inventory inventory = mock(Inventory.class);
		Player player = mock(Player.class);
		when(player.getInventory()).thenReturn(inventory);
		Slot slot = new Slot(inventory, 3, 0, 0);
		slot.index = 48;

		assertEquals(39, EquipSwapClient.serverSlotIndex(slot, screen, player));
	}

	@Test
	void creativeOtherTabRejectsItemListSlot() {
		CreativeModeInventoryScreen screen = mock(CreativeModeInventoryScreen.class);
		when(screen.isInventoryOpen()).thenReturn(false);
		Slot slot = new Slot(mock(Container.class), 3, 0, 0);
		slot.index = 3;

		assertEquals(-1, EquipSwapClient.serverSlotIndex(slot, screen, mock(Player.class)));
	}

	@Test
	void creativeOtherTabWithoutPlayerRejects() {
		CreativeModeInventoryScreen screen = mock(CreativeModeInventoryScreen.class);
		when(screen.isInventoryOpen()).thenReturn(false);
		Slot slot = new Slot(mock(Container.class), 3, 0, 0);
		slot.index = 48;

		assertEquals(-1, EquipSwapClient.serverSlotIndex(slot, screen, null));
	}

	@Test
	void creativeOtherTabRejectsNonHotbarContainerSlot() {
		CreativeModeInventoryScreen screen = mock(CreativeModeInventoryScreen.class);
		when(screen.isInventoryOpen()).thenReturn(false);
		Inventory inventory = mock(Inventory.class);
		Player player = mock(Player.class);
		when(player.getInventory()).thenReturn(inventory);
		Slot slot = new Slot(inventory, 12, 0, 0);
		slot.index = 48;

		assertEquals(-1, EquipSwapClient.serverSlotIndex(slot, screen, player));
	}

	@Test
	void normalContainerScreenUsesSlotIndex() {
		Slot slot = new Slot(mock(Container.class), 4, 0, 0);
		slot.index = 4;

		assertEquals(4, EquipSwapClient.serverSlotIndex(slot, mock(Screen.class), null));
	}
}
