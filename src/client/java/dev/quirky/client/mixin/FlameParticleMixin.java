package dev.quirky.client.mixin;

import dev.quirky.client.soul_lighting.SoulLightingHelper;
import dev.quirky.client.soul_lighting.SoulLightingModels;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.particle.SingleQuadParticle;
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
 *
 * 26.2 崩溃教训：mixin 的 {@code @Shadow} 只匹配目标类**本类**成员——{@code setSprite}
 * 声明在父类 {@link SingleQuadParticle}，@Mixin(FlameParticle) + @Shadow setSprite 会
 * 抛 "was not located in the target class"。因此目标类改为 {@link SingleQuadParticle}
 * （setSprite 为本类方法），注入构造器并用 instanceof 过滤只处理 FlameParticle。
 * FlameParticle 使用 7 参构造器（火把/蜡烛共用），4 参构造器是其他粒子的，不注入。
 */
@Mixin(SingleQuadParticle.class)
public abstract class FlameParticleMixin {
	@Shadow
	protected abstract void setSprite(TextureAtlasSprite icon);

	/**
	 * 只注入 FlameParticle 使用的 7 参构造器（{@code (ClientLevel, double×6, TextureAtlasSprite)}）。
	 * 必须用完整描述符限定：@Inject(method="<init>") 会应用到目标类所有构造器，
	 * handler 与 4 参构造器（其他粒子使用）不匹配会在 APPLY 阶段抛 InvalidInjectionException。
	 */
	@Inject(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDDLnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V", at = @At("TAIL"))
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
		if (!((Object) this instanceof FlameParticle)) {
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
