package dev.quirky.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuirkyConfigDefaultsTest {

	@Test
	void itemParamsMatchHardcodedDefaults() {
		QuirkyConfig c = new QuirkyConfig();
		assertEquals(4, c.quiverCapacity);
		assertEquals(32, c.ropeMaxExtendPerUse);
		assertEquals(24, c.boomerangRange);
		assertEquals(0.05F, c.boomerangBreakChance);
		assertEquals(1, c.seedPouchRadius);
		assertEquals(90, c.fishBaitDurationSeconds);
		assertEquals(4, c.fishBaitRadius);
		assertEquals(24, c.petWhistleRadius);
		assertEquals(3, c.petWhistlePhantomMax);
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
	void mechanicsParamsWithinBounds() {
		QuirkyConfig c = new QuirkyConfig();
		assertTrue(c.wakeUpSlowFallingSeconds >= 0 && c.wakeUpSlowFallingSeconds <= 60);
		assertTrue(c.arrowDingVolume >= 0.0F && c.arrowDingVolume <= 1.0F);
	}

	@Test
	void clientParamsWithinBounds() {
		QuirkyConfig c = new QuirkyConfig();
		assertEquals(50, c.deathCamDuration);
		assertTrue(c.tickerHoldTicks >= 20 && c.tickerHoldTicks <= 200);
		assertTrue(c.tickerAnimTicks >= 2 && c.tickerAnimTicks <= 20);
		assertTrue(c.deathCamDuration >= 40 && c.deathCamDuration <= 100);
	}

	@Test
	void featureSwitchesDefaultOn() {
		QuirkyConfig c = new QuirkyConfig();
		assertTrue(c.wakeUpEnabled);
		assertTrue(c.autoClimbEnabled);
		assertTrue(c.soulLightEnabled);
		assertTrue(c.harvestReplantEnabled);
		assertTrue(c.doubleDoorEnabled);
		assertTrue(c.quickEquipEnabled);
		assertTrue(c.melonSeedSpitEnabled);
		assertTrue(c.tickerEnabled);
		assertTrue(c.arrowDingEnabled);
		assertTrue(c.campfireSmokeEnabled);
		assertTrue(c.deathCamEnabled);
		assertTrue(c.shulkerPreviewEnabled);
		assertTrue(c.mapPreviewEnabled);
		assertTrue(c.clockTooltipEnabled);
		assertTrue(c.foodTooltipEnabled);
		assertTrue(c.attributeTooltipEnabled);
		assertTrue(c.advancedTooltipEnabled);
		assertTrue(c.totemEnabled);
		assertTrue(c.golemAiEnabled);
	}
}
