package dev.quirky.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Camera.setRotation/setPosition 为 protected，mixin 需经 @Invoker 访问
 * （两个方法会同步更新旋转四元数、forward/up/left 向量与 blockPosition）。
 */
@Mixin(Camera.class)
public interface CameraAccessor {
	@Invoker("setRotation")
	void quirky$invokeSetRotation(float yRot, float xRot);

	@Invoker("setPosition")
	void quirky$invokeSetPosition(Vec3 position);
}
