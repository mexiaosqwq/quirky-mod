package dev.quirky.whistle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.function.Predicate;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;

/**
 * 口哨纯逻辑测试：owner 匹配谓词、半径边界、幻翼数量范围（注入种子随机源）。
 */
class WhistleLogicTest {

	@Test
	void ownedByMatchesOwnerOnly() {
		Player owner = mock(Player.class);
		Player stranger = mock(Player.class);
		TamableAnimal mine = mock(TamableAnimal.class);
		TamableAnimal theirs = mock(TamableAnimal.class);
		when(mine.isOwnedBy(owner)).thenReturn(true);
		when(theirs.isOwnedBy(owner)).thenReturn(false);

		Predicate<TamableAnimal> predicate = WhistleLogic.ownedBy(owner);
		assertTrue(predicate.test(mine));
		assertFalse(predicate.test(theirs));
	}

	@Test
	void withinRadiusIncludesBoundary() {
		assertTrue(WhistleLogic.withinRadius(2.0, 0.0, 0.0, 2.0));
		assertTrue(WhistleLogic.withinRadius(1.0, 1.0, 1.0, 2.0));
		assertFalse(WhistleLogic.withinRadius(2.0, 0.0, 0.0, 1.9));
		assertFalse(WhistleLogic.withinRadius(24.1, 0.0, 0.0, 24.0));
	}

	@Test
	void selectPhantomsZeroWhenNoneAvailable() {
		assertEquals(0, WhistleLogic.selectPhantoms(0, 3, RandomSource.create(1L)));
		assertEquals(0, WhistleLogic.selectPhantoms(-1, 3, RandomSource.create(1L)));
	}

	@Test
	void selectPhantomsZeroWhenMaxCountZero() {
		assertEquals(0, WhistleLogic.selectPhantoms(10, 0, RandomSource.create(1L)));
	}

	@Test
	void selectPhantomsStaysInOneToMaxRange() {
		for (long seed = 0; seed < 200; seed++) {
			int count = WhistleLogic.selectPhantoms(10, 3, RandomSource.create(seed));
			assertTrue(count >= 1 && count <= 3, "count=" + count + " seed=" + seed);
		}
	}

	@Test
	void selectPhantomsCapsAtAvailable() {
		for (long seed = 0; seed < 200; seed++) {
			int count = WhistleLogic.selectPhantoms(2, 5, RandomSource.create(seed));
			assertTrue(count >= 1 && count <= 2, "count=" + count + " seed=" + seed);
		}
	}

	@Test
	void selectPhantomsCoversFullRangeAcrossSeeds() {
		boolean sawOne = false;
		boolean sawThree = false;
		for (long seed = 0; seed < 500; seed++) {
			int count = WhistleLogic.selectPhantoms(5, 3, RandomSource.create(seed));
			sawOne |= count == 1;
			sawThree |= count == 3;
		}
		assertTrue(sawOne && sawThree, "expected both extremes over 500 seeds");
	}
}
