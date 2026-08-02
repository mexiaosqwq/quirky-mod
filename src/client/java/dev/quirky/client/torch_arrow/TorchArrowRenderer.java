package dev.quirky.client.torch_arrow;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.quirky.torch_arrow.TorchArrowEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Renders the vanilla arrow model plus a torch item overlaid at the arrow tip,
 * so a torch arrow in flight visibly carries its torch head.
 */
public class TorchArrowRenderer extends ArrowRenderer<TorchArrowEntity, TorchArrowRenderState> {
	private static final Identifier ARROW_TEXTURE = Identifier.withDefaultNamespace("textures/entity/projectiles/arrow.png");
	private final ItemModelResolver itemModelResolver;

	public TorchArrowRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemModelResolver = context.getItemModelResolver();
	}

	@Override
	protected Identifier getTextureLocation(TorchArrowRenderState state) {
		return ARROW_TEXTURE;
	}

	@Override
	public TorchArrowRenderState createRenderState() {
		return new TorchArrowRenderState();
	}

	@Override
	public void extractRenderState(TorchArrowEntity entity, TorchArrowRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		this.itemModelResolver.updateForNonLiving(state.torchItem, new ItemStack(Items.TORCH), ItemDisplayContext.GROUND, entity);
	}

	@Override
	public void submit(TorchArrowRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
		super.submit(state, poseStack, submitNodeCollector, camera);
		if (state.torchItem.isEmpty()) {
			return;
		}
		poseStack.pushPose();
		// Mirror the arrow's own orientation so the torch follows the arrow's direction.
		poseStack.mulPose(Axis.YP.rotationDegrees(state.yRot - 90.0F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(state.xRot));
		// The arrow model's tip sits at +X; lay the torch there with its flame pointing forward.
		poseStack.translate(0.25F, 0.0F, 0.0F);
		poseStack.mulPose(Axis.ZP.rotationDegrees(-90.0F));
		poseStack.scale(0.8F, 0.8F, 0.8F);
		state.torchItem.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
	}
}
