package dev.quirky.mixin;

import dev.quirky.config.QuirkyConfigHolder;
import dev.quirky.deathcam.DeathCamPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家死亡（服务端）后发送死亡镜头锚点 payload。
 * 与 ServerPlayerMixin（图腾，dropAllDeathLoot 前注入）互不干扰：本 mixin 仅在 die 尾部追加发送。
 * kill packet（ClientboundPlayerCombatKillPacket）在 die 开头发出，先于本 payload 到达客户端，
 * 客户端以此确定「延迟死亡界面 + 启动镜头」的先后顺序。
 */
@Mixin(ServerPlayer.class)
public class DeathCamServerMixin {
	@Inject(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("RETURN"))
	private void quirky$sendDeathCam(DamageSource source, CallbackInfo ci) {
		if (!QuirkyConfigHolder.get().deathCam) {
			return;
		}
		ServerPlayer player = (ServerPlayer) (Object) this;
		if (player.level().getGameRules().get(GameRules.IMMEDIATE_RESPAWN)) {
			return; // doImmediateRespawn：客户端直接重生、不显示死亡界面，镜头无意义
		}
		ServerPlayNetworking.send(player, new DeathCamPayload(player.position(), player.getYRot(), player.getXRot()));
	}
}
