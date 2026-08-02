package dev.quirky.equip_swap;

import java.util.Optional;

import dev.quirky.QuirkyMod;
import dev.quirky.config.QuirkyConfigHolder;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public final class EquipSwapServer {
	private EquipSwapServer() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(EquipSwapPayload.TYPE, EquipSwapPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(EquipSwapPayload.TYPE, EquipSwapServer::handle);
	}

	private static void handle(EquipSwapPayload payload, ServerPlayNetworking.Context context) {
		if (!QuirkyConfigHolder.get().equipSwap) {
			return;
		}
		context.server().execute(() -> trySwap(context.player(), payload.containerId(), payload.slotIndex()));
	}

	static boolean trySwap(ServerPlayer player, int containerId, int slotIndex) {
		AbstractContainerMenu menu = player.containerMenu;
		if (menu.containerId != containerId) {
			return false;
		}
		if (slotIndex < 0 || slotIndex >= menu.slots.size()) {
			return false;
		}
		if (!menu.getCarried().isEmpty()) {
			return false;
		}

		Slot source = menu.getSlot(slotIndex);
		if (source.isFake()) {
			return false;
		}
		ItemStack stack = source.getItem();
		if (stack.isEmpty() || !stack.has(DataComponents.EQUIPPABLE)) {
			return false;
		}

		EquipmentSlot equipmentSlot = player.getEquipmentSlotForItem(stack);
		int inventoryIndex = inventoryIndexFor(equipmentSlot);
		if (inventoryIndex < 0) {
			return false;
		}
		if (source.container == player.getInventory() && source.getContainerSlot() == inventoryIndex) {
			return false;
		}
		if (!player.isEquippableInSlot(stack, equipmentSlot)) {
			return false;
		}

		ItemStack worn = player.getInventory().getItem(inventoryIndex);
		if (!worn.isEmpty()
			&& EnchantmentHelper.has(worn, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)
			&& !player.isCreative()) {
			return false;
		}
		if (!worn.isEmpty() && !source.mayPlace(worn)) {
			return false;
		}

		Optional<Slot> armorSlot = menu.slots.stream()
			.filter(slot -> slot.container == player.getInventory() && slot.getContainerSlot() == inventoryIndex)
			.findFirst();
		if (armorSlot.isPresent()) {
			Slot target = armorSlot.get();
			target.setByPlayer(stack, worn);
			source.setByPlayer(worn, stack);
		} else {
			player.setItemSlot(equipmentSlot, stack);
			source.setByPlayer(worn, stack);
		}
		menu.broadcastChanges();
		return true;
	}

	private static int inventoryIndexFor(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> 39;
			case CHEST -> 38;
			case LEGS -> 37;
			case FEET -> 36;
			case BODY -> 41;
			default -> -1;
		};
	}
}
