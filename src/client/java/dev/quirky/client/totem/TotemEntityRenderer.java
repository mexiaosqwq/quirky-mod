package dev.quirky.client.totem;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.quirky.ModItems;
import dev.quirky.totem.TotemEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public class TotemEntityRenderer extends EntityRenderer<TotemEntity, TotemEntityRenderState> {
	private final ItemModelResolver itemModelResolver;

	public TotemEntityRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemModelResolver = context.getItemModelResolver();
		this.shadowRadius = 0.2F;
		this.shadowStrength = 0.5F;
	}

	@Override
	public TotemEntityRenderState createRenderState() {
		return new TotemEntityRenderState();
	}

	@Override
	public void extractRenderState(TotemEntity entity, TotemEntityRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.extractItemGroupRenderState(entity, new ItemStack(ModItems.TOTEM_OF_HOLDING), this.itemModelResolver);
	}

	@Override
	public void submit(TotemEntityRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
		if (!state.item.isEmpty()) {
			poseStack.pushPose();
			float bob = Mth.sin(state.ageInTicks / 12.0F) * 0.25F + 2.5F;
			poseStack.translate(0.0F, bob, 0.0F);
			poseStack.mulPose(Axis.YP.rotation(state.ageInTicks / 8.0F));
			poseStack.mulPose(Axis.XP.rotation(Mth.sin(state.ageInTicks / 20.0F) * 0.08F));
			state.item.submit(poseStack, submitNodeCollector, 0xF000F0, OverlayTexture.NO_OVERLAY, state.outlineColor);
			poseStack.popPose();
			super.submit(state, poseStack, submitNodeCollector, camera);
		}
	}
}
