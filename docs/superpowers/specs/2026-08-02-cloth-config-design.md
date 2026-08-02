# Quirky Cloth Config 适配设计文档

日期：2026-08-02
状态：已确认（用户逐节批准）

## 1. 目标

接入 Cloth Config API，把 Quirky 的机制手感参数从硬编码命名常量改为游戏内可调（GUI + 配置文件），并提供**机制总开关**，让不想改变原版行为的玩家可以逐项禁用。对应 README「范围」里的既定计划（"接入 Cloth Config API，图腾手感参数改为游戏内可调"）。

## 2. 范围

**做**：
- Cloth Config API（AutoConfig + Jankson）配置系统：`config/quirky.json5`
- 配置 GUI（ModMenu 集成，ModMenu 为可选依赖）
- 8 个机制开关（全部机制可禁用）
- 图腾 8 个服务端手感参数 + 2 个客户端渲染参数
- `/quirky reload` 命令（服务端热重载，权限等级 2）
- 配置默认值对照测试（防行为漂移）
- README / features 文档同步

**不做（非目标）**：
- 云块 `CloudPlacement.STEP`（放置步进，非手感参数，无可调价值）
- 配置网络同步（ConfigSync）——dedicated server 行为由服主配置文件决定，客户端 GUI 不推送服务端
- 其他机制的参数化（只做图腾；收割/双开门等无手感参数需求）
- 每玩家独立配置

## 3. 配置归属模型（关键决策）

Quirky 是**双端 mod**（客户端渲染/交互 + 服务端机制逻辑），AutoConfig 在 `ModInitializer`（`onInitialize`，双端进程都会执行）注册一次：

- **客户端进程**：持有自己的配置实例，读写客户端本地 `config/quirky.json5`；GUI 修改保存到本地
- **服务端进程**：持有自己的配置实例，读写服务端 `config/quirky.json5`
- **单机/局域网（集成服务器）**：客户端与服务端共享同一 config 目录 → GUI 修改直接生效
- **dedicated server**：服务端行为参数由服主编辑服务器目录配置文件决定；客户端玩家 GUI 只影响本地渲染参数——规避官方 "DO NOT use Auto Config for server mods" 警告的场景（客户端改了期望影响服务器）

**读取路径**：各机制入口通过轻量静态 holder（`QuirkyConfigHolder.get()`）读取；注册时把 AutoConfig 实例注入 holder。测试环境不注册 AutoConfig（不碰文件系统），注入默认实例。

## 4. 配置项规格

### 4.1 toggles 分类（机制开关，8 个 boolean，默认全开）

| 字段 | 机制 | 检查点（开关关闭时的行为） |
|---|---|---|
| mapPreview | 地图悬浮预览（客户端） | `MapTooltipMixin` 不附加 tooltip |
| harvestReplant | 右键收割补种（服务端） | `HarvestHandler` 回调直接 return |
| doubleDoor | 双开门联动（服务端） | `DoubleDoorMixin` 直接 return |
| clockTooltip | 时钟/指南针悬浮信息（客户端） | `ClockCompassTooltipMixin` 直接 return |
| cloudBottle | 云瓶（服务端） | `BottledCloudItem.use` 返回失败（不放置） |
| equipSwap | 背包右键装备替换（服务端权威） | `EquipSwapServer.handle` 收到包直接忽略 |
| melonSeed | 吃西瓜吐籽（服务端） | `MelonSeedMixin` 直接 return |
| totemOfHolding | 保留图腾（服务端） | `ServerPlayerMixin` 死亡拦截直接 return（不生成） |

**开关语义（边界明确）**：
- 图腾开关只影响"死亡时是否生成"；**已存在的图腾实体照常可击打取回**（不困死玩家物品）
- 云瓶开关影响"能否放置"；已存在的云块不处理
- 装备替换开关在服务端 `handle` 入口检查（权威，对所有玩家统一）；客户端发包前不检查（省流量优化非必要，保持简单）
- 开关检查放在 mixin/入口薄层，纯逻辑类（`TotemOfHoldingLogic` 等）不感知配置

### 4.2 totem 分类（图腾手感参数，10 项）

| 字段 | 默认 | 范围 | 类型 | 说明 |
|---|---|---|---|---|
| hitsToRetrieve | 3 | 1–10 | IntSlider | 击打取回所需次数 |
| hitSoundVolume | 1.0 | 0–4 | FloatSlider | 击打反馈音音量（>1 只拉长衰减距离，响度 clamp 1.0） |
| hitSoundPitch | 1.0 | 0.5–2 | FloatSlider | 击打反馈音高 |
| retrieveSoundVolume | 0.5 | 0–2 | FloatSlider | 取回音效音量 |
| enchantParticleChance | 4 | **1–100** | IntSlider | 紫符文粒子每 tick 1/N 概率（调大更稀）；**min=1：`random.nextInt(0)` 抛异常** |
| endRodParticleChance | 12 | **1–100** | IntSlider | 白光点粒子每 tick 1/N 概率；同上 |
| particleXzSpread | 0.45 | 0–2 | FloatSlider | 紫符文 XZ 散布半径（格） |
| particleYSpread | 0.55 | 0–2 | FloatSlider | 紫符文 Y 散布高度（格） |
| floatHeight | 0.15 | 0–1 | FloatSlider | 客户端渲染：浮动基准高度（振幅固定 0.1） |
| spinSpeed | 0.05 | 0–0.5 | FloatSlider | 客户端渲染：旋转角速度（rad/tick） |

默认值与现有硬编码常量一一对应（3 / 1.0 / 1.0 / 0.5 / 4 / 12 / 0.45 / 0.55）。

## 5. 技术实现

### 5.1 依赖（版本已查证）

```groovy
repositories {
    maven { url "https://maven.shedaniel.me/" }
    maven { url "https://maven.terraformersmc.com/releases/" }
}
dependencies {
    modApi("me.shedaniel.cloth:cloth-config-fabric:26.2.155") { exclude(group: "net.fabricmc.fabric-api") }
    modCompileOnly("com.terraformersmc:modmenu:20.0.0-beta.2")
}
```

`fabric.mod.json`：
- `"depends"` 加 `"cloth-config": ">=26.2.155"`
- `"suggests"` 加 `"modmenu"`
- `"entrypoints"` 加 `"modmenu"` → 客户端 `ModMenuIntegration`

### 5.2 文件清单

**新增**：
- `src/main/java/dev/quirky/config/QuirkyConfig.java` — `@Config(name = "quirky")`，`implements ConfigData`；`toggles` / `totem` 两个 `@ConfigEntry.Category` 分类；数值项带 `@ConfigEntry.BoundedAbove/Below` + `@ConfigEntry.Gui.Tooltip` + Slider 注解
- `src/main/java/dev/quirky/config/QuirkyConfigHolder.java` — 静态 holder（`get()` 默认实例 / `set()` 注入 AutoConfig 实例）
- `src/main/java/dev/quirky/config/QuirkyReloadCommand.java` — `/quirky reload`：`ConfigHolder.load()`，权限等级 2，失败回退默认并提示
- `src/client/java/dev/quirky/client/config/ModMenuIntegration.java` — `implements ModMenuApi`，`AutoConfig.getConfigScreen(QuirkyConfig.class, parent)`
- `src/test/java/dev/quirky/config/QuirkyConfigDefaultsTest.java` — 默认值对照测试

**改动**：
- `build.gradle`、`gradle.properties`（版本常量）、`fabric.mod.json`
- `QuirkyMod.java` — 注册 AutoConfig + holder 注入 + reload 命令
- 8 个机制入口加开关检查（见 4.1 表）
- `TotemEntity.java` — 8 个常量改读 `QuirkyConfigHolder.get()`（每次使用处读取，天然支持热重载）
- `TotemEntityRenderer.java` — 浮动/旋转硬编码改读配置（`floatHeight` / `spinSpeed`）
- `README.md` / `features.md` — 配置说明章节
- `docs/26.2-mechanics-notes.md` — 补充 Cloth Config 26.2 适配结论（AutoConfig 双端注册、无 @ServerConfig 注解、reload 必要性）

### 5.3 测试策略

- `QuirkyConfigDefaultsTest`：断言默认值 == 现有常量值（防未来改默认值导致行为漂移）；顺带验证范围边界（chance ≥ 1 等）
- 现有 `TotemOfHoldingLogicTest` / `TotemEntityTest`：`@BeforeEach` 里 `QuirkyConfigHolder.set(new QuirkyConfig())` 注入默认实例，保持原断言不变
- 开关逻辑不单测（薄层判断，随构建验证）

### 5.4 验证

- `gradle build` BUILD SUCCESSFUL + 全量测试通过
- 手动验证清单（桌面客户端）：
  1. ModMenu 打开 Quirky 配置界面，两个分类显示正确
  2. 关掉 melonSeed → 吃西瓜不吐籽；再开 → 恢复
  3. 关掉 totemOfHolding → 死亡不生成图腾；已有图腾仍可击打取回
  4. 调 hitsToRetrieve=1 → 一击取回；hitsToRetrieve=10 → 十击取回
  5. 调粒子频率/散布 → 图腾粒子密度变化
  6. 调 floatHeight/spinSpeed → 渲染浮动/旋转变化
  7. `/quirky reload` 改配置文件后热重载生效
  8. dedicated server：服主改配置文件 → reload → 服务端行为变化；客户端 GUI 改动不影响服务器行为

## 6. 风险与边界

- **Cloth Config 26.2 兼容性**：maven 有 `26.2.155`，v26.2 分支源码确认存在（`@Config`/`@ConfigEntry` 无 `@ServerConfig`）；API 若有出入以 `$HOME/.cache/mcsrc` 或反编译 jar 为准修正
- **ModMenu 版本**：`20.0.0-beta.2`（v26.2 gradle.properties 实锤）；`modCompileOnly` + `suggests`，不装 ModMenu 不影响 mod 本体
- **AutoConfig 文件损坏**：回退默认值 + 日志，不崩溃
- **热重载与现存实体**：图腾每次使用时读取配置值，reload 即时生效；无状态缓存问题
