package dev.quirky.client.soul_lighting;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * 灵魂蜡烛模型：委托原版蜡烛派发模型，把火焰纹理（{@code minecraft:block/*_candle_lit}）的
 * quad 替换为自绘灵魂火焰纹理。26.2 原版蜡烛的点燃变体把火焰画进 {@code *_candle_lit} 纹理，
 * 因此整条纹理替换即得到"火焰呈灵魂青色"的效果；未点燃蜡烛不含 lit 纹理，quad 原样透传，
 * 外观与原版一致。
 */
public class SoulCandleModel implements BlockStateModel {
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

	private record SoulPart(BlockStateModelPart delegate) implements BlockStateModelPart {
		@Override
		public List<BakedQuad> getQuads(@Nullable Direction direction) {
			List<BakedQuad> quads = this.delegate.getQuads(direction);
			List<BakedQuad> result = new ArrayList<>(quads.size());
			for (BakedQuad quad : quads) {
				Identifier sprite = quad.materialInfo().sprite().contents().name();
				if (SoulLightingHelper.soulTextureFor(sprite) != null) {
					TextureAtlasSprite soulSprite = SoulLightingModels.soulCandleFlameSprite();
					BakedQuad.MaterialInfo info = quad.materialInfo();
					result.add(
						new BakedQuad(
							quad.position0(),
							quad.position1(),
							quad.position2(),
							quad.position3(),
							quad.packedUV0(),
							quad.packedUV1(),
							quad.packedUV2(),
							quad.packedUV3(),
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
