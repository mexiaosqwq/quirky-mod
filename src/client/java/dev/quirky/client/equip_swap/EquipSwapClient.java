package dev.quirky.client.equip_swap;

import dev.quirky.client.mixin.AbstractContainerScreenAccessor;
import dev.quirky.equip_swap.EquipSwapPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.Slot;

public final class EquipSwapClient {
	private EquipSwapClient() {
	}

	public static void init() {
		ScreenEvents.BEFORE_INIT.register((client, screen, width, height) -> {
			if (screen instanceof AbstractContainerScreen<?> containerScreen) {
				ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
					if (event.button() != 1) {
						return true;
					}
					Slot slot = ((AbstractContainerScreenAccessor) containerScreen)
						.quirky$getHoveredSlot(event.x(), event.y());
					if (slot == null
						|| !slot.hasItem()
						|| !slot.getItem().has(DataComponents.EQUIPPABLE)
						|| !containerScreen.getMenu().getCarried().isEmpty()) {
						return true;
					}
					ClientPlayNetworking.send(
						new EquipSwapPayload(containerScreen.getMenu().containerId, slot.index)
					);
					return false;
				});
			}
		});
	}
}
