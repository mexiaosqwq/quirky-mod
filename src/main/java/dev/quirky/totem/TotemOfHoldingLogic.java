package dev.quirky.totem;

import net.minecraft.core.BlockPos;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public final class TotemOfHoldingLogic {
	private static final int MAX_RAISE = 2; // 最高升到死亡点上方 2 格——保持在玩家攻击范围（眼睛 1.62 + 3 格射线）内

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

	/**
	 * 自适应悬浮位置：从死亡点上方 1 格起，在玩家可攻击范围内（最多 2 格）
	 * 找 2 格连续空气（图腾本身 + 上方空间）；全堵时兜底上方 1 格。
	 */
	public static BlockPos findSpawnPosition(Level level, BlockPos deathPos) {
		for (int i = 1; i <= MAX_RAISE; i++) {
			BlockPos candidate = deathPos.above(i);
			if (level.getBlockState(candidate).isAir() && level.getBlockState(candidate.above()).isAir()) {
				return candidate;
			}
		}
		return deathPos.above(1);
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
