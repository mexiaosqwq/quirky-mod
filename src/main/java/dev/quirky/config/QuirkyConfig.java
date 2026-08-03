package dev.quirky.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "quirky")
public class QuirkyConfig implements ConfigData {

	// ==== 行为类机制开关（默认全开，关掉即恢复原版行为）====

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean mapPreview = true;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean harvestReplant = true;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean doubleDoor = true;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean clockTooltip = true;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean equipSwap = true;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean offhandSwap = true;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean melonSeed = true;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean totemOfHolding = true;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean soulLighting = true;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean shulkerTooltip = true;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean foodTooltip = true;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean attributeTooltip = true;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean usageTicker = true;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean deathCam = true;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean ladderSnap = true;

<<<<<<< HEAD
	// ==== 批 A：弓箭叮声 / 起床保护 / 鹦鹉蛋 / 营火染色烟 ====

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean arrowDingEnabled = true;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public float arrowDingVolume = 0.6F;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean wakeUpProtectionEnabled = true;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.BoundedDiscrete(min = 0, max = 60)
	@ConfigEntry.Gui.Tooltip
	public int wakeUpSlowFallingSeconds = 12;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean parrotEggEnabled = true;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public float parrotEggHatchChance = 0.5F;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public float parrotEggTwinChance = 0.03F;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean dyedCampfireSmokeEnabled = true;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean dyedCampfireGlow = true;
=======
	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean seedPouchEnabled = true;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.BoundedDiscrete(min = 0, max = 2)
	@ConfigEntry.Gui.Tooltip
	public int seedPouchRadius = 1;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean fishBaitEnabled = true;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.BoundedDiscrete(min = 10, max = 300)
	@ConfigEntry.Gui.Tooltip
	public int fishBaitDurationSeconds = 90;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.BoundedDiscrete(min = 2, max = 8)
	@ConfigEntry.Gui.Tooltip
	public int fishBaitRadius = 4;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean fishBaitRainBonus = true;
>>>>>>> feat/batch-b-farm-fish

	// ==== 图腾手感参数（服务端）====

	@ConfigEntry.Category("totem")
	@ConfigEntry.BoundedDiscrete(min = 1, max = 2)
	@ConfigEntry.Gui.Tooltip
	public int spawnHeightOffset = 1;

	@ConfigEntry.Category("totem")
	@ConfigEntry.BoundedDiscrete(min = 1, max = 10)
	@ConfigEntry.Gui.Tooltip
	public int hitsToRetrieve = 3;

	@ConfigEntry.Category("totem")
	@ConfigEntry.Gui.Tooltip
	public float hitSoundVolume = 1.0F;

	@ConfigEntry.Category("totem")
	@ConfigEntry.Gui.Tooltip
	public float hitSoundPitch = 1.0F;

	@ConfigEntry.Category("totem")
	@ConfigEntry.Gui.Tooltip
	public float retrieveSoundVolume = 0.5F;

	@ConfigEntry.Category("totem")
	@ConfigEntry.BoundedDiscrete(min = 1, max = 100)
	@ConfigEntry.Gui.Tooltip
	public int enchantParticleChance = 4;

	@ConfigEntry.Category("totem")
	@ConfigEntry.BoundedDiscrete(min = 1, max = 100)
	@ConfigEntry.Gui.Tooltip
	public int endRodParticleChance = 12;

	@ConfigEntry.Category("totem")
	@ConfigEntry.Gui.Tooltip
	public float endRodParticleXzSpread = 0.35F;

	@ConfigEntry.Category("totem")
	@ConfigEntry.Gui.Tooltip
	public float endRodParticleYSpread = 0.3F;

	@ConfigEntry.Category("totem")
	@ConfigEntry.Gui.Tooltip
	public float particleXzSpread = 0.45F;

	@ConfigEntry.Category("totem")
	@ConfigEntry.Gui.Tooltip
	public float particleYSpread = 0.55F;

	// ==== 图腾手感参数（客户端渲染）====

	@ConfigEntry.Category("totem")
	@ConfigEntry.Gui.Tooltip
	public float modelScale = 1.8F;

	@ConfigEntry.Category("totem")
	@ConfigEntry.Gui.Tooltip
	public float bobAmplitude = 0.25F;

	@ConfigEntry.Category("totem")
	@ConfigEntry.BoundedDiscrete(min = 4, max = 60)
	@ConfigEntry.Gui.Tooltip
	public int bobPeriod = 12;

	@ConfigEntry.Category("totem")
	@ConfigEntry.BoundedDiscrete(min = 4, max = 60)
	@ConfigEntry.Gui.Tooltip
	public int spinPeriod = 8;

	@ConfigEntry.Category("totem")
	@ConfigEntry.Gui.Tooltip
	public float swayAmplitude = 0.08F;

	@ConfigEntry.Category("totem")
	@ConfigEntry.BoundedDiscrete(min = 4, max = 60)
	@ConfigEntry.Gui.Tooltip
	public int swayPeriod = 20;

	// ==== 客户端实用功能参数 ====

	@ConfigEntry.Category("client_qol")
	@ConfigEntry.BoundedDiscrete(min = 20, max = 200)
	@ConfigEntry.Gui.Tooltip
	public int tickerHoldTicks = 50;

	@ConfigEntry.Category("client_qol")
	@ConfigEntry.BoundedDiscrete(min = 2, max = 20)
	@ConfigEntry.Gui.Tooltip
	public int tickerAnimTicks = 5;

	@ConfigEntry.Category("client_qol")
	@ConfigEntry.BoundedDiscrete(min = 40, max = 100)
	@ConfigEntry.Gui.Tooltip
	public int deathCamDuration = 50;
}
