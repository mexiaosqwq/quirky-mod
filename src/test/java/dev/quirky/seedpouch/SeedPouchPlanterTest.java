package dev.quirky.seedpouch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SeedPouchPlanterTest {

	private static final BlockPos CENTER = new BlockPos(0, 64, 0);

	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
		TestBootstrap.bindMinimalComponents(Items.WHEAT_SEEDS);
		TestBootstrap.bindMinimalComponents(Items.CARROT);
		TestBootstrap.bindMinimalComponents(Items.NETHER_WART);
		TestBootstrap.bindMinimalComponents(Items.SUGAR_CANE);
	}

	// ---- helpers -----------------------------------------------------------

	/** (2*radius+1)² 个可播种候选格（上方空气）。 */
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

	/** 土壤为指定状态的 mock 世界（光照充足、默认流体为空、默认方块为空气）。 */
	private static LevelReader levelWithSoil(BlockState soil) {
		LevelReader level = mock(LevelReader.class);
		when(level.getRawBrightness(any(BlockPos.class), anyInt())).thenReturn(15);
		when(level.getBlockState(any(BlockPos.class))).thenReturn(soil);
		when(level.getFluidState(any(BlockPos.class))).thenReturn(Fluids.EMPTY.defaultFluidState());
		return level;
	}

	/** 支持某方块 tag 的土壤 mock（测试环境 vanilla tag 为空，必须 mock）。 */
	private static BlockState soilSupporting(TagKey<Block> tag) {
		BlockState soil = mock(BlockState.class);
		when(soil.is(tag)).thenReturn(true);
		return soil;
	}

	private static List<ItemStack> inventory(ItemStack... stacks) {
		return new ArrayList<>(List.of(stacks));
	}

	private static void assertPlantedWheatAt(PlanResult result, int index, BlockPos groundPos, int slot) {
		PlanEntry entry = result.entries().get(index);
		assertEquals(groundPos, entry.pos());
		assertTrue(entry.cropState().is(Blocks.WHEAT), "expected wheat crop");
		assertEquals(slot, entry.inventorySlot());
	}

	// ---- tests -------------------------------------------------------------

	@Test
	void threeByThreeFarmlandPlantsAllNineCells() {
		LevelReader level = levelWithSoil(soilSupporting(BlockTags.SUPPORTS_CROPS));
		PlanResult result = SeedPouchPlanter.plan(
			level, square(1), inventory(new ItemStack(Items.WHEAT_SEEDS, 64)), false
		);
		assertEquals(9, result.entries().size());
		// 扫描顺序 dx 外圈 / dz 内圈：(-1,-1) (-1,0) (-1,1) (0,-1) …；全部从 0 号槽位取种
		for (int i = 0; i < 9; i++) {
			assertPlantedWheatAt(result, i, new BlockPos(-1 + i / 3, 64, -1 + i % 3), 0);
		}
	}

	@Test
	void insufficientSeedsPlantsOnlyAvailableCells() {
		LevelReader level = levelWithSoil(soilSupporting(BlockTags.SUPPORTS_CROPS));
		PlanResult result = SeedPouchPlanter.plan(
			level, square(1), inventory(new ItemStack(Items.WHEAT_SEEDS, 2)), false
		);
		assertEquals(2, result.entries().size());
	}

	@Test
	void infiniteSeedsPlantsFullAreaRegardlessOfCount() {
		LevelReader level = levelWithSoil(soilSupporting(BlockTags.SUPPORTS_CROPS));
		PlanResult result = SeedPouchPlanter.plan(
			level, square(1), inventory(new ItemStack(Items.WHEAT_SEEDS, 1)), true
		);
		assertEquals(9, result.entries().size());
	}

	@Test
	void mixedAreaAssignsMatchingCropsInInventoryOrder() {
		BlockPos farmlandPos = CENTER;
		BlockPos soulSandPos = CENTER.east();
		// 先建好土壤 mock，再注册到 level（避免在 thenReturn 里嵌套 stubbing）
		BlockState cropsSoil = soilSupporting(BlockTags.SUPPORTS_CROPS);
		BlockState wartSoil = soilSupporting(BlockTags.SUPPORTS_NETHER_WART);
		LevelReader level = levelWithSoil(cropsSoil);
		when(level.getBlockState(eq(soulSandPos))).thenReturn(wartSoil);
		when(level.getBlockState(eq(farmlandPos))).thenReturn(cropsSoil);
		// 顺序：先 wheat 后 nether wart —— 耕地格拿 wheat，灵魂沙格 wheat 无法存活再取 nether wart
		List<ItemStack> inventory = inventory(
			new ItemStack(Items.WHEAT_SEEDS, 64),
			new ItemStack(Items.NETHER_WART, 64)
		);
		List<BlockSnapshot> area = List.of(
			new BlockSnapshot(farmlandPos, true, Blocks.FARMLAND.defaultBlockState()),
			new BlockSnapshot(soulSandPos, true, Blocks.SOUL_SAND.defaultBlockState())
		);
		PlanResult result = SeedPouchPlanter.plan(level, area, inventory, false);

		assertEquals(2, result.entries().size());
		assertTrue(result.entries().get(0).cropState().is(Blocks.WHEAT));
		assertEquals(0, result.entries().get(0).inventorySlot());
		assertTrue(result.entries().get(1).cropState().is(Blocks.NETHER_WART));
		assertEquals(1, result.entries().get(1).inventorySlot());
	}

	@Test
	void sugarCaneNearWaterPlantsOnSand() {
		BlockPos sandPos = CENTER;
		BlockPos waterPos = sandPos.east();
		LevelReader level = levelWithSoil(soilSupporting(BlockTags.SUPPORTS_SUGAR_CANE));
		FluidState water = mock(FluidState.class);
		when(water.is(FluidTags.SUPPORTS_SUGAR_CANE_ADJACENTLY)).thenReturn(true);
		when(level.getFluidState(eq(waterPos))).thenReturn(water);

		PlanResult result = SeedPouchPlanter.plan(
			level, square(0), inventory(new ItemStack(Items.SUGAR_CANE, 64)), false
		);
		assertEquals(1, result.entries().size());
		assertTrue(result.entries().getFirst().cropState().is(Blocks.SUGAR_CANE));
	}

	@Test
	void sugarCaneFarFromWaterSkipsCell() {
		LevelReader level = levelWithSoil(soilSupporting(BlockTags.SUPPORTS_SUGAR_CANE));
		PlanResult result = SeedPouchPlanter.plan(
			level, square(0), inventory(new ItemStack(Items.SUGAR_CANE, 64)), false
		);
		assertTrue(result.isEmpty());
	}

	@Test
	void preciseRadiusScansOnlyCenterCell() {
		LevelReader level = levelWithSoil(Blocks.AIR.defaultBlockState());
		List<BlockSnapshot> cells = SeedPouchPlanter.scan(level, CENTER, 0);
		assertEquals(1, cells.size());
		assertEquals(CENTER, cells.getFirst().pos());
		assertTrue(cells.getFirst().replaceableAbove());
	}

	@Test
	void scanCollectsRadiusSquare() {
		LevelReader level = levelWithSoil(Blocks.AIR.defaultBlockState());
		assertEquals(9, SeedPouchPlanter.scan(level, CENTER, 1).size());
		assertEquals(25, SeedPouchPlanter.scan(level, CENTER, 2).size());
	}

	@Test
	void stoneAreaProducesEmptyPlan() {
		// 真实石头方块：测试环境 tag 为空 → SUPPORTS_CROPS 判定为 false → 无种子可存活
		LevelReader level = levelWithSoil(Blocks.STONE.defaultBlockState());
		PlanResult result = SeedPouchPlanter.plan(
			level, square(1), inventory(new ItemStack(Items.WHEAT_SEEDS, 64)), false
		);
		assertTrue(result.isEmpty());
	}

	@Test
	void nonReplaceableCellsAreSkipped() {
		LevelReader level = levelWithSoil(soilSupporting(BlockTags.SUPPORTS_CROPS));
		List<BlockSnapshot> area = List.of(
			new BlockSnapshot(CENTER, false, Blocks.FARMLAND.defaultBlockState()),
			new BlockSnapshot(CENTER.east(), true, Blocks.FARMLAND.defaultBlockState())
		);
		PlanResult result = SeedPouchPlanter.plan(
			level, area, inventory(new ItemStack(Items.WHEAT_SEEDS, 64)), false
		);
		assertEquals(1, result.entries().size());
		assertEquals(CENTER.east(), result.entries().getFirst().pos());
	}
}
