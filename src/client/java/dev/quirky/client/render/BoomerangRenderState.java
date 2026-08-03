package dev.quirky.client.render;

import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;

/** 回旋镖渲染状态：物品模型 + 自转角度（由实体 tick 数驱动）。 */
public class BoomerangRenderState extends ItemClusterRenderState {
	public float spinDegrees;
}
