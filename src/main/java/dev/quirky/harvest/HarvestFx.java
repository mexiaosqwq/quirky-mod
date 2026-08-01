package dev.quirky.harvest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

final class HarvestFx {
	private HarvestFx() {
	}

	static void play(ServerLevel level, Player player, InteractionHand hand, BlockPos pos, BlockState state) {
		level.levelEvent(null, 2001, pos, Block.getId(state));
		player.swing(hand, true);
	}
}
