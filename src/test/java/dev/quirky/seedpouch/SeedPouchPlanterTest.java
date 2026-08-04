package dev.quirky.seedpouch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import dev.quirky.TestBootstrap;
import dev.quirky.seedpouch.SeedPouchPlanter.BlockSnapshot;
import dev.quirky.seedpouch.SeedPouchPlanter.PlanEntry;
import dev.quirky.seedpouch.SeedPouchPlanter.PlanResult;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SeedPouchPlanterTest {
	private static final BlockPos CENTER = new BlockPos(0, 64, 0);

	@BeforeAll
	static void boot() {
		TestBootstrap.boot();
		TestBootstrap.bindMinimalComponents(Items.WHEAT_SEEDS);
		TestBootstrap.bindMinimalComponents(Items.NETHER_WART);
		TestBootstrap.bindMinimalComponents(Items.SUGAR_CANE);
	}

	private static List<BlockSnapshot> square(int radius) {
		List<BlockSnapshot> cells = new ArrayList<>();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				BlockPos pos = new BlockPos(CENTER.getX() + dx, CENTER.getY(), CENTER.getZ() + dz);
				cells.add(new BlockSnapshot(pos, true, Blocks.FARMLAND.defaultBlockState()));
			}
		}
		return cells;
	}

	private static LevelReader levelWithSoil(BlockState soil) {
		LevelReader level = mock(LevelReader.class);
		when(level.getRawBrightness(any(BlockPos.class), anyInt())).thenReturn(15);
		when(level.getBlockState(any(BlockPos.class))).thenReturn(soil);
		when(level.getFluidState(any(BlockPos.class))).thenReturn(Fluids.EMPTY.defaultFluidState());
		return level;
	}

	private static BlockState soilSupporting(net.minecraft.tags.TagKey<Block> tag) {
		BlockState soil = mock(BlockState.class);
		when(soil.is(tag)).thenReturn(true);
		return soil;
	}

	@Test
	void pouchPlantsAllCellsFromBagItems() {
		LevelReader level = levelWithSoil(soilSupporting(BlockTags.SUPPORTS_CROPS));
		// 袋内只有小麦种子（来源是袋子，不是背包）
		List<ItemStack> pouch = List.of(new ItemStack(Items.WHEAT_SEEDS, 64));
		PlanResult result = SeedPouchPlanter.plan(level, square(1), pouch, false);
		assertEquals(9, result.entries().size());
		for (int i = 0; i < 9; i++) {
			PlanEntry e = result.entries().get(i);
			assertTrue(e.cropState().is(Blocks.WHEAT), "expected wheat");
			assertEquals(0, e.pouchIndex());
		}
	}

	@Test
	void insufficientSeedsPlantsOnlyAvailable() {
		LevelReader level = levelWithSoil(soilSupporting(BlockTags.SUPPORTS_CROPS));
		List<ItemStack> pouch = List.of(new ItemStack(Items.WHEAT_SEEDS, 2));
		assertEquals(2, SeedPouchPlanter.plan(level, square(1), pouch, false).entries().size());
	}

	@Test
	void infiniteSeedsPlantsFullArea() {
		LevelReader level = levelWithSoil(soilSupporting(BlockTags.SUPPORTS_CROPS));
		List<ItemStack> pouch = List.of(new ItemStack(Items.WHEAT_SEEDS, 1));
		assertEquals(9, SeedPouchPlanter.plan(level, square(1), pouch, true).entries().size());
	}

	@Test
	void mixedBagSplitsByCanSurvive() {
		BlockPos farmland = CENTER;
		BlockPos soulSand = CENTER.east();
		BlockState cropsSoil = soilSupporting(BlockTags.SUPPORTS_CROPS);
		BlockState wartSoil = soilSupporting(BlockTags.SUPPORTS_NETHER_WART);
		LevelReader level = levelWithSoil(cropsSoil);
		when(level.getBlockState(soulSand)).thenReturn(wartSoil);
		when(level.getBlockState(farmland)).thenReturn(cropsSoil);
		List<ItemStack> pouch = List.of(
			new ItemStack(Items.WHEAT_SEEDS, 64),
			new ItemStack(Items.NETHER_WART, 64)
		);
		List<BlockSnapshot> area = List.of(
			new BlockSnapshot(farmland, true, Blocks.FARMLAND.defaultBlockState()),
			new BlockSnapshot(soulSand, true, Blocks.SOUL_SAND.defaultBlockState())
		);
		PlanResult result = SeedPouchPlanter.plan(level, area, pouch, false);
		assertEquals(2, result.entries().size());
		assertTrue(result.entries().get(0).cropState().is(Blocks.WHEAT));
		assertEquals(0, result.entries().get(0).pouchIndex());
		assertTrue(result.entries().get(1).cropState().is(Blocks.NETHER_WART));
		assertEquals(1, result.entries().get(1).pouchIndex());
	}

	@Test
	void emptyBagProducesEmptyPlan() {
		LevelReader level = levelWithSoil(soilSupporting(BlockTags.SUPPORTS_CROPS));
		assertTrue(SeedPouchPlanter.plan(level, square(1), List.of(), false).isEmpty());
	}

	@Test
	void consumeOneDecrementsAndKeepsEntry() {
		List<ItemStack> pouch = new ArrayList<>(List.of(new ItemStack(Items.WHEAT_SEEDS, 5)));
		List<ItemStack> after = SeedPouchPlanter.consumeOne(pouch, 0);
		assertEquals(1, after.size());
		assertEquals(4, after.get(0).getCount());
	}

	@Test
	void consumeOneRemovesEntryWhenZero() {
		List<ItemStack> pouch = new ArrayList<>(List.of(new ItemStack(Items.WHEAT_SEEDS, 1)));
		List<ItemStack> after = SeedPouchPlanter.consumeOne(pouch, 0);
		assertTrue(after.isEmpty());
	}

	@Test
	void scanCollectsRadiusSquare() {
		LevelReader level = levelWithSoil(Blocks.AIR.defaultBlockState());
		assertEquals(1, SeedPouchPlanter.scan(level, CENTER, 0).size());
		assertEquals(9, SeedPouchPlanter.scan(level, CENTER, 1).size());
		assertEquals(25, SeedPouchPlanter.scan(level, CENTER, 2).size());
	}
}
