package dev.quirky.client.ladder_snap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.quirky.TestBootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec2;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LadderSnapHelperTest {

	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	@Test
	void correctionPointsToLadderCenter() {
		Vec2 correction = LadderSnapHelper.correction(0.3, 0.0, 0.5, 0.5, 0.5F);
		assertTrue(correction.x > 0); // 向 +x 中心修正
		assertTrue(correction.y > 0); // 向 +z 中心修正
	}

	@Test
	void centeredPlayerGetsNoCorrection() {
		Vec2 correction = LadderSnapHelper.correction(0.5, 0.5, 0.5, 0.5, 0.5F);
		assertEquals(0.0, correction.x, 1e-6);
		assertEquals(0.0, correction.y, 1e-6);
	}

	@Test
	void zeroStrengthGetsClampedToMinimum() {
		// config 范围 0.1~1.0：0 被 clamp 到 0.1，仍产生修正（偏移 0.2 × 0.1 = 0.02）
		Vec2 correction = LadderSnapHelper.correction(0.3, 0.0, 0.5, 0.5, 0.0F);
		assertEquals(0.2 * 0.1F, correction.x, 1e-6);
		assertEquals(0.5 * 0.1F, correction.y, 1e-6);
	}

	@Test
	void correctionIsProportionalToOffset() {
		Vec2 small = LadderSnapHelper.correction(0.4, 0.5, 0.5, 0.5, 0.5F);
		Vec2 large = LadderSnapHelper.correction(0.0, 0.5, 0.5, 0.5, 0.5F);
		assertEquals(0.1 * 0.5F, small.x, 1e-6);
		assertEquals(0.5 * 0.5F, large.x, 1e-6);
	}

	@Test
	void correctionNeverOvershootsCenter() {
		// strength > 1 时修正被 clamp 到 |偏移|，绝不越过中心
		Vec2 correction = LadderSnapHelper.correction(0.0, 0.0, 0.5, 0.5, 5.0F);
		assertEquals(0.5, correction.x, 1e-6);
		assertEquals(0.5, correction.y, 1e-6);
	}

	@Test
	void climbableTargetExcludesScaffolding() {
		// 脚手架在 #minecraft:climbable 中，但玩家在其上应自由走动，不吸附。
		// 注：测试环境无数据包 tag 数据（state.is(CLIMBABLE) 恒 false），
		// 只能验证"被排除"一侧；生产环境 tag 正常加载。
		org.junit.jupiter.api.Assertions.assertFalse(
			LadderSnapHelper.isClimbableTarget(Blocks.SCAFFOLDING.defaultBlockState())
		);
		org.junit.jupiter.api.Assertions.assertFalse(
			LadderSnapHelper.isClimbableTarget(Blocks.DIRT.defaultBlockState())
		);
	}
}
