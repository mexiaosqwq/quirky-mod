package dev.quirky.client.demobeast;

import dev.quirky.demobeast.DemoBeastEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/**
 * demo_beast 渲染器：26.2 RenderState 模式（对照 FrogRenderer）。
 */
public class DemoBeastRenderer extends MobRenderer<DemoBeastEntity, DemoBeastRenderState, DemoBeastModel> {
	public DemoBeastRenderer(final EntityRendererProvider.Context context) {
		super(context, new DemoBeastModel(context.bakeLayer(DemoBeastModel.LAYER_LOCATION)), 0.5F);
	}

	@Override
	public Identifier getTextureLocation(final DemoBeastRenderState state) {
		return state.texture;
	}

	@Override
	public DemoBeastRenderState createRenderState() {
		return new DemoBeastRenderState();
	}

	@Override
	public void extractRenderState(final DemoBeastEntity entity, final DemoBeastRenderState state, final float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.walkAnimationState.copyFrom(entity.walkAnimationState);
		state.idleAnimationState.copyFrom(entity.idleAnimationState);
		state.tailWagAnimationState.copyFrom(entity.tailWagAnimationState);
	}
}
