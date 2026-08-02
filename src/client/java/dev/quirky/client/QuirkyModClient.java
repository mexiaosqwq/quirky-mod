package dev.quirky.client;

import dev.quirky.ModEntities;
import dev.quirky.client.equip_swap.EquipSwapClient;
import dev.quirky.client.tooltips.ClientMapTooltipComponent;
import dev.quirky.client.totem.TotemEntityRenderer;
import dev.quirky.tooltips.MapTooltipComponent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class QuirkyModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EquipSwapClient.init();
		ClientTooltipComponentCallback.EVENT.register(component -> {
			if (component instanceof MapTooltipComponent map) {
				return new ClientMapTooltipComponent(map.mapId());
			}
			return null;
		});
		EntityRenderers.register(ModEntities.TOTEM, TotemEntityRenderer::new);
	}
}
