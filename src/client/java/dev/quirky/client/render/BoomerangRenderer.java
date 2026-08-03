package dev.quirky.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.quirky.entity.BoomerangEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * 回旋镖渲染：仿 {@link net.minecraft.client.renderer.entity.ThrownItemRenderer} 用物品模型渲染，
 * 绕垂直轴自转（每秒约 4 圈，视觉位置 = 实体实际位置，由 EntityRenderState.x/y/z 驱动）。
 */
public class BoomerangRenderer extends EntityRenderer<BoomerangEntity, BoomerangRenderState> {
	private final ItemModelResolver itemModelResolver;

	public BoomerangRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemModelResolver = context.getItemModelResolver();
		this.shadowRadius = 0.2F;
		this.shadowStrength = 0.5F;
	}

	@Override
	public BoomerangRenderState createRenderState() {
		return new BoomerangRenderState();
	}

	@Override
	public void extractRenderState(BoomerangEntity entity, BoomerangRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.extractItemGroupRenderState(entity, entity.getItem(), this.itemModelResolver);
		// 4 圈/秒 = 72°/tick
		state.spinDegrees = (entity.tickCount + partialTicks) * 72.0F;
	}

	@Override
	public void submit(BoomerangRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
		if (!state.item.isEmpty()) {
			poseStack.pushPose();
			poseStack.mulPose(Axis.YP.rotationDegrees(state.spinDegrees));
			// 略倾斜使自转在飞行中清晰可辨
			poseStack.mulPose(Axis.XP.rotationDegrees(65.0F));
			state.item.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
			poseStack.popPose();
		}
		super.submit(state, poseStack, submitNodeCollector, camera);
	}
}
