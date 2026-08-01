package dev.quirky.food;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class MelonSeedHandler {
	private MelonSeedHandler() {
	}

	public static ItemStack finishUsing(ItemStack stack, Level level, LivingEntity entity) {
		boolean isMelonSlice = stack.is(Items.MELON_SLICE);
		ItemStack result = stack.finishUsingItem(level, entity);
		if (entity instanceof ServerPlayer player
			&& isMelonSlice
			&& !player.hasInfiniteMaterials()) {
			ItemStack seed = new ItemStack(Items.MELON_SEEDS);
			if (!player.getInventory().add(seed)) {
				player.drop(seed, false);
			}
		}
		return result;
	}
}
