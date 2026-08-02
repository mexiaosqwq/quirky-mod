package dev.quirky.client.mixin;

import dev.quirky.client.pick_range.PickRangeHelper;
import dev.quirky.config.QuirkyConfigHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 远距中键拾取：26.2 中键拾取入口为 {@code Minecraft#pickBlockOrEntity()}，
 * 它直接消费每 tick 缓存的 {@code Minecraft#hitResult}（按原版交互距离计算）。
 * 这里把该方法内的 hitResult 读取重定向为扩展距离的方块射线结果：
 * 原版范围内已有目标（方块或实体）时保持原样，只有原版未命中（MISS）时才用
 * 扩展距离重新对 level 做方块 clip（忽略流体，同原版），命中的方块结果替换后
 * 继续原逻辑走 {@code gameMode.handlePickItemFromBlock}。
 */
@Mixin(Minecraft.class)
public abstract class PickBlockMixin {
	@Redirect(
		method = "pickBlockOrEntity",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;hitResult:Lnet/minecraft/world/phys/HitResult;")
	)
	private HitResult quirky$extendedPickHitResult(Minecraft minecraft) {
		HitResult original = minecraft.hitResult;
		// 原版范围内已有目标：实体拾取、近距离方块拾取等行为一律保持原版。
		if (original == null || original.getType() != HitResult.Type.MISS) {
			return original;
		}
		if (!QuirkyConfigHolder.get().longPick) {
			return original;
		}
		Player player = minecraft.player;
		Entity camera = minecraft.getCameraEntity();
		if (minecraft.level == null || player == null || camera == null) {
			return original;
		}
		boolean creative = player.isCreative();
		if (!PickRangeHelper.isEnabled(creative)) {
			return original;
		}
		int range = PickRangeHelper.rangeFor(creative);
		Vec3 from = camera.getEyePosition(1.0F);
		Vec3 view = camera.getViewVector(1.0F);
		Vec3 to = from.add(view.x * range, view.y * range, view.z * range);
		BlockHitResult hit = minecraft.level.clip(
			new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, camera)
		);
		return hit.getType() == HitResult.Type.MISS ? original : hit;
	}
}
