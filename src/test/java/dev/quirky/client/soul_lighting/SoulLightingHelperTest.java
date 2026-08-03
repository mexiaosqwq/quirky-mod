package dev.quirky.client.soul_lighting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.quirky.TestBootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 灵魂光源判定（粒子替换用）：下方方块判定 + 光源方块判定。
 * 模型贴图替换已放弃（见 spec §5.6），无 resolve 相关测试。
 */
class SoulLightingHelperTest {
	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	@Test
	void soulBlockCheck() {
		assertTrue(SoulLightingHelper.isSoulBlock(Blocks.SOUL_SAND.defaultBlockState()));
		assertTrue(SoulLightingHelper.isSoulBlock(Blocks.SOUL_SOIL.defaultBlockState()));
		assertFalse(SoulLightingHelper.isSoulBlock(Blocks.DIRT.defaultBlockState()));
	}

	@Test
	void lightSourceCheck() {
		assertTrue(SoulLightingHelper.isLightSource(Blocks.TORCH.defaultBlockState()));
		assertTrue(SoulLightingHelper.isLightSource(Blocks.WALL_TORCH.defaultBlockState()));
		assertTrue(SoulLightingHelper.isLightSource(Blocks.CANDLE.defaultBlockState()));
		// 灯笼无原版火焰粒子、且 26.2 模型贴图替换不可靠 → 不在覆盖面
		assertFalse(SoulLightingHelper.isLightSource(Blocks.LANTERN.defaultBlockState()));
		// 灵魂变体本身不再是可被替换的光源
		assertFalse(SoulLightingHelper.isLightSource(Blocks.SOUL_TORCH.defaultBlockState()));
		assertFalse(SoulLightingHelper.isLightSource(Blocks.SOUL_LANTERN.defaultBlockState()));
		// 产生 FLAME 粒子的非光源方块（熔炉等）不得被灵魂化
		assertFalse(SoulLightingHelper.isLightSource(Blocks.FURNACE.defaultBlockState()));
		assertFalse(SoulLightingHelper.isLightSource(Blocks.DIRT.defaultBlockState()));
	}
}
