package dev.quirky.client;

import dev.quirky.ModEntities;
import dev.quirky.client.equip_swap.EquipSwapClient;
import dev.quirky.client.greener_grass.GreenerGrassClient;
import dev.quirky.client.tooltips.ClientAttributeTooltipComponent;
import dev.quirky.client.tooltips.ClientFoodTooltipComponent;
import dev.quirky.client.tooltips.ClientMapTooltipComponent;
import dev.quirky.client.tooltips.ClientShulkerTooltipComponent;
import dev.quirky.client.totem.TotemEntityRenderer;
import dev.quirky.tooltips.AttributeTooltipComponent;
import dev.quirky.tooltips.FoodTooltipComponent;
import dev.quirky.tooltips.MapTooltipComponent;
import dev.quirky.tooltips.ShulkerTooltipComponent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class QuirkyModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EquipSwapClient.init();
		GreenerGrassClient.init();
		ClientTooltipComponentCallback.EVENT.register(component -> {
			if (component instanceof MapTooltipComponent map) {
				return new ClientMapTooltipComponent(map.mapId());
			}
			if (component instanceof ShulkerTooltipComponent shulker) {
				return new ClientShulkerTooltipComponent(shulker);
			}
			if (component instanceof FoodTooltipComponent food) {
				return new ClientFoodTooltipComponent(food);
			}
			if (component instanceof AttributeTooltipComponent attribute) {
				return new ClientAttributeTooltipComponent(attribute);
			}
			return null;
		});
		EntityRenderers.register(ModEntities.TOTEM, TotemEntityRenderer::new);
	}
}
