package dev.quirky.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "quirky")
public class QuirkyConfig implements ConfigData {

	// ==== 机制开关（默认全开，关掉恢复原版行为）====

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
	public boolean cloudBottle = true;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean equipSwap = true;

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
	public boolean longPick = true;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean ladderSnap = true;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean offhandSwap = true;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean goldButton = true;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean ironButton = true;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean obsidianPlate = true;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean torchArrow = true;

	@ConfigEntry.Category("toggles")
	@ConfigEntry.Gui.Tooltip
	public boolean woodenHopper = true;

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

	@ConfigEntry.Category("client_qol")
	@ConfigEntry.BoundedDiscrete(min = 16, max = 256)
	@ConfigEntry.Gui.Tooltip
	public int pickRangeCreative = 100;

	@ConfigEntry.Category("client_qol")
	@ConfigEntry.BoundedDiscrete(min = 4, max = 64)
	@ConfigEntry.Gui.Tooltip
	public int pickRangeSurvival = 12;
}
