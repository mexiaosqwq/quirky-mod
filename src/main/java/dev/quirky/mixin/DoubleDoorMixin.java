package dev.quirky.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DoorBlock.class)
public abstract class DoubleDoorMixin {
	@Inject(method = "useWithoutItem", at = @At("TAIL"))
	private void quirky$syncPartner(
		BlockState state,
		Level level,
		BlockPos pos,
		Player player,
		BlockHitResult hitResult,
		CallbackInfoReturnable<InteractionResult> cir
	) {
		if (level.isClientSide() || !(state.getBlock() instanceof DoorBlock door)) {
			return;
		}
		if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
			pos = pos.below();
		}
		Direction facing = state.getValue(DoorBlock.FACING);
		Direction side = state.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT
			? facing.getClockWise()
			: facing.getCounterClockWise();
		BlockPos partnerPos = pos.relative(side);
		BlockState partnerState = level.getBlockState(partnerPos);
		if (partnerState.is(state.getBlock())
			&& partnerState.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
			&& partnerState.getValue(DoorBlock.FACING) == facing) {
			door.setOpen(player, level, partnerState, partnerPos, state.getValue(DoorBlock.OPEN));
		}
	}
}
