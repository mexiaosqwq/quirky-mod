package dev.quirky.client;

import dev.quirky.client.equip_swap.EquipSwapClient;
import dev.quirky.client.tooltips.ClientMapTooltipComponent;
import dev.quirky.tooltips.MapTooltipComponent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;

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
	}
}
