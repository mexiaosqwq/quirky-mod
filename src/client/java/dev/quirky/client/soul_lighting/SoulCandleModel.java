package dev.quirky.client.soul_lighting;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * 灵魂蜡烛模型：委托原版蜡烛派发模型，把火焰部分替换为自绘灵魂火焰纹理。
 *
 * <p>26.2 原版蜡烛模型（template_candle 系列）把火焰画成蜡烛顶部的两个交叉小面
 * （uv [0,5,1,6]，即 lit 纹理第 5 行的 1 像素），烛身面采样纹理其余区域
 * （侧面 [0,8,2,14]、顶面 [0,6,2,8]、底面 [0,14,2,16]）。因此这里只替换
 * UV 落在火焰区（模型 v ∈ [5, 8)、u ∈ [0, 2)）的 quad，烛身保留各色蜡烛
 * 自身的纹理；未点燃蜡烛不含 lit 纹理，quad 原样透传，外观与原版一致。
 *
 * <p>替换 quad 的 packedUV 按新 sprite 重新打包：packedUV 存的是旧 sprite 在
 * 图集中的绝对坐标，直接复用会采样到错误位置，须先还原成模型 UV（0..16）再
 * 映射到新 sprite 上。
 */
public class SoulCandleModel implements BlockStateModel {
	/** 模型 UV 容差（float 精度）。 */
	private static final float EPS = 0.05F;
	/** 火焰区：模型 UV v ∈ [5, 8)（交叉火焰面 [0,5,1,6] + 烛顶面 [0,6,2,8]）。 */
	private static final float FLAME_V_MIN = 5.0F;
	private static final float FLAME_V_MAX = 8.0F;
	private static final float FLAME_U_MAX = 2.0F;

	private final BlockStateModel delegate;

	public SoulCandleModel(BlockStateModel delegate) {
		this.delegate = delegate;
	}

	@Override
	public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
		List<BlockStateModelPart> parts = new ArrayList<>();
		this.delegate.collectParts(random, parts);
		for (BlockStateModelPart part : parts) {
			output.add(new SoulPart(part));
		}
	}

	@Override
	public Material.Baked particleMaterial() {
		return this.delegate.particleMaterial();
	}

	@Override
	public int materialFlags() {
		return this.delegate.materialFlags();
	}

	/** 该 quad 是否采样了点燃蜡烛纹理的火焰区（lit 纹理 + UV 落在火焰区）。 */
	private static boolean isFlameQuad(BakedQuad quad) {
		BakedQuad.MaterialInfo info = quad.materialInfo();
		if (SoulLightingHelper.soulTextureFor(info.sprite().contents().name()) == null) {
			return false;
		}
		float minU = Float.MAX_VALUE;
		float maxU = -Float.MAX_VALUE;
		float minV = Float.MAX_VALUE;
		float maxV = -Float.MAX_VALUE;
		for (int i = 0; i < BakedQuad.VERTEX_COUNT; i++) {
			long packed = quad.packedUV(i);
			float modelU = modelU(packed, info.sprite());
			float modelV = modelV(packed, info.sprite());
			minU = Math.min(minU, modelU);
			maxU = Math.max(maxU, modelU);
			minV = Math.min(minV, modelV);
			maxV = Math.max(maxV, modelV);
		}
		return maxV <= FLAME_V_MAX + EPS && minV >= FLAME_V_MIN - EPS && maxU <= FLAME_U_MAX + EPS && minU >= -EPS;
	}

	private static float modelU(long packedUV, TextureAtlasSprite sprite) {
		return (UVPair.unpackU(packedUV) - sprite.getU0()) / (sprite.getU1() - sprite.getU0()) * 16.0F;
	}

	private static float modelV(long packedUV, TextureAtlasSprite sprite) {
		return (UVPair.unpackV(packedUV) - sprite.getV0()) / (sprite.getV1() - sprite.getV0()) * 16.0F;
	}

	/** 按新 sprite 重新打包 UV（packedUV 存旧 sprite 的图集绝对坐标，不可直接复用）。 */
	private static long repackUV(long packedUV, TextureAtlasSprite oldSprite, TextureAtlasSprite newSprite) {
		return UVPair.pack(newSprite.getU(modelU(packedUV, oldSprite)), newSprite.getV(modelV(packedUV, oldSprite)));
	}

	private record SoulPart(BlockStateModelPart delegate) implements BlockStateModelPart {
		@Override
		public List<BakedQuad> getQuads(@Nullable Direction direction) {
			List<BakedQuad> quads = this.delegate.getQuads(direction);
			List<BakedQuad> result = new ArrayList<>(quads.size());
			for (BakedQuad quad : quads) {
				if (isFlameQuad(quad)) {
					BakedQuad.MaterialInfo info = quad.materialInfo();
					TextureAtlasSprite soulSprite = SoulLightingModels.soulCandleFlameSprite();
					result.add(
						new BakedQuad(
							quad.position0(),
							quad.position1(),
							quad.position2(),
							quad.position3(),
							repackUV(quad.packedUV(0), info.sprite(), soulSprite),
							repackUV(quad.packedUV(1), info.sprite(), soulSprite),
							repackUV(quad.packedUV(2), info.sprite(), soulSprite),
							repackUV(quad.packedUV(3), info.sprite(), soulSprite),
							quad.direction(),
							new BakedQuad.MaterialInfo(
								soulSprite, info.layer(), info.itemRenderType(), info.tintIndex(), info.shade(), info.lightEmission()
							)
						)
					);
				} else {
					result.add(quad);
				}
			}
			return result;
		}

		@Override
		public boolean useAmbientOcclusion() {
			return this.delegate.useAmbientOcclusion();
		}

		@Override
		public Material.Baked particleMaterial() {
			return this.delegate.particleMaterial();
		}

		@Override
		public int materialFlags() {
			return this.delegate.materialFlags();
		}
	}
}
