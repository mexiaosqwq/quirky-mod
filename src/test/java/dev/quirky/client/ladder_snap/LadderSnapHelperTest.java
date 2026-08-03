package dev.quirky.client.ladder_snap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.quirky.TestBootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LadderSnapHelperTest {

	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	@Test
	void coverageExcludesScaffoldingButNotVinesOrLadders() {
		// 覆盖面（spec §5.9）：onClimbable() 语义（#minecraft:climbable ∪ 梯上同向开放活板门）
		// 自动爬，仅脚手架排除。
		// 块比较（非 tag）单测可验证；tag 分支（onClimbable）单测环境恒 false 不在此测。
		assertTrue(LadderSnapHelper.isExcluded(Blocks.SCAFFOLDING.defaultBlockState()));
		assertFalse(LadderSnapHelper.isExcluded(Blocks.LADDER.defaultBlockState()));
		assertFalse(LadderSnapHelper.isExcluded(Blocks.VINE.defaultBlockState()));
	}

	@Test
	void lookUpClimbsUp() {
		assertEquals(LadderSnapHelper.CLIMB_SPEED, LadderSnapHelper.climbVelocity(-45.0F, false), 1e-9);
		assertEquals(LadderSnapHelper.CLIMB_SPEED, LadderSnapHelper.climbVelocity(-16.0F, false), 1e-9);
	}

	@Test
	void lookDownSlidesDown() {
		assertEquals(-0.15, LadderSnapHelper.climbVelocity(45.0F, false), 1e-9);
		assertEquals(-0.15, LadderSnapHelper.climbVelocity(16.0F, false), 1e-9);
	}

	@Test
	void levelViewDoesNotClimb() {
		// 平视/轻仰只缓慢下滑（0.05 < 重力 0.08 → 净 −0.03），绝不自动上升
		assertEquals(LadderSnapHelper.HOVER_SPEED, LadderSnapHelper.climbVelocity(0.0F, false), 1e-9);
		assertEquals(LadderSnapHelper.HOVER_SPEED, LadderSnapHelper.climbVelocity(-10.0F, false), 1e-9);
		assertEquals(LadderSnapHelper.HOVER_SPEED, LadderSnapHelper.climbVelocity(10.0F, false), 1e-9);
	}

	@Test
	void manualInputTakesPriority() {
		// 按了 W/S/空格/Shift 时返回 NaN，不干预手动爬梯
		assertTrue(Double.isNaN(LadderSnapHelper.climbVelocity(-45.0F, true)));
	}
}
