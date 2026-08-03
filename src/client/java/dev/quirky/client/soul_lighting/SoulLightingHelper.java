package dev.quirky.client.soul_lighting;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 灵魂光源判定（纯逻辑，可单测）。
 *
 * <p>规则：光源方块（火把/墙上火把/蜡烛）正下方（y-1）为灵魂沙/灵魂土时，
 * 火焰粒子替换为灵魂青色（soul_fire_flame）。灯笼无原版火焰粒子且 26.2 模型贴图替换不可靠，不在覆盖面。
 * 无状态存储：每次粒子生成动态按下方方块判定，破坏/移动后自动恢复原版外观。
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
	 * 是否为可被灵魂化的光源方块（火把/墙上火把/蜡烛——均有原版火焰粒子）。
	 * 灯笼无原版火焰粒子、且 26.2 模型贴图替换受区块编译缓存限制无法实现，不在覆盖面。
	 * 灵魂火把/灵魂灯笼本身已是灵魂变体，不在此列；
	 * 熔炉/刷怪笼等也会产生 FLAME 粒子的方块不在此列（见 FlameParticleMixin）。
	 */
	public static boolean isLightSource(BlockState state) {
		Block block = state.getBlock();
		return block == Blocks.TORCH || block == Blocks.WALL_TORCH || block instanceof CandleBlock;
	}
}
