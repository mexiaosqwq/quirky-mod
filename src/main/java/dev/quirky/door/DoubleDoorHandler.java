package dev.quirky.door;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;

public final class DoubleDoorHandler {
	private DoubleDoorHandler() {
	}

	public static void sync(Level level, BlockPos pos, Entity source, boolean shouldOpen) {
		if (level.isClientSide()) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof DoorBlock)) {
			return;
		}
		if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
			pos = pos.below();
			state = level.getBlockState(pos);
		}
		if (!(state.getBlock() instanceof DoorBlock door)) {
			return;
		}

		Direction facing = state.getValue(DoorBlock.FACING);
		Direction side = state.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT
			? facing.getClockWise()
			: facing.getCounterClockWise();
		BlockPos partnerPos = pos.relative(side);
		BlockState partnerState = level.getBlockState(partnerPos);
		if (!isPartner(state, partnerState)) {
			return;
		}
		if (partnerState.getValue(DoorBlock.OPEN) == shouldOpen) {
			return;
		}

		level.setBlock(partnerPos, partnerState.setValue(DoorBlock.OPEN, shouldOpen), 10);
		SoundEvent sound = shouldOpen ? door.type().doorOpen() : door.type().doorClose();
		level.playSound(source, partnerPos, sound, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
		level.gameEvent(source, shouldOpen ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, partnerPos);
	}

	private static boolean isPartner(BlockState state, BlockState partnerState) {
		return partnerState.is(state.getBlock())
			&& partnerState.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
			&& partnerState.getValue(DoorBlock.FACING) == state.getValue(DoorBlock.FACING)
			&& partnerState.getValue(DoorBlock.HINGE) != state.getValue(DoorBlock.HINGE);
	}
}
