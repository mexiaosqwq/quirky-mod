package dev.quirky.parrotegg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.quirky.TestBootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.parrot.Parrot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ParrotEggHatchLogicTest {

	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	@Test
	void rollAboveChanceNeverHatches() {
		RandomSource random = mock(RandomSource.class);
		when(random.nextFloat()).thenReturn(0.5F); // 0.5 >= 1/8 → 失败
		assertEquals(0, ParrotEggHatchLogic.hatchCount(random, ParrotEggHatchLogic.BASE_HATCH_CHANCE));
	}

	@Test
	void rollBelowChanceHatchesSingle() {
		RandomSource random = mock(RandomSource.class);
		// 第一掷 0.05 < 1/8 → 孵化；第二掷 0.9 >= 1/32 → 单只
		when(random.nextFloat()).thenReturn(0.05F, 0.9F);
		assertEquals(1, ParrotEggHatchLogic.hatchCount(random, ParrotEggHatchLogic.BASE_HATCH_CHANCE));
	}

	@Test
	void twinRollTriggersTwins() {
		RandomSource random = mock(RandomSource.class);
		// 第一掷 0.05 < 1/8 → 孵化；第二掷 0.01 < 1/32 → 双胞胎
		when(random.nextFloat()).thenReturn(0.05F, 0.01F);
		assertEquals(2, ParrotEggHatchLogic.hatchCount(random, ParrotEggHatchLogic.BASE_HATCH_CHANCE));
	}

	@Test
	void hatchBoundaryRollFails() {
		RandomSource random = mock(RandomSource.class);
		when(random.nextFloat()).thenReturn(1.0F / 8.0F); // 恰好等于基准 → 不孵化（>= 判定）
		assertEquals(0, ParrotEggHatchLogic.hatchCount(random, ParrotEggHatchLogic.BASE_HATCH_CHANCE));
	}

	@Test
	void jungleBoostDoublesBaseChance() {
		assertEquals(0.25F, ParrotEggHatchLogic.jungleBoost(), 1.0E-6F);
	}

	@Test
	void higherChanceHatchesWhereBaseFails() {
		// 丛林概率 25%：0.2 < 0.25 → 孵化；基础 12.5%：0.2 >= 0.125 → 失败
		RandomSource baseRoll = mock(RandomSource.class);
		when(baseRoll.nextFloat()).thenReturn(0.2F);
		assertEquals(0, ParrotEggHatchLogic.hatchCount(baseRoll, ParrotEggHatchLogic.BASE_HATCH_CHANCE));

		RandomSource jungleRoll = mock(RandomSource.class);
		when(jungleRoll.nextFloat()).thenReturn(0.2F, 0.9F); // 0.2 < 0.25 孵化；0.9 >= 1/32 单只
		assertEquals(1, ParrotEggHatchLogic.hatchCount(jungleRoll, ParrotEggHatchLogic.jungleBoost()));
	}

	@Test
	void shellColorDistinctPerVariant() {
		int redBlue = ParrotEggHatchLogic.shellColor(Parrot.Variant.RED_BLUE);
		int blue = ParrotEggHatchLogic.shellColor(Parrot.Variant.BLUE);
		assertNotEquals(redBlue, blue);
		assertEquals(redBlue, ParrotEggHatchLogic.shellColor(Parrot.Variant.RED_BLUE));
	}

	@Test
	void randomShellColorStaysInVariantPalette() {
		RandomSource random = mock(RandomSource.class);
		when(random.nextInt(5)).thenReturn(0);
		assertEquals(ParrotEggHatchLogic.shellColor(Parrot.Variant.RED_BLUE), ParrotEggHatchLogic.randomShellColor(random));
	}
}
