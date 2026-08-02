package dev.quirky.totem;

import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.List;

public final class TotemOfHoldingLogic {
	private TotemOfHoldingLogic() {
	}

	public static boolean shouldSpawnTotem(Player player, DamageSource source, boolean keepInventory) {
		return !player.isSpectator()
			&& !player.hasInfiniteMaterials()
			&& !keepInventory
			&& !(source.getEntity() instanceof Player);
	}

	public static List<ItemStackWithSlot> collectInventory(Player player) {
		Inventory inventory = player.getInventory();
		List<ItemStackWithSlot> stored = new ArrayList<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty() || EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
				continue;
			}
			stored.add(new ItemStackWithSlot(i, stack.copy()));
		}
		return stored;
	}

	public static List<ItemStack> restoreToPlayer(Player player, List<ItemStackWithSlot> stored) {
		Inventory inventory = player.getInventory();
		List<ItemStack> overflow = new ArrayList<>();
		for (ItemStackWithSlot entry : stored) {
			ItemStack stack = entry.stack();
			int slot = entry.slot();
			if (slot < inventory.getContainerSize() && inventory.getItem(slot).isEmpty()) {
				inventory.setItem(slot, stack);
			} else {
				int free = inventory.getFreeSlot();
				if (free != -1) {
					inventory.setItem(free, stack);
				} else {
					overflow.add(stack);
				}
			}
		}
		return overflow;
	}
}
