package dev.quirky.mixin;

import dev.quirky.door.DoubleDoorHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DoorBlock.class)
public abstract class DoubleDoorMixin {
	@Inject(method = "useWithoutItem", at = @At("HEAD"))
	private void quirky$syncBeforeHandUse(
		BlockState state,
		Level level,
		BlockPos pos,
		Player player,
		BlockHitResult hitResult,
		CallbackInfoReturnable<InteractionResult> cir
	) {
		if (DoorBlock.isWoodenDoor(level, pos)) {
			DoubleDoorHandler.sync(level, pos, player, !state.getValue(DoorBlock.OPEN));
		}
	}

	@Inject(method = "setOpen", at = @At("TAIL"))
	private void quirky$syncAfterSetOpen(
		Entity sourceEntity,
		Level level,
		BlockState state,
		BlockPos pos,
		boolean shouldOpen,
		CallbackInfo ci
	) {
		if (DoorBlock.isWoodenDoor(level, pos)) {
			DoubleDoorHandler.sync(level, pos, sourceEntity, shouldOpen);
		}
	}
}
