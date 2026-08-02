package dev.quirky.food;

import dev.quirky.config.QuirkyConfigHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class MelonSeedHandler {
	private MelonSeedHandler() {
	}

	public static ItemStack finishUsing(ItemStack stack, Level level, LivingEntity entity) {
		if (!QuirkyConfigHolder.get().melonSeed) {
			return stack.finishUsingItem(level, entity); // 恢复原版行为（不吐籽）
		}
		boolean isMelonSlice = stack.is(Items.MELON_SLICE);
		ItemStack result = stack.finishUsingItem(level, entity);
		if (entity instanceof ServerPlayer player
			&& isMelonSlice
			&& !player.hasInfiniteMaterials()
			&& level instanceof ServerLevel serverLevel) {
			ItemStack seed = new ItemStack(Items.MELON_SEEDS);
			ItemEntity item = new ItemEntity(
				serverLevel,
				player.getEyePosition().x + player.getLookAngle().x,
				player.getEyePosition().y + player.getLookAngle().y,
				player.getEyePosition().z + player.getLookAngle().z,
				seed
			);
			item.setPickUpDelay(40);
			item.setThrower(player);
			item.setDeltaMovement(player.getLookAngle().scale(0.3));
			serverLevel.addFreshEntity(item);
			player.playSound(SoundEvents.FOX_SPIT, 1.0F, 1.0F);
		}
		return result;
	}
}
