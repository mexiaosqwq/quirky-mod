# Quirky Cloth Config 适配实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 接入 Cloth Config API，把图腾手感参数从硬编码常量改为游戏内可调（GUI + `config/quirky.json5`），并为全部 8 个机制加开关。

**Architecture:** AutoConfig 在 `ModInitializer`（双端进程都会执行）注册 `QuirkyConfig`（Jankson 序列化）；各机制入口通过静态 `QuirkyConfigHolder.get()` 读配置（测试环境注入默认实例，不碰 AutoConfig/文件系统）；`/quirky reload` 命令调 `ConfigHolder.load()` 后**重新注入 holder**（AutoConfig load 会替换内部实例）；ModMenu 提供 GUI 入口（可选依赖）。

**Tech Stack:** Minecraft 26.2 官方映射、Fabric API、Cloth Config API 26.2.155、ModMenu 20.0.0-beta.2（compileOnly）、JUnit 5 + Mockito。

## Global Constraints

- 构建：`JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH gradle build --no-daemon --console=plain`；单元测试 `gradle test`，Java 25，26.2 官方映射
- Tab 缩进；资源路径 `lower_snake_case`；配置包 `dev.quirky.config`（common）/ `dev.quirky.client.config`（客户端 GUI）
- 规格：`docs/superpowers/specs/2026-08-02-cloth-config-design.md`（行为以规格为准）
- 依赖：`modApi("me.shedaniel.cloth:cloth-config-fabric:26.2.155")`（exclude fabric-api）+ `modCompileOnly("com.terraformersmc:modmenu:20.0.0-beta.2")`；`fabric.mod.json` `depends` 加 `cloth-config: >=26.2.155`、`suggests` 加 `modmenu`、`entrypoints` 加 `modmenu`
- 默认值必须与现有硬编码常量一一对应：服务端 8 个（3 / 1.0 / 1.0 / 0.5 / 4 / 12 / 0.45 / 0.55）、客户端 6 个（1.8 / 0.25 / 12 / 8 / 0.08 / 20）
- 开关检查放 mixin/入口薄层；纯逻辑类（`TotemOfHoldingLogic`）不感知配置
- `nextInt(0)` 会抛异常：粒子频率 chance 字段 min=1；**注意 26.2 注解仅 `@ConfigEntry.BoundedDiscrete(long min, long max)` 适用于 int/long 字段（自动渲染 slider），float 字段无边界注解（BoundedFloating 被注释）——运行时读取仍需 clamp 防御**（见 Task 5）
- 若 AutoConfig 具体 API（注解/类名）与计划不符，以反编译 `cloth-config-fabric-26.2.155.jar` 为准修正（`find ~/.gradle -name "cloth-config*jar"`）

---

### Task 1: QuirkyConfig 配置类 + QuirkyConfigHolder 静态容器（TDD）

**Files:**
- Create: `src/main/java/dev/quirky/config/QuirkyConfig.java`
- Create: `src/main/java/dev/quirky/config/QuirkyConfigHolder.java`
- Test: `src/test/java/dev/quirky/config/QuirkyConfigDefaultsTest.java`

**Interfaces:**
- Consumes: 无（独立）
- Produces:
  - `QuirkyConfig` — `@Config(name = "quirky")` + `implements ConfigData`；`toggles` 分类 8 个 boolean（全默认 true）；`totem` 分类 14 个数值字段
  - `QuirkyConfigHolder.get()` → `QuirkyConfig`（默认 `new QuirkyConfig()`）/ `QuirkyConfigHolder.set(QuirkyConfig)`

- [ ] **Step 1: 写失败测试** `src/test/java/dev/quirky/config/QuirkyConfigDefaultsTest.java`

```java
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
		assertEquals(1.0F, c.hitSoundVolume);
		assertEquals(1.0F, c.hitSoundPitch);
		assertEquals(0.5F, c.retrieveSoundVolume);
		assertEquals(4, c.enchantParticleChance);
		assertEquals(12, c.endRodParticleChance);
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
	void holderDefaultsToFreshConfig() {
		QuirkyConfig c = QuirkyConfigHolder.get();
		assertTrue(c.melonSeed);
		assertEquals(3, c.hitsToRetrieve);
	}
}
```

- [ ] **Step 2: 运行确认失败**

Run: `gradle test --tests 'dev.quirky.config.QuirkyConfigDefaultsTest'`
Expected: 编译失败（QuirkyConfig / QuirkyConfigHolder 不存在）

- [ ] **Step 3: 实现** `src/main/java/dev/quirky/config/QuirkyConfig.java`

```java
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

	// ==== 图腾手感参数（服务端）====

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
}
```

- [ ] **Step 4: 实现** `src/main/java/dev/quirky/config/QuirkyConfigHolder.java`

```java
package dev.quirky.config;

/**
 * 轻量静态容器：测试环境直接 set 默认实例（不碰 AutoConfig/文件系统）；
 * 生产环境在 QuirkyMod.onInitialize 注入 AutoConfig 实例。
 */
public final class QuirkyConfigHolder {
	private static QuirkyConfig config = new QuirkyConfig();

	private QuirkyConfigHolder() {
	}

	public static QuirkyConfig get() {
		return config;
	}

	public static void set(QuirkyConfig config) {
		QuirkyConfigHolder.config = config;
	}
}
```

- [ ] **Step 5: 运行确认通过**

Run: `gradle test --tests 'dev.quirky.config.QuirkyConfigDefaultsTest'`
Expected: 4 个测试全过

- [ ] **Step 6: 提交**

```bash
git add src/main/java/dev/quirky/config/QuirkyConfig.java src/main/java/dev/quirky/config/QuirkyConfigHolder.java src/test/java/dev/quirky/config/QuirkyConfigDefaultsTest.java
git commit -m "feat: add quirky config class with toggles and totem params"
```

---

### Task 2: 接入 Cloth Config 依赖 + AutoConfig 注册 + reload 命令

**Files:**
- Modify: `gradle.properties`（加 `cloth_config_version=26.2.155`、`modmenu_version=20.0.0-beta.2`）
- Modify: `build.gradle`（repositories + dependencies）
- Modify: `src/main/resources/fabric.mod.json`（depends/suggests）
- Create: `src/main/java/dev/quirky/config/QuirkyReloadCommand.java`
- Modify: `src/main/java/dev/quirky/QuirkyMod.java`（注册 AutoConfig + 注入 holder + 注册命令）

**Interfaces:**
- Consumes: Task 1 的 `QuirkyConfig` / `QuirkyConfigHolder`
- Produces: 无（启动注册 + `/quirky reload` 命令）

- [ ] **Step 1: 改 gradle.properties**

在 `fabric_api_version` 行后追加：
```properties
cloth_config_version=26.2.155
modmenu_version=20.0.0-beta.2
```

- [ ] **Step 2: 改 build.gradle**

repositories 块改为：
```groovy
repositories {
	maven { url "https://maven.shedaniel.me/" }
	maven { url "https://maven.terraformersmc.com/releases/" }
}
```

dependencies 块追加：
```groovy
	// Cloth Config API for the in-game config screen; ModMenu is optional (compileOnly + suggests).
	modApi("me.shedaniel.cloth:cloth-config-fabric:${project.cloth_config_version}") {
		exclude(group: "net.fabricmc.fabric-api")
	}
	modCompileOnly("com.terraformersmc:modmenu:${project.modmenu_version}")
```

- [ ] **Step 3: 改 fabric.mod.json**

```json
  "entrypoints": {
    "main": ["dev.quirky.QuirkyMod"],
    "client": ["dev.quirky.client.QuirkyModClient"],
    "modmenu": ["dev.quirky.client.config.ModMenuIntegration"]
  },
```
（modmenu entrypoint 类在 Task 4 才创建，本任务先只改构建文件可编译；depends/suggests 加：）
```json
  "depends": {
    "fabricloader": ">=0.19.3",
    "minecraft": "~26.2",
    "java": ">=25",
    "fabric-api": "*",
    "cloth-config": ">=26.2.155"
  },
  "suggests": {
    "modmenu": "*"
  }
```

- [ ] **Step 4: 实现** `src/main/java/dev/quirky/config/QuirkyReloadCommand.java`

```java
package dev.quirky.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.literal;

public final class QuirkyReloadCommand {
	private QuirkyReloadCommand() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(literal("quirky")
				.then(literal("reload")
					.requires(source -> source.hasPermission(2))
					.executes(ctx -> {
						ConfigHolder<QuirkyConfig> holder = AutoConfig.getConfigHolder(QuirkyConfig.class);
						try {
							holder.load();
							// AutoConfig 的 load() 用新反序列化的实例替换内部引用，必须重新注入静态 holder
							QuirkyConfigHolder.set(holder.getConfig());
							ctx.getSource().sendSuccess(
								() -> Component.literal("Quirky config reloaded"), true);
							return 1;
						} catch (Exception e) {
							ctx.getSource().sendFailure(
								Component.literal("Quirky config reload failed, keeping old values")
									.withStyle(ChatFormatting.RED));
							return 0;
						}
					}))));
	}
}
```

- [ ] **Step 5: 改 `QuirkyMod.java`**

```java
package dev.quirky;

import dev.quirky.config.QuirkyConfig;
import dev.quirky.config.QuirkyConfigHolder;
import dev.quirky.config.QuirkyReloadCommand;
import dev.quirky.equip_swap.EquipSwapServer;
import dev.quirky.harvest.HarvestHandler;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuirkyMod implements ModInitializer {
	public static final String MOD_ID = "quirky";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// AutoConfig 在双端进程都会执行；服务端进程读写服务端 config 目录，客户端进程读写客户端 config 目录
		AutoConfig.register(QuirkyConfig.class, JanksonConfigSerializer::new);
		QuirkyConfigHolder.set(AutoConfig.getConfigHolder(QuirkyConfig.class).getConfig());
		ModBlocks.register();
		ModItems.register();
		ModEntities.register();
		HarvestHandler.init();
		EquipSwapServer.init();
		QuirkyReloadCommand.init();
		LOGGER.info("Quirky loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
```

- [ ] **Step 6: 构建验证**

Run: `gradle build`
Expected: BUILD SUCCESSFUL（依赖下载成功；若 `ConfigHolder.load()` / `JanksonConfigSerializer` 名称不符，用 `find ~/.gradle -name "cloth-config-fabric-26.2.155.jar"` 定位 jar 后 `unzip -p` 反查类名修正）

- [ ] **Step 7: 提交**

```bash
git add gradle.properties build.gradle src/main/resources/fabric.mod.json src/main/java/dev/quirky/config/QuirkyReloadCommand.java src/main/java/dev/quirky/QuirkyMod.java
git commit -m "feat: add cloth config dependency, auto config registration and reload command"
```

---

### Task 3: 服务端机制开关（6 个入口）

**Files:**
- Modify: `src/main/java/dev/quirky/harvest/HarvestHandler.java`（onUseBlock 开头）
- Modify: `src/main/java/dev/quirky/door/DoubleDoorHandler.java`（sync 开头——被 DoubleDoorMixin 两个注入点共用，单点覆盖）
- Modify: `src/main/java/dev/quirky/item/BottledCloudItem.java`（use 开头）
- Modify: `src/main/java/dev/quirky/equip_swap/EquipSwapServer.java`（handle 开头）
- Modify: `src/main/java/dev/quirky/food/MelonSeedHandler.java`（finishUsing 开头——被 MelonSeedMixin 调用，单点覆盖）
- Modify: `src/main/java/dev/quirky/mixin/ServerPlayerMixin.java`（quirky$totemProtectInventory 开头）

**Interfaces:**
- Consumes: Task 1 的 `QuirkyConfigHolder.get()`（字段：`harvestReplant` / `doubleDoor` / `cloudBottle` / `equipSwap` / `melonSeed` / `totemOfHolding`）
- Produces: 无（开关关闭时各入口恢复原版行为）

- [ ] **Step 1: HarvestHandler.onUseBlock 加开关**

`onUseBlock` 方法 `if (level.isClientSide())` 之后加：
```java
		if (!QuirkyConfigHolder.get().harvestReplant) {
			return InteractionResult.PASS;
		}
```
（import `dev.quirky.config.QuirkyConfigHolder`）

- [ ] **Step 2: DoubleDoorHandler.sync 加开关**

`sync` 方法 `if (level.isClientSide())` 之后加：
```java
		if (!QuirkyConfigHolder.get().doubleDoor) {
			return;
		}
```

- [ ] **Step 3: BottledCloudItem.use 加开关**

`use` 方法开头（`BlockPos pos = ...` 之前）加：
```java
		if (!QuirkyConfigHolder.get().cloudBottle) {
			return InteractionResult.FAIL;
		}
```
（客户端/服务端统一检查：客户端返回 FAIL 不发使用请求，服务端同样拒绝）

- [ ] **Step 4: EquipSwapServer.handle 加开关（服务端权威）**

`handle` 方法 `context.server().execute(...)` 之前加：
```java
		if (!QuirkyConfigHolder.get().equipSwap) {
			return;
		}
```

- [ ] **Step 5: MelonSeedHandler.finishUsing 加开关**

`finishUsing` 开头加：
```java
		if (!QuirkyConfigHolder.get().melonSeed) {
			return stack.finishUsingItem(level, entity); // 恢复原版行为（不吐籽）
		}
```

- [ ] **Step 6: ServerPlayerMixin.quirky$totemProtectInventory 加开关**

`quirky$totemProtectInventory` 开头（`boolean keepInventory = ...` 之前）加：
```java
		if (!QuirkyConfigHolder.get().totemOfHolding) {
			return; // 不生成图腾，原版掉落照常
		}
```

- [ ] **Step 7: 构建 + 全量测试**

Run: `gradle build`
Expected: BUILD SUCCESSFUL，全量测试 0 失败

- [ ] **Step 8: 提交**

```bash
git add src/main/java/dev/quirky/harvest/HarvestHandler.java src/main/java/dev/quirky/door/DoubleDoorHandler.java src/main/java/dev/quirky/item/BottledCloudItem.java src/main/java/dev/quirky/equip_swap/EquipSwapServer.java src/main/java/dev/quirky/food/MelonSeedHandler.java src/main/java/dev/quirky/mixin/ServerPlayerMixin.java
git commit -m "feat: add toggle checks to all server-side mechanics"
```

---

### Task 4: 客户端机制开关（2 个）+ ModMenu 集成

**Files:**
- Modify: `src/main/java/dev/quirky/mixin/MapTooltipMixin.java`（quirky$mapTooltip 开头）
- Modify: `src/client/java/dev/quirky/client/mixin/ClockCompassTooltipMixin.java`（quirky$appendTooltip 开头）
- Create: `src/client/java/dev/quirky/client/config/ModMenuIntegration.java`

**Interfaces:**
- Consumes: Task 1 的 `QuirkyConfigHolder.get()`（字段：`mapPreview` / `clockTooltip`）；`QuirkyConfig` 类
- Produces: ModMenu GUI 入口（`AutoConfig.getConfigScreen(QuirkyConfig.class, parent)`）

- [ ] **Step 1: MapTooltipMixin 加开关**

`quirky$mapTooltip` 方法开头加：
```java
		if (!QuirkyConfigHolder.get().mapPreview) {
			return;
		}
```

- [ ] **Step 2: ClockCompassTooltipMixin 加开关**

`quirky$appendTooltip` 方法开头（`ItemStack stack = ...` 之前）加：
```java
		if (!QuirkyConfigHolder.get().clockTooltip) {
			return;
		}
```

- [ ] **Step 3: 实现 ModMenu 集成** `src/client/java/dev/quirky/client/config/ModMenuIntegration.java`

```java
package dev.quirky.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.quirky.config.QuirkyConfig;
import me.shedaniel.autoconfig.AutoConfig;

public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> AutoConfig.getConfigScreen(QuirkyConfig.class, parent).get();
	}
}
```

- [ ] **Step 4: 构建验证**

Run: `gradle build`
Expected: BUILD SUCCESSFUL（modmenu 为 compileOnly，编译期有类即可）

- [ ] **Step 5: 提交**

```bash
git add src/main/java/dev/quirky/mixin/MapTooltipMixin.java src/client/java/dev/quirky/client/mixin/ClockCompassTooltipMixin.java src/client/java/dev/quirky/client/config/ModMenuIntegration.java
git commit -m "feat: add client mechanic toggles and modmenu integration"
```

---

### Task 5: 图腾参数化（服务端 8 项 + 客户端渲染 6 项）

**Files:**
- Modify: `src/main/java/dev/quirky/totem/TotemEntity.java`（删除 8 个常量，改读配置）
- Modify: `src/client/java/dev/quirky/client/totem/TotemEntityRenderer.java`（删除 6 个常量，改读配置）

**Interfaces:**
- Consumes: Task 1 的 `QuirkyConfigHolder.get()`（字段：`hitsToRetrieve` / `hitSoundVolume` / `hitSoundPitch` / `retrieveSoundVolume` / `enchantParticleChance` / `endRodParticleChance` / `particleXzSpread` / `particleYSpread` / `modelScale` / `bobAmplitude` / `bobPeriod` / `spinPeriod` / `swayAmplitude` / `swayPeriod`）
- Produces: 无（行为不变，默认值=原常量，现有测试应原样通过）

- [ ] **Step 1: TotemEntity 删除常量区、改读配置**

删除 `// ==== 手感参数区（集中调参）====` 注释及 8 个 `private static final` 常量，方法内改为：
```java
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (!this.level().isClientSide()
			&& source.getEntity() instanceof Player player
			&& source.is(DamageTypes.PLAYER_ATTACK)) {
			QuirkyConfig config = QuirkyConfigHolder.get();
			int count = this.hits.merge(player.getUUID(), 1, Integer::sum);
			if (count >= config.hitsToRetrieve) {
				this.retrieveFor(player);
			} else {
				this.playSound(SoundEvents.AMETHYST_BLOCK_HIT, config.hitSoundVolume, config.hitSoundPitch);
			}
		}
		return false;
	}
```
`retrieveFor` 内 `playSound(SoundEvents.TOTEM_USE, RETRIEVE_SOUND_VOLUME, 1.0F)` 改为：
```java
		this.playSound(SoundEvents.TOTEM_USE, QuirkyConfigHolder.get().retrieveSoundVolume, 1.0F);
```
`tick()` 内四处 `ENCHANT_PARTICLE_CHANCE` / `END_ROD_PARTICLE_CHANCE` / `PARTICLE_XZ_SPREAD` / `PARTICLE_Y_SPREAD` 改为（方法开头取一次配置；**BoundedDiscrete 在 26.2 不强制反序列化边界，chance 必须 clamp ≥1 防 `nextInt(0)` 崩溃**）：
```java
		if (this.level() instanceof ServerLevel serverLevel) {
			QuirkyConfig config = QuirkyConfigHolder.get();
			int enchantChance = Math.max(1, config.enchantParticleChance);
			int endRodChance = Math.max(1, config.endRodParticleChance);
			if (this.random.nextInt(enchantChance) == 0) {
				serverLevel.sendParticles(
					ParticleTypes.ENCHANT,
					this.getX(),
					this.getY() + 0.3,
					this.getZ(),
					1,
					config.particleXzSpread,
					config.particleYSpread,
					config.particleXzSpread,
					0.02
				);
			}
			if (this.random.nextInt(endRodChance) == 0) {
				serverLevel.sendParticles(
					ParticleTypes.END_ROD,
					this.getX(),
					this.getY() + 0.5,
					this.getZ(),
					1,
					0.35,
					0.3,
					0.35,
					0.01
				);
			}
		}
```
import 加 `dev.quirky.config.QuirkyConfig` 和 `dev.quirky.config.QuirkyConfigHolder`。

- [ ] **Step 2: TotemEntityRenderer 删除常量区、改读配置**

删除 `// ==== 手感参数区（集中调参）====` 注释及 6 个 `private static final` 常量，`submit` 方法内改为：
```java
		if (!state.item.isEmpty()) {
			QuirkyConfig config = QuirkyConfigHolder.get();
			poseStack.pushPose();
			float bob = Mth.sin(state.ageInTicks / config.bobPeriod) * config.bobAmplitude;
			poseStack.translate(0.0F, bob, 0.0F);
			poseStack.scale(config.modelScale, config.modelScale, config.modelScale);
			poseStack.mulPose(Axis.YP.rotation(state.ageInTicks / config.spinPeriod));
			poseStack.mulPose(Axis.XP.rotation(Mth.sin(state.ageInTicks / config.swayPeriod) * config.swayAmplitude));
			state.item.submit(poseStack, submitNodeCollector, 0xF000F0, OverlayTexture.NO_OVERLAY, state.outlineColor);
			poseStack.popPose();
			super.submit(state, poseStack, submitNodeCollector, camera);
		}
```
import 加 `dev.quirky.config.QuirkyConfig` 和 `dev.quirky.config.QuirkyConfigHolder`。

- [ ] **Step 3: 运行确认现有测试仍通过**

Run: `gradle test`
Expected: 全部通过（`TotemEntityTest` 3 个测试：默认配置 `hitsToRetrieve=3`，打 3 下取回的断言不变；`QuirkyConfigDefaultsTest` 保证默认值=原常量）

- [ ] **Step 4: 构建验证**

Run: `gradle build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add src/main/java/dev/quirky/totem/TotemEntity.java src/client/java/dev/quirky/client/totem/TotemEntityRenderer.java
git commit -m "feat: read totem tuning params from config instead of constants"
```

---

### Task 6: 文档同步 + 全量验证

**Files:**
- Modify: `README.md`（范围段更新 + 配置说明章节）
- Modify: `features.md`（同步）
- Modify: `docs/26.2-mechanics-notes.md`（补 Cloth Config 适配结论）

- [ ] **Step 1: 更新 README.md**

「范围」段把：
> - 暂时没有配置界面。**计划**：后续接入 Cloth Config API，图腾/云块手感参数（音效音量与频率、粒子密度、尺寸/浮动/旋转等，现已集中为命名常量）改为游戏内可调。

改为：
> - **配置**：已接入 Cloth Config API——8 个机制开关（关掉即恢复原版行为）+ 图腾手感参数（击打次数/音效/粒子/渲染尺寸/浮动/旋转/摇摆）游戏内可调。单机/局域网在 ModMenu 里打开 Quirky 配置界面；专用服务器由服主编辑 `config/quirky.json5` 后执行 `/quirky reload` 热重载。ModMenu 为可选依赖，cloth-config 为必需依赖。

- [ ] **Step 2: 更新 features.md**

加配置说明小节（照现有风格）：
```markdown
### 9. 所有机制可开关、图腾手感可调

接入 Cloth Config API。游戏内 ModMenu → Quirky 配置界面（或编辑 `config/quirky.json5`）：
- **机制开关**：8 个机制全部可独立禁用（地图预览、收割补种、双开门、时钟信息、云瓶、背包装备替换、吃瓜吐籽、保留图腾），关掉即恢复原版行为
- **图腾参数**：击打次数、音效音量/音高、粒子频率与散布、渲染尺寸/浮动/旋转/摇摆
- 专用服务器：服主编辑服务器目录 `config/quirky.json5` 后 `/quirky reload` 热重载（需要权限等级 2）；客户端玩家修改只影响自己本地的渲染参数
```

- [ ] **Step 3: 更新 `docs/26.2-mechanics-notes.md`**

加一节：
```markdown
## 12. Cloth Config API 适配结论

- **版本**：maven `me.shedaniel.cloth:cloth-config-fabric:26.2.155`（对应 MC 26.2）；ModMenu `com.terraformersmc:modmenu:20.0.0-beta.2`
- **官方红线**："DO NOT use Auto Config / Cloth Config for server mods"——AutoConfig 无 `@ServerConfig` 注解（v26.2 源码实锤），dedicated server 行为参数由服主改配置文件决定，客户端 GUI 不推送服务端
- **双端注册**：AutoConfig 在 `ModInitializer` 注册，双端进程各自读写自己的 `config/quirky.json5`；单机/局域网共享目录，GUI 修改直接生效
- **load() 替换实例**：`ConfigHolder.load()` 会反序列化新实例替换内部引用——静态缓存持有者必须在 load 后重新注入，否则热重载失效
- **测试隔离**：配置读取走静态 holder，测试注入默认实例，不注册 AutoConfig（不碰文件系统）
```

- [ ] **Step 4: 全量构建 + 测试**

Run: `gradle clean build`
Expected: BUILD SUCCESSFUL，全量测试（现有 + QuirkyConfigDefaultsTest 4 个）0 失败

- [ ] **Step 5: 提交**

```bash
git add README.md features.md docs/26.2-mechanics-notes.md
git commit -m "docs: document cloth config integration and toggle controls"
```

- [ ] **Step 6: 桌面手动验证清单**（交给用户/桌面客户端）

1. ModMenu 打开 Quirky 配置界面，「toggles」「totem」两个分类显示正确，Slider/Tooltip 正常
2. 关掉 melonSeed → 吃西瓜不吐籽；再开 → 恢复
3. 关掉 totemOfHolding → 死亡不生成图腾；**已存在的图腾仍可击打取回**
4. 调 hitsToRetrieve=1 → 一击取回；=10 → 十击取回
5. 调 enchantParticleChance=1 → 粒子密集；=100 → 几乎无粒子
6. 调 modelScale=3 / bobAmplitude=0.5 / spinPeriod=4 → 渲染尺寸/浮动/旋转明显变化
7. 编辑 `config/quirky.json5` 后 `/quirky reload` → 行为热更新生效（无需重启）
8. dedicated server：服主改配置 + reload 生效；客户端 GUI 改动不影响服务器行为
9. 不装 ModMenu：mod 正常运行（无 GUI 入口，无崩溃）；不装 cloth-config：Fabric 阻止加载并提示
