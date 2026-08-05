package dev.quirky.rope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.quirky.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

class RopeSupportLogicTest {

	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	// ==== 支撑判定（四类挂点）====

	@Test
	void fullSolidBlockAboveSupportsRope() {
		assertTrue(RopeSupportLogic.isSupported(true, false, false));
	}

	@Test
	void fenceOrWallAboveSupportsRope() {
		assertTrue(RopeSupportLogic.isSupported(false, true, false));
	}

	@Test
	void anotherRopeSegmentAboveSupportsRope() {
		assertTrue(RopeSupportLogic.isSupported(false, false, true));
	}

	@Test
	void openAirAboveDoesNotSupportRope() {
		assertFalse(RopeSupportLogic.isSupported(false, false, false));
	}

	// ==== 贴墙段支撑判定（背后墙）====

	@Test
	void solidWallBehindSupportsWallRope() {
		assertTrue(RopeSupportLogic.isBacked(false, false, false));
	}

	@Test
	void airBehindDoesNotSupportWallRope() {
		assertFalse(RopeSupportLogic.isBacked(true, false, false));
	}

	@Test
	void fluidBehindDoesNotSupportWallRope() {
		assertFalse(RopeSupportLogic.isBacked(false, true, false));
	}

	@Test
	void ropeBehindDoesNotSupportWallRope() {
		assertFalse(RopeSupportLogic.isBacked(false, false, true));
	}

	// ==== 连锁掉落段计算 ====

	@Test
	void topSegmentUnsupportedDropsWholeColumnBottomUp() {
		// 柱顶失去支撑：整列自下而上掉落
		List<BlockPos> column = List.of(new BlockPos(0, 5, 0), new BlockPos(0, 4, 0), new BlockPos(0, 3, 0), new BlockPos(0, 2, 0));
		// 只有柱顶（y=5）上方的 y=6 不是绳/支撑 → 柱顶不受支撑
		List<BlockPos> falling = RopeSupportLogic.fallingSegments(column, pos -> pos.getY() != 5);
		assertEquals(List.of(new BlockPos(0, 2, 0), new BlockPos(0, 3, 0), new BlockPos(0, 4, 0), new BlockPos(0, 5, 0)), falling);
	}

	@Test
	void middleSegmentUnsupportedDropsOnlySegmentsBelow() {
		// 中间段断掉：其上方的列仍挂着（各自受支撑），下方段连锁掉落
		List<BlockPos> column = List.of(new BlockPos(0, 5, 0), new BlockPos(0, 4, 0), new BlockPos(0, 3, 0), new BlockPos(0, 2, 0));
		// y=5、y=4 受支撑（上方是支撑/绳），y=3 上方是空气（断点），y=2 下方段随之掉落
		List<BlockPos> falling = RopeSupportLogic.fallingSegments(column, pos -> pos.getY() >= 4);
		assertEquals(List.of(new BlockPos(0, 2, 0), new BlockPos(0, 3, 0)), falling);
	}

	@Test
	void fullySupportedColumnDoesNotFall() {
		List<BlockPos> column = List.of(new BlockPos(0, 5, 0), new BlockPos(0, 4, 0), new BlockPos(0, 3, 0));
		List<BlockPos> falling = RopeSupportLogic.fallingSegments(column, pos -> true);
		assertTrue(falling.isEmpty());
	}

	// ==== 批量铺设停止 ====

	@Test
	void batchExtendStopsAtSolidBlock() {
		List<BlockState> candidates = List.of(
			Blocks.AIR.defaultBlockState(),
			Blocks.AIR.defaultBlockState(),
			Blocks.STONE.defaultBlockState(),
			Blocks.AIR.defaultBlockState()
		);
		assertEquals(2, RopeSupportLogic.extendStop(candidates, 32, 32));
	}

	@Test
	void batchExtendCanPlaceIntoWater() {
		// 水是允许延伸的（绳可含水），撞水不停止
		List<BlockState> candidates = List.of(
			Blocks.AIR.defaultBlockState(),
			Blocks.WATER.defaultBlockState(),
			Blocks.AIR.defaultBlockState()
		);
		assertEquals(3, RopeSupportLogic.extendStop(candidates, 32, 32));
	}

	@Test
	void batchExtendStopsWhenHandRunsOut() {
		List<BlockState> candidates = List.of(
			Blocks.AIR.defaultBlockState(),
			Blocks.AIR.defaultBlockState(),
			Blocks.AIR.defaultBlockState()
		);
		assertEquals(2, RopeSupportLogic.extendStop(candidates, 32, 2));
	}

	@Test
	void batchExtendStopsAtHardCap() {
		List<BlockState> candidates = List.of(
			Blocks.AIR.defaultBlockState(),
			Blocks.AIR.defaultBlockState(),
			Blocks.AIR.defaultBlockState(),
			Blocks.AIR.defaultBlockState()
		);
		assertEquals(3, RopeSupportLogic.extendStop(candidates, 3, 32));
	}

	// ==== 可放置判定 ====

	@Test
	void airAndWaterArePlaceable() {
		assertTrue(RopeSupportLogic.isPlaceable(Blocks.AIR.defaultBlockState()));
		assertTrue(RopeSupportLogic.isPlaceable(Blocks.WATER.defaultBlockState()));
	}

	@Test
	void solidBlockIsNotPlaceable() {
		assertFalse(RopeSupportLogic.isPlaceable(Blocks.STONE.defaultBlockState()));
		assertFalse(RopeSupportLogic.isPlaceable(Blocks.OAK_SLAB.defaultBlockState()));
	}

	// ==== isSupportedAt 生产路径：非绳方块不得崩溃（2026-08-05 修复回归）====

	@Test
	void airBlockBelowSolidAboveIsSupportedWithoutCrash() {
		// 回归：点击实心方块时 target=其下方空气，曾因对空气 getValue(WALL) 抛 IllegalArgumentException
		Level level = mock(Level.class);
		BlockPos pos = new BlockPos(10, 64, 10);
		when(level.getBlockState(pos)).thenReturn(Blocks.AIR.defaultBlockState());
		when(level.getBlockState(pos.above())).thenReturn(Blocks.STONE.defaultBlockState());
		assertTrue(RopeSupportLogic.isSupportedAt(level, pos));
	}

	@Test
	void airAboveAirIsNotSupportedWithoutCrash() {
		Level level = mock(Level.class);
		BlockPos pos = new BlockPos(10, 64, 10);
		when(level.getBlockState(pos)).thenReturn(Blocks.AIR.defaultBlockState());
		when(level.getBlockState(pos.above())).thenReturn(Blocks.AIR.defaultBlockState());
		assertFalse(RopeSupportLogic.isSupportedAt(level, pos));
	}

	@Test
	void stoneBlockItselfDoesNotCrashIsSupportedAt() {
		// 悬空石头（上方空气）：不崩溃，按上方支撑判定返回 false
		Level level = mock(Level.class);
		BlockPos pos = new BlockPos(10, 64, 10);
		when(level.getBlockState(pos)).thenReturn(Blocks.STONE.defaultBlockState());
		when(level.getBlockState(pos.above())).thenReturn(Blocks.AIR.defaultBlockState());
		assertFalse(RopeSupportLogic.isSupportedAt(level, pos));
	}
}
