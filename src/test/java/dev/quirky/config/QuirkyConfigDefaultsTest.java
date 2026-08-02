package dev.quirky.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuirkyConfigDefaultsTest {

	@Test
	void allTogglesDefaultOn() {
		QuirkyConfig c = new QuirkyConfig();
		assertTrue(c.mapPreview);
		assertTrue(c.harvestReplant);
		assertTrue(c.doubleDoor);
		assertTrue(c.clockTooltip);
		assertTrue(c.cloudBottle);
		assertTrue(c.equipSwap);
		assertTrue(c.melonSeed);
		assertTrue(c.totemOfHolding);
	}

	@Test
	void totemParamsMatchCurrentHardcodedConstants() {
		QuirkyConfig c = new QuirkyConfig();
		assertEquals(3, c.hitsToRetrieve);
		assertEquals(1, c.spawnHeightOffset);
		assertEquals(1.0F, c.hitSoundVolume);
		assertEquals(1.0F, c.hitSoundPitch);
		assertEquals(0.5F, c.retrieveSoundVolume);
		assertEquals(4, c.enchantParticleChance);
		assertEquals(12, c.endRodParticleChance);
		assertEquals(0.35F, c.endRodParticleXzSpread);
		assertEquals(0.3F, c.endRodParticleYSpread);
		assertEquals(0.45F, c.particleXzSpread);
		assertEquals(0.55F, c.particleYSpread);
		assertEquals(1.8F, c.modelScale);
		assertEquals(0.25F, c.bobAmplitude);
		assertEquals(12, c.bobPeriod);
		assertEquals(8, c.spinPeriod);
		assertEquals(0.08F, c.swayAmplitude);
		assertEquals(20, c.swayPeriod);
	}

	@Test
	void particleChanceBoundsPreventNextIntCrash() {
		QuirkyConfig c = new QuirkyConfig();
		assertTrue(c.enchantParticleChance >= 1);
		assertTrue(c.endRodParticleChance >= 1);
	}

	@Test
	void clientQolTogglesDefaultOn() {
		QuirkyConfig c = new QuirkyConfig();
		assertTrue(c.soulLighting && c.shulkerTooltip
			&& c.foodTooltip && c.attributeTooltip && c.usageTicker
			&& c.deathCam && c.longPick && c.ladderSnap && c.offhandSwap
			&& c.goldButton && c.ironButton && c.obsidianPlate
			&& c.torchArrow && c.woodenHopper);
		assertEquals(100, c.pickRangeCreative);
		assertEquals(12, c.pickRangeSurvival);
		assertEquals(50, c.deathCamDuration);
	}

	@Test
	void clientQolParamsWithinBounds() {
		QuirkyConfig c = new QuirkyConfig();
		assertTrue(c.tickerHoldTicks >= 20 && c.tickerHoldTicks <= 200);
		assertTrue(c.deathCamDuration >= 40 && c.deathCamDuration <= 100);
		assertTrue(c.pickRangeCreative >= 16 && c.pickRangeCreative <= 256);
		assertTrue(c.pickRangeSurvival >= 4 && c.pickRangeSurvival <= 64);
	}
}
