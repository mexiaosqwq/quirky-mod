package dev.quirky.harvest;

import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.quirky.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HarvestFxTest {
	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	@Test
	void playsBreakParticlesAndSendsSwingToClickingPlayer() {
		ServerLevel level = mock(ServerLevel.class);
		Player player = mock(Player.class);
		BlockPos pos = new BlockPos(1, 64, 1);
		BlockState state = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE);

		HarvestFx.playBreak(level, player, InteractionHand.MAIN_HAND, pos, state);

		verify(level).levelEvent(isNull(), eq(2001), eq(pos), eq(Block.getId(state)));
		verify(player).swing(InteractionHand.MAIN_HAND, true);
	}

	@Test
	void playsBreakSoundAndReplantSound() {
		ServerLevel level = mock(ServerLevel.class);
		Player player = mock(Player.class);
		BlockPos pos = new BlockPos(1, 64, 1);
		BlockState wheat = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE);

		HarvestFx.playBreak(level, player, InteractionHand.MAIN_HAND, pos, wheat);
		HarvestFx.playReplant(level, pos, wheat);

		verify(level).playSound(isNull(), eq(pos), eq(wheat.getSoundType().getBreakSound()), eq(SoundSource.BLOCKS), anyFloat(), anyFloat());
		verify(level).playSound(isNull(), eq(pos), eq(SoundEvents.CROP_PLANTED), eq(SoundSource.BLOCKS), anyFloat(), anyFloat());
		verify(level).levelEvent(isNull(), eq(2001), eq(pos), eq(Block.getId(wheat)));
		verify(player).swing(InteractionHand.MAIN_HAND, true);
	}
}
