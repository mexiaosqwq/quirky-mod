package dev.quirky.client.mixin;

import dev.quirky.client.deathcam.DeathCamClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Esc 提前跳过死亡镜头：26.2 中无屏幕时按 Esc 走 Minecraft.pauseGame →
 * Gui.setPauseScreen 打开暂停菜单（玩家死亡时暂停菜单无意义）。镜头播放期间拦截
 * pauseGame（Esc 与失焦暂停共用此入口），改为立即结束镜头并进入死亡界面。
 */
@Mixin(Minecraft.class)
public class DeathCamSkipMixin {
	@Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
	private void quirky$skipDeathCamOnEsc(boolean suppressPauseMenuIfWeReallyArePausing, CallbackInfo ci) {
		if (DeathCamClient.active()) {
			DeathCamClient.skip();
			ci.cancel();
		}
	}
}
