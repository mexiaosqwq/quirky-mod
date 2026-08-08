package dev.quirky.client.mixin;

import dev.quirky.client.ladder_snap.LadderSnapHelper;
import dev.quirky.config.QuirkyConfigHolder;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自动上梯（spec 5.9，基岩版式）：爬梯子/藤蔓时未按手动键（W/S/空格/Shift）则按视角控制
 * 垂直速度——抬头（pitch &lt; -15°）自动上升、低头（pitch &gt; 15°）自动下降、平视缓慢下滑（不自动爬），
 * 爬梯只需抬头。脚手架排除（玩家在其上自由走动，原版自带空格升/Shift 降）。注入 {@code Player.travel} HEAD，本 tick 移动即生效。
 *
 * 26.2 mixin 教训：@Inject 的 method 选择器只匹配目标类【本类】方法——{@code travel}
 * 声明在 {@link Player}（LocalPlayer 未覆写），@Mixin(LocalPlayer) + method="travel"
 * 会注入失败（启动报 "was not located in the target class"）。目标类改为 Player 并用
 * instanceof 过滤只处理 LocalPlayer（服务端 ServerPlayer 不受影响）。
 */
@Mixin(Player.class)
public abstract class LocalPlayerAIStepMixin {
	@Inject(method = "travel", at = @At("HEAD"))
	private void quirky$autoClimb(Vec3 input, CallbackInfo ci) {
		if (!QuirkyConfigHolder.get().autoClimbEnabled) {
			return;
		}
		if (!((Object) this instanceof LocalPlayer player)) {
			return;
		}
		// 覆盖面 = onClimbable() 语义（#minecraft:climbable ∪ 梯上同向开放活板门，mcsrc LivingEntity.onClimbable）
		// 即梯子 + 全部藤蔓 + 梯上活板门，仅排除脚手架
		// （原版自带空格升/Shift 降，抬头自动爬会在脚手架塔里莫名上升；见 LadderSnapHelper.isExcluded）
		BlockState climbState = player.getInBlockState();
		if (!player.onClimbable()) {
			// 站非整高方块（土径/耕地/半砖，脚底带小数）：blockPosition 低一格、onClimbable false，
			// 但身体已在上方可爬方块内 → 补检 feet.above()（土径上藤蔓第一格无需跳一下）
			climbState = player.level().getBlockState(player.blockPosition().above());
			if (!climbState.is(BlockTags.CLIMBABLE)) {
				return;
			}
		}
		if (LadderSnapHelper.isExcluded(climbState)) {
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
