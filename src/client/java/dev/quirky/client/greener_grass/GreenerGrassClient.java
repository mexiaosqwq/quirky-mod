package dev.quirky.client.greener_grass;

import java.util.List;
import java.util.Set;

import dev.quirky.client_color.GrassColorMatrix;
import dev.quirky.config.QuirkyConfigHolder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * 草地增绿（对齐 Quark GreenerGrass）：
 * 在首次客户端 tick 后注册包装 tint source —— 先让其他 mod 完成各自的注册，
 * 再读回原 tint source 包装卷积，避免覆盖别人的注册。
 * <p>
 * 26.2 颜色注册走 {@link BlockColorRegistry}（BlockTintSource 列表，注册即整体替换），
 * 因此包装前必须先取回 {@link Minecraft#getBlockColors()} 里该方块当前的 tint source。
 */
public final class GreenerGrassClient {
	/** 始终作用的草地系方块 */
	private static final List<Block> GRASS_BLOCKS = List.of(
		Blocks.GRASS_BLOCK,
		Blocks.SHORT_GRASS,
		Blocks.FERN,
		Blocks.LARGE_FERN,
		Blocks.SUGAR_CANE,
		Blocks.POTTED_FERN
	);

	/** 仅当 grassAffectLeaves 开启时作用的树叶/藤蔓 */
	private static final List<Block> LEAF_BLOCKS = List.of(
		Blocks.OAK_LEAVES,
		Blocks.SPRUCE_LEAVES,
		Blocks.BIRCH_LEAVES,
		Blocks.JUNGLE_LEAVES,
		Blocks.ACACIA_LEAVES,
		Blocks.DARK_OAK_LEAVES,
		Blocks.MANGROVE_LEAVES,
		Blocks.CHERRY_LEAVES,
		Blocks.PALE_OAK_LEAVES,
		Blocks.VINE
	);

	private static boolean registered;
	/** 上次注册时的 BlockColors 实例：资源重载/换世界后实例变化，需重注册（review B3） */
	private static Object registeredBlockColors;

	/** 强度变化时才重建矩阵，避免热路径逐像素分配 */
	private static float lastMultiplier = Float.NaN;
	private static GrassColorMatrix currentMatrix = new GrassColorMatrix(1.0F);

	private GreenerGrassClient() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level == null) {
				return;
			}
			Object colors = client.getBlockColors();
			if (registered && colors == registeredBlockColors) {
				return;
			}
			// 首帧注册（等其他 mod 注册完）+ 资源重载/重进世界后 BlockColors 实例变化时重注册
			registered = true;
			registeredBlockColors = colors;
			registerTints();
		});
	}

	private static void registerTints() {
		for (Block block : GRASS_BLOCKS) {
			registerWrapped(block);
		}
		// 树叶/藤蔓也总是注册包装，开关在取色时判断（S1：游戏内热切换，无需重进世界）
		for (Block block : LEAF_BLOCKS) {
			registerWrapped(block);
		}
	}

	/**
	 * 读回方块当前的 tint sources（含其他 mod 注册的），逐个包装后重新注册。
	 * 无 tint source 的方块（26.2 樱花/苍白橡木树叶本身不着色）直接跳过。
	 */
	private static void registerWrapped(Block block) {
		List<BlockTintSource> originals = Minecraft.getInstance().getBlockColors().getTintSources(block.defaultBlockState());
		if (originals.isEmpty()) {
			return;
		}
		List<BlockTintSource> wrapped = originals.stream()
			.<BlockTintSource>map(delegate -> new GrassTintSourceWrapper(delegate, block))
			.toList();
		BlockColorRegistry.register(wrapped, block);
	}

	/** 包装原 tint source：取色后按配置开关与强度做颜色矩阵卷积。 */
	private static final class GrassTintSourceWrapper implements BlockTintSource {
		private final BlockTintSource delegate;
		/** 所属方块：树叶/藤蔓在 grassAffectLeaves 关闭时不过卷积 */
		private final Block block;

		private GrassTintSourceWrapper(BlockTintSource delegate, Block block) {
			this.delegate = delegate;
			this.block = block;
		}

		@Override
		public int color(BlockState state) {
			return convolveIfEnabled(this.delegate.color(state));
		}

		@Override
		public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
			return convolveIfEnabled(this.delegate.colorInWorld(state, level, pos));
		}

		@Override
		public int colorAsTerrainParticle(BlockState state, BlockAndTintGetter level, BlockPos pos) {
			return convolveIfEnabled(this.delegate.colorAsTerrainParticle(state, level, pos));
		}

		@Override
		public Set<Property<?>> relevantProperties() {
			return this.delegate.relevantProperties();
		}

		private int convolveIfEnabled(int color) {
			// -1 是"不着色"哨兵（如草方块碎裂粒子、甘蔗手中渲染），保持原样
			if (color == -1 || !QuirkyConfigHolder.get().greenerGrass) {
				return color;
			}
			if (LEAF_BLOCKS.contains(this.block) && !QuirkyConfigHolder.get().grassAffectLeaves) {
				return color;
			}
			return matrix().convolve(color);
		}

		private static GrassColorMatrix matrix() {
			float multiplier = QuirkyConfigHolder.get().grassMultiplier;
			if (multiplier != lastMultiplier) {
				currentMatrix = new GrassColorMatrix(multiplier);
				lastMultiplier = multiplier;
			}
			return currentMatrix;
		}
	}
}
