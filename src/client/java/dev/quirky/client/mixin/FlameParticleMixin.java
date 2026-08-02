package dev.quirky.client.mixin;

import dev.quirky.client.soul_lighting.SoulLightingHelper;
import dev.quirky.client.soul_lighting.SoulLightingModels;
import dev.quirky.config.QuirkyConfigHolder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 灵魂火焰粒子注入点：火把/蜡烛的 {@link FlameParticle} 生成时，若粒子所在光源正下方
 * （y-1）为灵魂方块，则把 sprite 替换为粒子图集中的 {@code soul_fire_flame}。
 */
@Mixin(FlameParticle.class)
public abstract class FlameParticleMixin {
	@Shadow
	protected abstract void setSprite(TextureAtlasSprite icon);

	@Inject(method = "<init>", at = @At("TAIL"))
	private void quirky$soulFlame(
		ClientLevel level,
		double x,
		double y,
		double z,
		double xd,
		double yd,
		double zd,
		TextureAtlasSprite sprite,
		CallbackInfo ci
	) {
		if (!QuirkyConfigHolder.get().soulLighting) {
			return;
		}
		// 只处理火把/蜡烛自身产生的火焰粒子：粒子所在方块必须是光源方块本身，
		// 且其正下方（y-1）为灵魂方块。熔炉/刷怪笼等也生成 FLAME 粒子，
		// 但它们不是光源方块，不会误替换。
		BlockPos pos = BlockPos.containing(x, y, z);
		if (!SoulLightingHelper.isLightSource(level.getBlockState(pos))) {
			return;
		}
		if (SoulLightingHelper.isSoulBlock(level.getBlockState(pos.below()))) {
			this.setSprite(SoulLightingModels.soulFlameSprite());
		}
	}
}
