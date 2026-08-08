package dev.quirky.client.mixin;

import dev.quirky.client.deathcam.DeathCamClient;
import dev.quirky.config.QuirkyConfigHolder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2 死亡界面延迟注入点（mcsrc 实测结论）：
 * 客户端死亡界面由服务端包驱动——ClientPacketListener.handlePlayerCombatKill 收到
 * ClientboundPlayerCombatKillPacket 后直接 mc.gui.setScreen(DeathScreen)（玩家死亡时
 * 客户端 LocalPlayer 无 die 路径可注入）。此处拦截该 setScreen：
 * - deathCam 开 → 取消原 setScreen，启动镜头状态机（死亡信息暂存，镜头结束/Esc 后显示）；
 * - 关 → 原版行为不变。
 *
 * 注意：Gui.tick 在玩家死亡且无屏幕时会重新 setScreen(null) 自动补开死亡界面
 * （GuiDeathScreenDelayMixin 处理），否则延迟会被原版流程顶掉。
 */
@Mixin(ClientPacketListener.class)
public class DeathScreenDelayMixin {
	@Shadow
	private ClientLevel level;

	@Inject(
		method = "handlePlayerCombatKill",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/Gui;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"
		),
		cancellable = true
	)
	private void quirky$delayDeathScreen(ClientboundPlayerCombatKillPacket packet, CallbackInfo ci) {
		if (!QuirkyConfigHolder.get().deathCamEnabled) {
			return; // 不 cancel → 原版死亡界面立即打开（带死亡消息行）
		}
		ci.cancel();
		DeathCamClient.onKillPacket(packet.message(), this.level.getLevelData().isHardcore());
	}
}
