package dev.quirky.client.soul_lighting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.quirky.TestBootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SoulLightingHelperTest {
	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	@Test
	void torchOnSoulSandResolvesToSoulTorch() {
		assertEquals(
			Identifier.withDefaultNamespace("block/soul_torch"),
			SoulLightingHelper.resolve(Blocks.TORCH.defaultBlockState(), Blocks.SOUL_SAND.defaultBlockState())
		);
	}

	@Test
	void wallTorchOnSoulSoilResolvesToSoulTorch() {
		assertEquals(
			Identifier.withDefaultNamespace("block/soul_torch"),
			SoulLightingHelper.resolve(Blocks.WALL_TORCH.defaultBlockState(), Blocks.SOUL_SOIL.defaultBlockState())
		);
	}

	@Test
	void lanternOnSoulSandResolvesToSoulLantern() {
		assertEquals(
			Identifier.withDefaultNamespace("block/soul_lantern"),
			SoulLightingHelper.resolve(Blocks.LANTERN.defaultBlockState(), Blocks.SOUL_SAND.defaultBlockState())
		);
	}

	@Test
	void torchOnDirtResolvesToNull() {
		assertNull(SoulLightingHelper.resolve(Blocks.TORCH.defaultBlockState(), Blocks.DIRT.defaultBlockState()));
	}

	@Test
	void candleOnSoulSoilResolvesToSoulCandle() {
		assertEquals(
			Identifier.fromNamespaceAndPath("quirky", "block/soul_candle"),
			SoulLightingHelper.resolve(Blocks.CANDLE.defaultBlockState(), Blocks.SOUL_SOIL.defaultBlockState())
		);
	}

	@Test
	void litCandleOnSoulSoilResolvesToSoulCandleLit() {
		BlockState lit = Blocks.CANDLE.defaultBlockState().setValue(AbstractCandleBlock.LIT, true);
		assertEquals(
			Identifier.fromNamespaceAndPath("quirky", "block/soul_candle_lit"),
			SoulLightingHelper.resolve(lit, Blocks.SOUL_SOIL.defaultBlockState())
		);
	}

	@Test
	void coloredCandleOnSoulSandResolves() {
		BlockState redCandle = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("red_candle")).defaultBlockState();
		assertNotNull(SoulLightingHelper.resolve(redCandle, Blocks.SOUL_SAND.defaultBlockState()));
	}

	@Test
	void candleOnDirtResolvesToNull() {
		assertNull(SoulLightingHelper.resolve(Blocks.CANDLE.defaultBlockState(), Blocks.DIRT.defaultBlockState()));
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
		assertTrue(SoulLightingHelper.isLightSource(Blocks.LANTERN.defaultBlockState()));
		assertTrue(SoulLightingHelper.isLightSource(Blocks.CANDLE.defaultBlockState()));
		// 灵魂变体本身不再是可被替换的光源
		assertFalse(SoulLightingHelper.isLightSource(Blocks.SOUL_TORCH.defaultBlockState()));
		assertFalse(SoulLightingHelper.isLightSource(Blocks.SOUL_LANTERN.defaultBlockState()));
		// 产生 FLAME 粒子的非光源方块（熔炉等）不得被灵魂化
		assertFalse(SoulLightingHelper.isLightSource(Blocks.FURNACE.defaultBlockState()));
		assertFalse(SoulLightingHelper.isLightSource(Blocks.DIRT.defaultBlockState()));
	}

	@Test
	void candleLitSpriteMapsToSoulFlameTexture() {
		assertEquals(
			Identifier.fromNamespaceAndPath("quirky", "block/quirky_soul_candle_flame"),
			SoulLightingHelper.soulTextureFor(Identifier.withDefaultNamespace("block/red_candle_lit"))
		);
		assertEquals(
			Identifier.fromNamespaceAndPath("quirky", "block/quirky_soul_candle_flame"),
			SoulLightingHelper.soulTextureFor(Identifier.withDefaultNamespace("block/candle_lit"))
		);
		assertNull(SoulLightingHelper.soulTextureFor(Identifier.withDefaultNamespace("block/red_candle")));
	}
}
