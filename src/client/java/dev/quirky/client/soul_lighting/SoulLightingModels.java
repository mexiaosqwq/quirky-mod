package dev.quirky.client.soul_lighting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 灵魂光源模型解析（依赖 Minecraft 客户端运行时，不可单测）。
 *
 * <p>26.2 中 {@code BlockModelShaper} 已被 {@link BlockStateModelSet}/{@link ModelManager} 取代，
 * 模型按 BlockState 派发且无位置信息；真正的渲染入口 {@code ModelBlockRenderer.tesselateBlock}
 * 才同时持有 Level 与 BlockPos。因此模型替换发生在 {@code SectionCompiler.compile} 的
 * tesselateBlock 调用点（见 SectionCompilerMixin），由本类把灵魂变体 id 解析为实际模型：
 * <ul>
 *   <li>火把/墙上火把/灯笼 → 直接从 {@link BlockStateModelSet} 取原版灵魂变体模型
 *       （墙上火把/灯笼保留原朝向与悬挂属性）；</li>
 *   <li>蜡烛 → {@link SoulCandleModel} 包装原蜡烛模型，把火焰纹理替换为灵魂火焰纹理。</li>
 * </ul>
 */
public final class SoulLightingModels {
	private SoulLightingModels() {
	}

	/**
	 * 渲染期解析：{@code state} 正下方为灵魂方块时返回灵魂变体模型，否则原样返回。
	 * 该调用点可拿到渲染区域（BlockAndTintGetter）与 BlockPos，因此能按 y-1 方块动态判定。
	 */
	public static BlockStateModel resolve(BlockState state, BlockState below, BlockStateModel original) {
		Identifier id = SoulLightingHelper.resolve(state, below);
		if (id == null) {
			return original;
		}
		ModelManager modelManager = Minecraft.getInstance().getModelManager();
		BlockStateModelSet modelSet = modelManager.getBlockStateModelSet();
		if (id.equals(Identifier.withDefaultNamespace("block/soul_torch"))) {
			if (state.getBlock() == Blocks.WALL_TORCH) {
				BlockState soulWallTorch = Blocks.SOUL_WALL_TORCH.defaultBlockState()
					.setValue(WallTorchBlock.FACING, state.getValue(WallTorchBlock.FACING));
				return modelSet.get(soulWallTorch);
			}
			return modelSet.get(Blocks.SOUL_TORCH.defaultBlockState());
		}
		if (id.equals(Identifier.withDefaultNamespace("block/soul_lantern"))) {
			BlockState soulLantern = Blocks.SOUL_LANTERN.defaultBlockState()
				.setValue(LanternBlock.HANGING, state.getValue(LanternBlock.HANGING))
				.setValue(LanternBlock.WATERLOGGED, state.getValue(LanternBlock.WATERLOGGED));
			return modelSet.get(soulLantern);
		}
		// quirky:block/soul_candle[_lit]：包装原蜡烛模型做火焰纹理替换
		return new SoulCandleModel(original);
	}

	/** 灵魂火焰粒子 sprite（粒子图集中的 soul_fire_flame）。 */
	public static TextureAtlasSprite soulFlameSprite() {
		return Minecraft.getInstance().getAtlasManager()
			.get(new SpriteId(TextureAtlas.LOCATION_PARTICLES, Identifier.withDefaultNamespace("soul_fire_flame")));
	}

	/** 灵魂蜡烛火焰纹理 sprite（方块图集中的 quirky_soul_candle_flame）。 */
	public static TextureAtlasSprite soulCandleFlameSprite() {
		return Minecraft.getInstance().getAtlasManager()
			.get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, Identifier.fromNamespaceAndPath("quirky", "block/quirky_soul_candle_flame")));
	}
}
