package dev.quirky.mixin;

import dev.quirky.door.DoubleDoorHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
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
		DoubleDoorHandler.sync(level, pos, state, player, cir.getReturnValue());
	}
}
