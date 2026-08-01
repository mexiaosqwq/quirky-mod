package dev.quirky.equip_swap;

import java.util.Optional;

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
		context.server().execute(() -> {
			ServerPlayer player = context.player();
			AbstractContainerMenu menu = player.containerMenu;
			if (menu.containerId != payload.containerId()) {
				return;
			}
			if (payload.slotIndex() < 0 || payload.slotIndex() >= menu.slots.size()) {
				return;
			}
			if (!menu.getCarried().isEmpty()) {
				return;
			}

			Slot source = menu.getSlot(payload.slotIndex());
			ItemStack stack = source.getItem();
			if (stack.isEmpty() || !stack.has(DataComponents.EQUIPPABLE)) {
				return;
			}

			EquipmentSlot equipmentSlot = player.getEquipmentSlotForItem(stack);
			int inventoryIndex = inventoryIndexFor(equipmentSlot);
			if (inventoryIndex < 0) {
				return;
			}
			if (source.container == player.getInventory() && source.getContainerSlot() == inventoryIndex) {
				return;
			}
			if (!player.isEquippableInSlot(stack, equipmentSlot)) {
				return;
			}

			ItemStack worn = player.getInventory().getItem(inventoryIndex);
			if (!worn.isEmpty()
				&& EnchantmentHelper.has(worn, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)
				&& !player.isCreative()) {
				return;
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
		});
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
