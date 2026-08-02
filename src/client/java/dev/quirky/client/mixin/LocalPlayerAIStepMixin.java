package dev.quirky.client.mixin;

import dev.quirky.client.ladder_snap.LadderSnapHelper;
import dev.quirky.config.QuirkyConfigHolder;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自动上梯（spec 5.9，基岩版式）：爬梯子/藤蔓时未按手动键（W/S/空格/Shift）则按视角控制
 * 垂直速度——抬头（pitch &lt; -30°）自动上升、低头（pitch &gt; 30°）自动下降、平视悬停，
 * 爬梯只需抬头。注入 {@code Player.travel} HEAD，本 tick 移动即生效。
 *
 * 26.2 mixin 教训：@Inject 的 method 选择器只匹配目标类【本类】方法——{@code travel}
 * 声明在 {@link Player}（LocalPlayer 未覆写），@Mixin(LocalPlayer) + method="travel"
 * 会注入失败（启动报 "was not located in the target class"）。目标类改为 Player 并用
 * instanceof 过滤只处理 LocalPlayer（服务端 ServerPlayer 不受影响）。
 * 脚手架不自动爬（玩家在其上自由走动）。
 */
@Mixin(Player.class)
public abstract class LocalPlayerAIStepMixin {
	@Inject(method = "travel", at = @At("HEAD"))
	private void quirky$autoClimb(Vec3 input, CallbackInfo ci) {
		if (!((Object) this instanceof LocalPlayer player)) {
			return;
		}
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
