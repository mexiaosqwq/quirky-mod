package dev.quirky.boomerang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.quirky.TestBootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BoomerangBlockLogicTest {

	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	// ==== 可碎判定三分支 ====

	@Test
	void instantBreakBlockAlwaysBreaks() {
		// 破坏速度 0（树叶/高草/花/火把）：不参与 5% 摇骰，必碎
		assertEquals(BoomerangBlockLogic.BreakResult.MUST_BREAK, BoomerangBlockLogic.decideBreak(0.0F, false, false));
	}

	@Test
	void solidBlockRollsChance() {
		assertEquals(BoomerangBlockLogic.BreakResult.ROLL, BoomerangBlockLogic.decideBreak(2.0F, false, false));
	}

	@Test
	void immuneTagNeverBreaks() {
		// 免疫 tag（基岩/黑曜石等）永不打碎
		assertEquals(BoomerangBlockLogic.BreakResult.CANNOT_BREAK, BoomerangBlockLogic.decideBreak(0.0F, true, false));
		assertEquals(BoomerangBlockLogic.BreakResult.CANNOT_BREAK, BoomerangBlockLogic.decideBreak(50.0F, true, false));
	}

	@Test
	void adventureModeNeverBreaks() {
		// 冒险模式不打碎任何方块
		assertEquals(BoomerangBlockLogic.BreakResult.CANNOT_BREAK, BoomerangBlockLogic.decideBreak(0.0F, false, true));
		assertEquals(BoomerangBlockLogic.BreakResult.CANNOT_BREAK, BoomerangBlockLogic.decideBreak(2.0F, false, true));
	}

	@Test
	void unbreakableBlockNeverBreaks() {
		// destroySpeed < 0（如基岩 -1）：不可破坏
		assertEquals(BoomerangBlockLogic.BreakResult.CANNOT_BREAK, BoomerangBlockLogic.decideBreak(-1.0F, false, false));
	}

	// ==== 远程激活判定 ====

	@Test
	void bellAndButtonAreProjectileActivatable() {
		BlockState bell = Blocks.BELL.defaultBlockState();
		BlockState oakButton = Blocks.OAK_BUTTON.defaultBlockState();
		BlockState target = Blocks.TARGET.defaultBlockState();
		assertTrue(BoomerangBlockLogic.canActivate(bell));
		assertTrue(BoomerangBlockLogic.canActivate(oakButton));
		assertTrue(BoomerangBlockLogic.canActivate(target));
	}

	@Test
	void ordinaryBlocksAreNotActivatable() {
		assertFalse(BoomerangBlockLogic.canActivate(Blocks.STONE.defaultBlockState()));
		assertFalse(BoomerangBlockLogic.canActivate(Blocks.OAK_PLANKS.defaultBlockState()));
	}
}
