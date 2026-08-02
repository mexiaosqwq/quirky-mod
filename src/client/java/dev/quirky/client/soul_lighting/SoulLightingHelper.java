package dev.quirky.client.soul_lighting;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * 灵魂光源判定（纯逻辑，可单测）。
 *
 * <p>规则：光源方块（火把/墙上火把/灯笼/蜡烛）正下方（y-1）为灵魂沙/灵魂土时，渲染为灵魂变体。
 * 无状态存储：每次渲染动态按下方方块判定，破坏/移动后自动恢复原版外观。
 */
public final class SoulLightingHelper {
	private SoulLightingHelper() {
	}

	/** 下方方块是否为灵魂方块（灵魂沙/灵魂土）。 */
	public static boolean isSoulBlock(BlockState below) {
		Block block = below.getBlock();
		return block == Blocks.SOUL_SAND || block == Blocks.SOUL_SOIL;
	}

	/**
	 * 是否为可被灵魂化的光源方块（火把/墙上火把/灯笼/蜡烛）。
	 * 灵魂火把/灵魂灯笼本身已是灵魂变体，不在此列；
	 * 熔炉/刷怪笼等也会产生 FLAME 粒子的方块不在此列（见 FlameParticleMixin）。
	 */
	public static boolean isLightSource(BlockState state) {
		Block block = state.getBlock();
		return block == Blocks.TORCH || block == Blocks.WALL_TORCH || block == Blocks.LANTERN || block instanceof CandleBlock;
	}

	/**
	 * 解析灵魂变体模型 id。
	 *
	 * @return 下方为灵魂方块且光源匹配时返回灵魂模型 id
	 *         （火把/墙上火把→{@code minecraft:block/soul_torch}、灯笼→{@code minecraft:block/soul_lantern}、
	 *         蜡烛（含 16 色）→{@code quirky:block/soul_candle}（点燃→{@code soul_candle_lit}））；
	 *         否则返回 {@code null}（渲染原版模型）。
	 */
	@Nullable
	public static Identifier resolve(BlockState state, BlockState below) {
		if (!isSoulBlock(below) || !isLightSource(state)) {
			return null;
		}
		Block block = state.getBlock();
		if (block == Blocks.TORCH || block == Blocks.WALL_TORCH) {
			return Identifier.withDefaultNamespace("block/soul_torch");
		}
		if (block == Blocks.LANTERN) {
			return Identifier.withDefaultNamespace("block/soul_lantern");
		}
		boolean lit = state.getValue(AbstractCandleBlock.LIT);
		return Identifier.fromNamespaceAndPath("quirky", lit ? "block/soul_candle_lit" : "block/soul_candle");
	}

	/**
	 * 蜡烛 lit 纹理 id（{@code minecraft:block/<color>_candle_lit}）→ 灵魂火焰纹理 id
	 * （{@code quirky:block/quirky_soul_candle_flame}）；非蜡烛纹理返回 {@code null}。
	 */
	@Nullable
	public static Identifier soulTextureFor(Identifier sprite) {
		String path = sprite.getPath();
		if (path.startsWith("block/") && (path.equals("block/candle_lit") || path.endsWith("_candle_lit"))) {
			return Identifier.fromNamespaceAndPath("quirky", "block/quirky_soul_candle_flame");
		}
		return null;
	}
}
