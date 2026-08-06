package dev.quirky.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

/**
 * 配置集中定义。机制默认全开且不可关闭（v1.1 整理：删除全部布尔开关），
 * 这里只保留玩家可调数值参数，按「物品 / 机制 / 客户端」三组分类。
 */
@Config(name = "quirky")
public class QuirkyConfig implements ConfigData {

	// ==== 物品组：各物品机制的数值参数 ====

	@ConfigEntry.Category("items")
	@ConfigEntry.BoundedDiscrete(min = 1, max = 8)
	@ConfigEntry.Gui.Tooltip
	public int quiverCapacity = 4;

	@ConfigEntry.Category("items")
	@ConfigEntry.BoundedDiscrete(min = 1, max = 64)
	@ConfigEntry.Gui.Tooltip
	public int ropeMaxExtendPerUse = 32;

	@ConfigEntry.Category("items")
	@ConfigEntry.BoundedDiscrete(min = 4, max = 24)
	@ConfigEntry.Gui.Tooltip
	public int boomerangRange = 12;

	@ConfigEntry.Category("items")
	@ConfigEntry.Gui.Tooltip
	public float boomerangBreakChance = 0.05F;

	@ConfigEntry.Category("items")
	@ConfigEntry.BoundedDiscrete(min = 0, max = 2)
	@ConfigEntry.Gui.Tooltip
	public int seedPouchRadius = 1;

	@ConfigEntry.Category("items")
	@ConfigEntry.BoundedDiscrete(min = 10, max = 300)
	@ConfigEntry.Gui.Tooltip
	public int fishBaitDurationSeconds = 90;

	@ConfigEntry.Category("items")
	@ConfigEntry.BoundedDiscrete(min = 2, max = 8)
	@ConfigEntry.Gui.Tooltip
	public int fishBaitRadius = 4;

	@ConfigEntry.Category("items")
	@ConfigEntry.BoundedDiscrete(min = 8, max = 64)
	@ConfigEntry.Gui.Tooltip
	public int petWhistleRadius = 24;

	@ConfigEntry.Category("items")
	@ConfigEntry.BoundedDiscrete(min = 1, max = 5)
	@ConfigEntry.Gui.Tooltip
	public int petWhistlePhantomMax = 3;

	// ==== 保留图腾（物品组，含服务端与客户端渲染参数）====

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

	// ==== 机制组：行为类机制的数值参数 ====

	@ConfigEntry.Category("mechanics")
	@ConfigEntry.Gui.Tooltip
	public float arrowDingVolume = 0.6F;

	@ConfigEntry.Category("mechanics")
	@ConfigEntry.BoundedDiscrete(min = 0, max = 60)
	@ConfigEntry.Gui.Tooltip
	public int wakeUpSlowFallingSeconds = 12;

	// ==== 客户端组：显示类数值参数 ====

	@ConfigEntry.Category("client")
	@ConfigEntry.BoundedDiscrete(min = 20, max = 200)
	@ConfigEntry.Gui.Tooltip
	public int tickerHoldTicks = 50;

	@ConfigEntry.Category("client")
	@ConfigEntry.BoundedDiscrete(min = 2, max = 20)
	@ConfigEntry.Gui.Tooltip
	public int tickerAnimTicks = 5;

	@ConfigEntry.Category("client")
	@ConfigEntry.BoundedDiscrete(min = 40, max = 100)
	@ConfigEntry.Gui.Tooltip
	public int deathCamDuration = 50;

	// ==== AI 组：铜傀儡 AI 对话参数 ====

	@ConfigEntry.Category("ai")
	@ConfigEntry.Gui.Tooltip
	public String aiBaseUrl = "https://api.openai.com/v1";

	@ConfigEntry.Category("ai")
	@ConfigEntry.Gui.Tooltip
	public String aiApiKey = "";

	@ConfigEntry.Category("ai")
	@ConfigEntry.Gui.Tooltip
	public String aiModel = "";

	@ConfigEntry.Category("ai")
	@ConfigEntry.Gui.Tooltip
	public float aiTemperature = 0.7F;

	@ConfigEntry.Category("ai")
	@ConfigEntry.BoundedDiscrete(min = 64, max = 1024)
	@ConfigEntry.Gui.Tooltip
	public int aiMaxTokens = 256;

	@ConfigEntry.Category("ai")
	@ConfigEntry.BoundedDiscrete(min = 2, max = 32)
	@ConfigEntry.Gui.Tooltip
	public int aiListenRange = 8;

	@ConfigEntry.Category("ai")
	@ConfigEntry.BoundedDiscrete(min = 10, max = 600)
	@ConfigEntry.Gui.Tooltip
	public int aiCooldownTicks = 40;

	@ConfigEntry.Category("ai")
	@ConfigEntry.Gui.Tooltip
	public String aiThinking = "low"; // off/low/medium/high/xhigh/max

	@ConfigEntry.Category("ai")
	@ConfigEntry.Gui.Tooltip
	public String aiSummaryModel = "";

	@ConfigEntry.Category("ai")
	@ConfigEntry.BoundedDiscrete(min = 4, max = 100)
	@ConfigEntry.Gui.Tooltip
	public int aiSummaryMessages = 20;

	@ConfigEntry.Category("ai")
	@ConfigEntry.BoundedDiscrete(min = 256, max = 16000)
	@ConfigEntry.Gui.Tooltip
	public int aiSummaryTokens = 4000;
}
