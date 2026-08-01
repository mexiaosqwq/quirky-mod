package dev.quirky.door;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public final class DoubleDoorHandler {
	private DoubleDoorHandler() {
	}

	public static void sync(Level level, BlockPos pos, BlockState state, Player player, InteractionResult result) {
		if (level.isClientSide() || !result.consumesAction()) {
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
		if (isPartner(state, partnerState)) {
			door.setOpen(player, level, partnerState, partnerPos, state.getValue(DoorBlock.OPEN));
		}
	}

	private static boolean isPartner(BlockState state, BlockState partnerState) {
		return partnerState.is(state.getBlock())
			&& partnerState.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
			&& partnerState.getValue(DoorBlock.FACING) == state.getValue(DoorBlock.FACING)
			&& partnerState.getValue(DoorBlock.HINGE) != state.getValue(DoorBlock.HINGE);
	}
}
