package dev.quirky.item;

import dev.quirky.ModBlocks;
import dev.quirky.cloud.CloudPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class BottledCloudItem extends Item {
	public BottledCloudItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		BlockPos pos = CloudPlacement.findNearestAir(
			level,
			player.getEyePosition(),
			player.getLookAngle(),
			player.blockInteractionRange()
		);
		if (pos == null) {
			return InteractionResult.FAIL;
		}
		level.setBlock(pos, ModBlocks.CLOUD.defaultBlockState(), 3);
		player.playSound(SoundEvents.BOTTLE_EMPTY, 1.0F, 1.0F);
		if (!player.hasInfiniteMaterials()) {
			player.getItemInHand(hand).consume(1, player);
		}
		return InteractionResult.SUCCESS;
	}
}
