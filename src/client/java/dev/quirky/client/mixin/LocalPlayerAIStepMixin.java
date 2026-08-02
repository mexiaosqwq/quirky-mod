package dev.quirky.client.mixin;

import dev.quirky.client.ladder_snap.LadderSnapHelper;
import dev.quirky.config.QuirkyConfigHolder;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自动上梯（spec 5.9，基岩版式）：爬梯子/藤蔓时未按手动键（W/S/空格/Shift）则按视角控制
 * 垂直速度——抬头（pitch &lt; -30°）自动上升、低头（pitch &gt; 30°）自动下降、平视悬停，
 * 爬梯只需抬头。注入 {@code LocalPlayer.travel} HEAD，本 tick 移动即生效。
 * 脚手架不自动爬（玩家在其上自由走动）。
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerAIStepMixin {
	@Inject(method = "travel", at = @At("HEAD"))
	private void quirky$autoClimb(Vec3 input, CallbackInfo ci) {
		LocalPlayer player = (LocalPlayer) (Object) this;
		if (!QuirkyConfigHolder.get().ladderSnap || !player.onClimbable()) {
			return;
		}
		// 脚手架在 #minecraft:climbable 中，但玩家在其上应自由走动，不自动爬
		if (player.getInBlockState().is(Blocks.SCAFFOLDING)) {
			return;
		}
		boolean manual = player.input.keyPresses.forward()
			|| player.input.keyPresses.backward()
			|| player.input.keyPresses.jump()
			|| player.input.keyPresses.shift();
		double vy = LadderSnapHelper.climbVelocity(player.getXRot(), manual);
		if (!Double.isNaN(vy)) {
			player.setDeltaMovement(player.getDeltaMovement().x, vy, player.getDeltaMovement().z);
		}
	}
}
