package dev.quirky.client.mixin;

import dev.quirky.client.deathcam.DeathCamClient;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2 相机管线：Camera.update(DeltaTracker) → alignWithEntity(partialTicks) 从相机实体
 * 计算位置/旋转（第一/第三人称均在此完成）。在 alignWithEntity 返回后覆写为死亡镜头
 * 时间轴插值——相机实体仍为玩家（死亡 FOV 缩放、尸体渲染等原版行为不受影响）。
 *
 * 第三人称标志（detached）由 alignWithEntity 依据 options.cameraType 设置，镜头启动时
 * DeathCamClient 已强制第三人称，尸体因此可见、第一人称手持物不渲染；鼠标改的是玩家实体
 * 的旋转，镜头旋转由时间轴插值固定，天然不受视角输入影响。
 */
@Mixin(Camera.class)
public class CameraSetupMixin {
	@Inject(method = "alignWithEntity", at = @At("RETURN"))
	private void quirky$deathCamOverride(float partialTicks, CallbackInfo ci) {
		Camera camera = (Camera) (Object) this;
		if (DeathCamClient.active()) {
			Vec3 position = DeathCamClient.cameraPosition(partialTicks);
			((CameraAccessor) camera).quirky$invokeSetPosition(position);
			((CameraAccessor) camera).quirky$invokeSetRotation(
				DeathCamClient.cameraYaw(partialTicks), DeathCamClient.cameraPitch(partialTicks));
			return;
		}
		if (DeathCamClient.frozen()) {
			// 死亡界面期间保持镜头冻结视角（背景 = 镜头最后画面 + 原版红色渐变，无闪回）；
			// 玩家重生/退出死亡界面后解除冻结，相机交还原版逻辑。
			// TitleConfirmScreen（死亡界面点"退出到标题"的确认框）也保持冻结。
			if (Minecraft.getInstance().gui.screen() instanceof DeathScreen
				|| Minecraft.getInstance().gui.screen() instanceof DeathScreen.TitleConfirmScreen) {
				((CameraAccessor) camera).quirky$invokeSetPosition(DeathCamClient.frozenPosition());
				((CameraAccessor) camera).quirky$invokeSetRotation(
					DeathCamClient.frozenYaw(), DeathCamClient.frozenPitch());
			} else {
				DeathCamClient.unfreeze();
			}
		}
	}
}
