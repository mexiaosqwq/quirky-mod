package dev.quirky.client;

import dev.quirky.ModEntities;
import dev.quirky.ModParticles;
import dev.quirky.client.deathcam.DeathCamClient;
import dev.quirky.client.equip_swap.EquipSwapClient;
import dev.quirky.client.particle.DyedCampfireSmokeProvider;
import dev.quirky.client.tooltips.ClientAttributeTooltipComponent;
import dev.quirky.client.tooltips.ClientFoodTooltipComponent;
import dev.quirky.client.tooltips.ClientMapTooltipComponent;
import dev.quirky.client.tooltips.ClientShulkerTooltipComponent;
import dev.quirky.client.totem.TotemEntityRenderer;
import dev.quirky.client.torch_arrow.TorchArrowRenderer;
import dev.quirky.client.usage_ticker.UsageTickerHud;
import dev.quirky.tooltips.AttributeTooltipComponent;
import dev.quirky.tooltips.FoodTooltipComponent;
import dev.quirky.tooltips.MapTooltipComponent;
import dev.quirky.tooltips.ShulkerTooltipComponent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

public class QuirkyModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EquipSwapClient.init();
		UsageTickerHud.init();
		DeathCamClient.init();
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
		EntityRenderers.register(ModEntities.TORCH_ARROW, TorchArrowRenderer::new);
<<<<<<< HEAD
		// 染色营火烟粒子工厂：fabric-particles-v1 的 PendingParticleProvider 提供 SpriteSet
		ParticleProviderRegistry.getInstance().register(ModParticles.DYED_CAMPFIRE_SMOKE, DyedCampfireSmokeProvider::new);
=======
		// 鱼饵球投掷物渲染为物品图标；诱鱼区用 NoopRenderer（不渲染但必须有渲染器，否则客户端 getRenderer 返回 null 崩溃）
		EntityRenderers.register(ModEntities.FISH_BAIT, ThrownItemRenderer::new);
		EntityRenderers.register(ModEntities.BAIT_ZONE, NoopRenderer::new);
>>>>>>> feat/batch-b-farm-fish
	}
}
