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
	private static final int MAX_RAISE = 2; // 目标格被挡时最多再上浮 2 格找空气

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
	 * 非整高方块（土径/耕地/半砖/雪层等）顶部站立时，blockPosition() 的 floor 取整
	 * 会让死亡基准凭空低 1 格（如脚底 64.9375 → floor 成 64），导致图腾贴地。
	 * 脚底坐标带小数即视为站在非整高方块顶部，基准上移一格；整数脚底不动。
	 */
	public static BlockPos raiseSpawnBase(double feetY, BlockPos pos) {
		return feetY - Math.floor(feetY) > 0.001 ? pos.above() : pos;
	}

	/**
	 * 图腾悬浮位置：目标格 = 死亡点上方 offset 格（可配置，默认 1）。
	 * 目标格被挡（图腾需要本身 + 上方 1 格共 2 格空气）时向上最多再找 2 格；
	 * 全堵时兜底目标格（offset 仍然生效）。
	 */
	public static BlockPos findSpawnPosition(Level level, BlockPos deathPos, int offset) {
		for (int i = 0; i <= MAX_RAISE; i++) {
			BlockPos candidate = deathPos.above(offset + i);
			if (level.getBlockState(candidate).isAir() && level.getBlockState(candidate.above()).isAir()) {
				return candidate;
			}
		}
		return deathPos.above(offset);
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
