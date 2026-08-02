package dev.quirky.client.mixin;

import dev.quirky.client.deathcam.DeathCamClient;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gui.tick 的死亡界面兜底路径（mcsrc 实测）：玩家 isDeadOrDying 且无屏幕时，
 * Gui.tick 每 tick 调用 setScreen(null)，而 setScreen(null) 在玩家死亡时会自动补开
 * DeathScreen——若只拦截 kill packet 的 setScreen，下一 tick 死亡界面仍会被顶开。
 * 镜头播放期间（含已收到 kill packet 的瞬间）取消该兜底调用，镜头结束/Esc 后由
 * DeathCamClient.finish 显式打开死亡界面，此后 active() 为 false，本注入放行原版流程。
 */
@Mixin(Gui.class)
public class GuiDeathScreenDelayMixin {
	@Inject(
		method = "tick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/Gui;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"
		),
		cancellable = true
	)
	private void quirky$holdDeathScreenDuringCamera(CallbackInfo ci) {
		if (DeathCamClient.active()) {
			ci.cancel();
		}
	}
}
