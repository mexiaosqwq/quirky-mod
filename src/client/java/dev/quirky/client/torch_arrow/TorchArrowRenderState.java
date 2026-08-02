package dev.quirky.client.torch_arrow;

import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public class TorchArrowRenderState extends ArrowRenderState {
	public final ItemStackRenderState torchItem = new ItemStackRenderState();
}
