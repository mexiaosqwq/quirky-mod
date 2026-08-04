package dev.quirky.client.demobeast;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;

/**
 * demo_beast 渲染状态：26.2 RenderState 模式（对照 FrogRenderState）。
 * 动画状态由实体 tick 驱动，经 extractRenderState.copyFrom 同步到客户端。
 */
public class DemoBeastRenderState extends LivingEntityRenderState {
	public final AnimationState walkAnimationState = new AnimationState();
	public final AnimationState idleAnimationState = new AnimationState();
	public final AnimationState tailWagAnimationState = new AnimationState();
	public Identifier texture = Identifier.fromNamespaceAndPath("quirky", "textures/entity/demo_beast/demo_beast.png");
}
