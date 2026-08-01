package dev.quirky.harvest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class HarvestFx {
	private HarvestFx() {
	}

	static void playBreak(ServerLevel level, Player player, InteractionHand hand, BlockPos pos, BlockState state) {
		level.levelEvent(null, 2001, pos, Block.getId(state));
		level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 0.9F);
		player.swing(hand, true);
	}

	static void playReplant(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.is(Blocks.NETHER_WART)) {
			level.playSound(null, pos, SoundEvents.NETHER_WART_PLANTED, SoundSource.BLOCKS, 1.0F, 0.9F);
		} else {
			level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 0.9F);
		}
	}
}
