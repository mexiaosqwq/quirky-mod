package dev.quirky.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.quirky.ModItems;
import dev.quirky.client.render.BoomerangHandPose;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 回旋镖第一人称蓄力握持姿势：包裹 {@code submitArmWithItem} 的 {@code renderItem} 调用
 * （else 分支 ordinal=1，回旋镖非 crossbow 不走 ordinal=0），在回旋镖蓄力时叠加
 * {@link BoomerangHandPose}（竖向握持 + 蓄力后摆 + 满蓄抖动），替代原版 TRIDENT 长杆姿势。
 *
 * <p>配合 {@code BoomerangItem.getUseAnimation = NONE}：不走 TRIDENT switch 分支，
 * applyItemArmTransform 后直接进 renderItem，本 mixin 在此处包裹叠加自定义 pose。
 * 非回旋镖物品原样调用，不受影响。
 *
 * <p>注入点经 javap 核对（26.2 named 映射）：
 * <pre>ItemInHandRenderer#renderItem(LivingEntity, ItemStack, ItemDisplayContext, PoseStack, SubmitNodeCollector, int)V</pre>
 * submitArmWithItem 内两次调用：ordinal=0=crossbow 分支，ordinal=1=else 分支（回旋镖走此）。
 */
@Mixin(ItemInHandRenderer.class)
public abstract class BoomerangHandRenderMixin {

	@WrapOperation(
		method = "submitArmWithItem",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
			ordinal = 1
		)
	)
	private void quirky$boomerangChargePose(
		ItemInHandRenderer self,
		LivingEntity mob,
		ItemStack stack,
		ItemDisplayContext type,
		PoseStack pose,
		SubmitNodeCollector submitNodeCollector,
		int lightCoords,
		Operation<Void> original
	) {
		if (stack.is(ModItems.BOOMERANG)
			&& mob instanceof Player player
			&& player.isUsingItem()
			&& player.getUseItemRemainingTicks() > 0) {
			int invert = type == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND ? 1 : -1;
			pose.pushPose();
			BoomerangHandPose.applyChargePose(pose, player, stack, invert);
			original.call(self, mob, stack, type, pose, submitNodeCollector, lightCoords);
			pose.popPose();
		} else {
			original.call(self, mob, stack, type, pose, submitNodeCollector, lightCoords);
		}
	}
}
