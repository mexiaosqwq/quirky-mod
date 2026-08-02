package dev.quirky.client.equip_swap;

import dev.quirky.client.mixin.AbstractContainerScreenAccessor;
import dev.quirky.config.QuirkyConfigHolder;
import dev.quirky.equip_swap.EquipSwapPayload;
import dev.quirky.equip_swap.EquipSwapServer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class EquipSwapClient {
	private EquipSwapClient() {
	}

	public static void init() {
		ScreenEvents.BEFORE_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
				return;
			}
			ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
				if (event.button() != 1) {
					return true;
				}
				Slot slot = ((AbstractContainerScreenAccessor) containerScreen)
					.quirky$getHoveredSlot(event.x(), event.y());
				if (slot == null
					|| !slot.hasItem()
					|| !containerScreen.getMenu().getCarried().isEmpty()) {
					return true;
				}
				ItemStack stack = slot.getItem();
				if (!stack.has(DataComponents.EQUIPPABLE)
					&& !(EquipSwapServer.isOffhandSwapItem(stack) && QuirkyConfigHolder.get().offhandSwap)) {
					return true;
				}
				int slotIndex = serverSlotIndex(slot, screen, client.player);
				if (slotIndex < 0) {
					return true;
				}
				ClientPlayNetworking.send(
					new EquipSwapPayload(containerScreen.getMenu().containerId, slotIndex)
				);
				return false;
			});
		});
	}

	static int serverSlotIndex(Slot slot, Screen screen, @Nullable Player player) {
		if (screen instanceof CreativeModeInventoryScreen creativeScreen) {
			if (creativeScreen.isInventoryOpen()) {
				return slot.index;
			}
			if (player == null || slot.container != player.getInventory()) {
				return -1;
			}
			int hotbarIndex = slot.getContainerSlot();
			return hotbarIndex >= 0 && hotbarIndex < 9 ? 36 + hotbarIndex : -1;
		}
		return slot.index;
	}
}
