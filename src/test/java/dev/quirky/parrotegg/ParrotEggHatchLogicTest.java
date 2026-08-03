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
	void zeroChanceNeverHatches() {
		RandomSource random = mock(RandomSource.class);
		when(random.nextFloat()).thenReturn(0.5F);
		assertEquals(0, ParrotEggHatchLogic.hatchCount(random, 0.0F, 0.03F));
	}

	@Test
	void fullChanceAlwaysHatchesSingle() {
		RandomSource random = mock(RandomSource.class);
		when(random.nextFloat()).thenReturn(0.5F);
		assertEquals(1, ParrotEggHatchLogic.hatchCount(random, 1.0F, 0.0F));
	}

	@Test
	void middleChanceRespectsRandomRoll() {
		RandomSource above = mock(RandomSource.class);
		when(above.nextFloat()).thenReturn(0.8F);
		assertEquals(0, ParrotEggHatchLogic.hatchCount(above, 0.5F, 0.0F));

		RandomSource below = mock(RandomSource.class);
		when(below.nextFloat()).thenReturn(0.2F);
		assertEquals(1, ParrotEggHatchLogic.hatchCount(below, 0.5F, 0.0F));
	}

	@Test
	void twinRollTriggersTwins() {
		RandomSource random = mock(RandomSource.class);
		// 第一掷 0.2 < 0.5 → 孵化；第二掷 0.1 < 1.0（twinChance 越界仍 clamp）→ 双胞胎
		when(random.nextFloat()).thenReturn(0.2F, 0.1F);
		assertEquals(2, ParrotEggHatchLogic.hatchCount(random, 0.5F, 1.0F));
	}

	@Test
	void twinChanceBelowZeroNeverTwins() {
		RandomSource random = mock(RandomSource.class);
		when(random.nextFloat()).thenReturn(0.2F);
		assertEquals(1, ParrotEggHatchLogic.hatchCount(random, 0.5F, -0.1F));
	}

	@Test
	void twinChanceBoundaryFallsThroughToSingle() {
		RandomSource random = mock(RandomSource.class);
		// 第一掷 0.2 → 孵化；第二掷 0.5 不小于 0.03 → 单只
		when(random.nextFloat()).thenReturn(0.2F, 0.5F);
		assertEquals(1, ParrotEggHatchLogic.hatchCount(random, 0.5F, 0.03F));
	}

	@Test
	void jungleBoostRaisesDefaultChance() {
		assertEquals(0.75F, ParrotEggHatchLogic.jungleBoost(0.5F), 1.0E-6F);
	}

	@Test
	void jungleBoostCapsAtOne() {
		assertEquals(1.0F, ParrotEggHatchLogic.jungleBoost(0.9F), 1.0E-6F);
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
