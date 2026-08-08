package dev.quirky.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

/**
 * 配置集中定义。v3：开关为主——行为/QoL 机制每个一个开关（默认全开，关闭 = 该机制不生效）；
 * 非核心数值保留为隐藏字段（@ConfigEntry.Gui.Excluded，json5 可手改，GUI 不显示）；
 * 物品类机制无开关（玩家主动使用，不做禁用）。四组：gameplay / client / totem / copper_golem。
 */
@Config(name = "quirky")
public class QuirkyConfig implements ConfigData {

	// ==== gameplay 玩法组：行为机制开关 ====

	@ConfigEntry.Category("gameplay")
	@ConfigEntry.Gui.Tooltip
	public boolean wakeUpEnabled = true;

	@ConfigEntry.Category("gameplay")
	@ConfigEntry.Gui.Tooltip
	public boolean autoClimbEnabled = true;

	@ConfigEntry.Category("gameplay")
	@ConfigEntry.Gui.Tooltip
	public boolean soulLightEnabled = true;

	@ConfigEntry.Category("gameplay")
	@ConfigEntry.Gui.Tooltip
	public boolean harvestReplantEnabled = true;

	@ConfigEntry.Category("gameplay")
	@ConfigEntry.Gui.Tooltip
	public boolean doubleDoorEnabled = true;

	@ConfigEntry.Category("gameplay")
	@ConfigEntry.Gui.Tooltip
	public boolean quickEquipEnabled = true;

	@ConfigEntry.Category("gameplay")
	@ConfigEntry.Gui.Tooltip
	public boolean melonSeedSpitEnabled = true;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("gameplay")
	public int wakeUpSlowFallingSeconds = 12;

	// ==== client 客户端组：提示类开关 ====

	@ConfigEntry.Category("client")
	@ConfigEntry.Gui.Tooltip
	public boolean tickerEnabled = true;

	@ConfigEntry.Category("client")
	@ConfigEntry.Gui.Tooltip
	public boolean arrowDingEnabled = true;

	@ConfigEntry.Category("client")
	@ConfigEntry.Gui.Tooltip
	public boolean campfireSmokeEnabled = true;

	@ConfigEntry.Category("client")
	@ConfigEntry.Gui.Tooltip
	public boolean deathCamEnabled = true;

	@ConfigEntry.Category("client")
	@ConfigEntry.Gui.Tooltip
	public boolean shulkerPreviewEnabled = true;

	@ConfigEntry.Category("client")
	@ConfigEntry.Gui.Tooltip
	public boolean mapPreviewEnabled = true;

	@ConfigEntry.Category("client")
	@ConfigEntry.Gui.Tooltip
	public boolean clockTooltipEnabled = true;

	@ConfigEntry.Category("client")
	@ConfigEntry.Gui.Tooltip
	public boolean foodTooltipEnabled = true;

	@ConfigEntry.Category("client")
	@ConfigEntry.Gui.Tooltip
	public boolean attributeTooltipEnabled = true;

	@ConfigEntry.Category("client")
	@ConfigEntry.Gui.Tooltip
	public boolean advancedTooltipEnabled = true;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("client")
	public int tickerHoldTicks = 50;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("client")
	public int tickerAnimTicks = 5;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("client")
	public int deathCamDuration = 50;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("client")
	public float arrowDingVolume = 0.6F;

	// ==== totem 图腾组（独立页）：开关 + 核心数值可见 ====

	@ConfigEntry.Category("totem")
	@ConfigEntry.Gui.Tooltip
	public boolean totemEnabled = true;

	@ConfigEntry.Category("totem")
	@ConfigEntry.BoundedDiscrete(min = 1, max = 10)
	@ConfigEntry.Gui.Tooltip
	public int hitsToRetrieve = 3;

	@ConfigEntry.Category("totem")
	@ConfigEntry.BoundedDiscrete(min = 1, max = 2)
	@ConfigEntry.Gui.Tooltip
	public int spawnHeightOffset = 1;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("totem")
	public float hitSoundVolume = 1.0F;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("totem")
	public float hitSoundPitch = 1.0F;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("totem")
	public float retrieveSoundVolume = 0.5F;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("totem")
	public int enchantParticleChance = 4;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("totem")
	public int endRodParticleChance = 12;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("totem")
	public float endRodParticleXzSpread = 0.35F;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("totem")
	public float endRodParticleYSpread = 0.3F;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("totem")
	public float particleXzSpread = 0.45F;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("totem")
	public float particleYSpread = 0.55F;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("totem")
	public float modelScale = 1.8F;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("totem")
	public float bobAmplitude = 0.25F;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("totem")
	public int bobPeriod = 12;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("totem")
	public int spinPeriod = 8;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("totem")
	public float swayAmplitude = 0.08F;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("totem")
	public int swayPeriod = 20;

	// ==== copper_golem 铜傀儡组：总开关 + 连接三件套 + 行为数值可见 ====

	@ConfigEntry.Category("copper_golem")
	@ConfigEntry.Gui.Tooltip
	public boolean golemAiEnabled = true;

	@ConfigEntry.Category("copper_golem")
	@ConfigEntry.BoundedDiscrete(min = 0, max = 300)
	@ConfigEntry.Gui.Tooltip
	public int heartbeatIntervalSeconds = 30;

	@ConfigEntry.Category("copper_golem")
	@ConfigEntry.BoundedDiscrete(min = 4, max = 32)
	@ConfigEntry.Gui.Tooltip
	public int droppedPickupRange = 16;

	@ConfigEntry.Category("copper_golem")
	@ConfigEntry.Gui.Tooltip
	public String aiBaseUrl = "https://api.openai.com/v1";

	@ConfigEntry.Category("copper_golem")
	@ConfigEntry.Gui.Tooltip
	public String aiApiKey = "";

	@ConfigEntry.Category("copper_golem")
	@ConfigEntry.Gui.Tooltip
	public String aiModel = "";

	@ConfigEntry.Category("copper_golem")
	@ConfigEntry.BoundedDiscrete(min = 16, max = 128)
	@ConfigEntry.Gui.Tooltip
	public int maxTransportRange = 64;

	@ConfigEntry.Category("copper_golem")
	@ConfigEntry.BoundedDiscrete(min = 2, max = 32)
	@ConfigEntry.Gui.Tooltip
	public int aiListenRange = 16;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("copper_golem")
	public float aiTemperature = 0.7F;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("copper_golem")
	public int aiMaxTokens = 256;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("copper_golem")
	public int aiCooldownTicks = 40;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("copper_golem")
	public String aiThinking = "low"; // off/low/medium/high/xhigh/max

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("copper_golem")
	public String aiSummaryModel = "";

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("copper_golem")
	public int aiSummaryMessages = 20;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("copper_golem")
	public int aiSummaryTokens = 4000;

	// ==== 物品类（无开关，数值全部隐藏，json5 可调）====

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("gameplay")
	public int quiverCapacity = 4;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("gameplay")
	public int ropeMaxExtendPerUse = 32;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("gameplay")
	public int boomerangRange = 24;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("gameplay")
	public float boomerangBreakChance = 0.05F;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("gameplay")
	public int seedPouchRadius = 1;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("gameplay")
	public int fishBaitDurationSeconds = 90;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("gameplay")
	public int fishBaitRadius = 4;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("gameplay")
	public int petWhistleRadius = 24;

	@ConfigEntry.Gui.Excluded
	@ConfigEntry.Category("gameplay")
	public int petWhistlePhantomMax = 3;

	/**
	 * 运行时自 clamp：GUI 的 BoundedDiscrete 只约束界面操作，json5 手改可越界；
	 * float 字段 GUI 无范围约束。越界值会导致 API 报错/渲染异常，统一在此兜底。
	 */
	@Override
	public void validatePostLoad() throws ValidationException {
		quiverCapacity = Math.clamp(quiverCapacity, 1, 8);
		ropeMaxExtendPerUse = Math.clamp(ropeMaxExtendPerUse, 1, 64);
		boomerangRange = Math.clamp(boomerangRange, 4, 24);
		boomerangBreakChance = Math.clamp(boomerangBreakChance, 0F, 1F);
		seedPouchRadius = Math.clamp(seedPouchRadius, 0, 2);
		fishBaitDurationSeconds = Math.clamp(fishBaitDurationSeconds, 10, 300);
		fishBaitRadius = Math.clamp(fishBaitRadius, 2, 8);
		petWhistleRadius = Math.clamp(petWhistleRadius, 8, 64);
		petWhistlePhantomMax = Math.clamp(petWhistlePhantomMax, 1, 5);
		wakeUpSlowFallingSeconds = Math.clamp(wakeUpSlowFallingSeconds, 0, 60);
		arrowDingVolume = Math.clamp(arrowDingVolume, 0F, 2F);
		tickerHoldTicks = Math.clamp(tickerHoldTicks, 20, 200);
		tickerAnimTicks = Math.clamp(tickerAnimTicks, 2, 20);
		deathCamDuration = Math.clamp(deathCamDuration, 40, 100);
		spawnHeightOffset = Math.clamp(spawnHeightOffset, 1, 2);
		hitsToRetrieve = Math.clamp(hitsToRetrieve, 1, 10);
		hitSoundVolume = Math.clamp(hitSoundVolume, 0F, 2F);
		hitSoundPitch = Math.clamp(hitSoundPitch, 0F, 2F);
		retrieveSoundVolume = Math.clamp(retrieveSoundVolume, 0F, 2F);
		enchantParticleChance = Math.clamp(enchantParticleChance, 1, 100);
		endRodParticleChance = Math.clamp(endRodParticleChance, 1, 100);
		endRodParticleXzSpread = Math.clamp(endRodParticleXzSpread, 0F, 10F);
		endRodParticleYSpread = Math.clamp(endRodParticleYSpread, 0F, 10F);
		particleXzSpread = Math.clamp(particleXzSpread, 0F, 10F);
		particleYSpread = Math.clamp(particleYSpread, 0F, 10F);
		modelScale = Math.clamp(modelScale, 0.1F, 10F);
		bobAmplitude = Math.clamp(bobAmplitude, 0F, 2F);
		bobPeriod = Math.clamp(bobPeriod, 4, 60);
		spinPeriod = Math.clamp(spinPeriod, 4, 60);
		swayAmplitude = Math.clamp(swayAmplitude, 0F, 2F);
		swayPeriod = Math.clamp(swayPeriod, 4, 60);
		heartbeatIntervalSeconds = Math.clamp(heartbeatIntervalSeconds, 0, 300);
		droppedPickupRange = Math.clamp(droppedPickupRange, 4, 32);
		maxTransportRange = Math.clamp(maxTransportRange, 16, 128);
		aiListenRange = Math.clamp(aiListenRange, 2, 32);
		aiTemperature = Math.clamp(aiTemperature, 0F, 2F);
		aiMaxTokens = Math.clamp(aiMaxTokens, 64, 4096);
		aiCooldownTicks = Math.clamp(aiCooldownTicks, 10, 600);
		aiSummaryMessages = Math.clamp(aiSummaryMessages, 4, 100);
		aiSummaryTokens = Math.clamp(aiSummaryTokens, 256, 16000);
	}
}
