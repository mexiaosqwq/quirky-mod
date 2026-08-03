package dev.quirky.client.equip_swap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.quirky.TestBootstrap;
import dev.quirky.config.QuirkyConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EquipSwapClientTest {
	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
		TestBootstrap.bindItem(Items.IRON_CHESTPLATE);
		TestBootstrap.bindItem(Items.WIND_CHARGE);
		TestBootstrap.bindItem(Items.FIREWORK_ROCKET);
	}

	@Test
	void dedicatedOffhandItemsDoNotDependOnEquipSwap() {
		QuirkyConfig config = new QuirkyConfig();
		config.equipSwap = false;
		config.offhandSwap = true;

		assertTrue(EquipSwapClient.isQuickEquipEnabled(new ItemStack(Items.WIND_CHARGE), config));
		assertTrue(EquipSwapClient.isQuickEquipEnabled(new ItemStack(Items.FIREWORK_ROCKET), config));
	}

	@Test
	void ordinaryEquipmentDoesNotDependOnOffhandSwap() {
		QuirkyConfig config = new QuirkyConfig();
		config.equipSwap = true;
		config.offhandSwap = false;

		assertTrue(EquipSwapClient.isQuickEquipEnabled(new ItemStack(Items.IRON_CHESTPLATE), config));
	}

	@Test
	void disabledFeaturesDoNotInterceptTheirItems() {
		QuirkyConfig config = new QuirkyConfig();
		config.equipSwap = false;
		config.offhandSwap = false;

		assertFalse(EquipSwapClient.isQuickEquipEnabled(new ItemStack(Items.IRON_CHESTPLATE), config));
		assertFalse(EquipSwapClient.isQuickEquipEnabled(new ItemStack(Items.FIREWORK_ROCKET), config));
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
