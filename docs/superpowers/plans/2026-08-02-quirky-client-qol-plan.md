# Quirky 客户端实用功能合集 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Quirky 新增 15 个 Quark 风味的实用小功能（tooltip 扩展 / HUD 交互 / 渲染 tweak / 交互 tweak / 新方块与物品），全部可独立开关。

**Architecture:** 按现有模式扩展——tooltip 走 `TooltipComponent` + `ClientTooltipComponentCallback`（地图预览同路径）；HUD 走 `HudRenderCallback`/`Gui.render` mixin；网络通知走 payload 注册（equip_swap 模式）；新方块/物品走 `ResourceKey` + `Registry.register` + `setId`；配置全部进 `QuirkyConfig`（toggles 分类）。渲染细节（26.2 API 形态）以 `$HOME/.cache/mcsrc` 反编译源码为准，每任务先验证 API 再写实现。

**Tech Stack:** Fabric API 0.155.2+26.2 / cloth-config / modmenu / JUnit 5 + Mockito（测试）/ Java 25 / 官方映射。

## Global Constraints

- MC 26.2，Fabric，Java 25；构建：`JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk PATH=.../bin:$PATH gradle build --no-daemon --console=plain`
- 依赖仅 Fabric API + cloth-config + modmenu，**不新增任何依赖**；版本不升级。
- 注册模式：`ResourceKey.create(Registries.X, QuirkyMod.id(...))` + `Registry.register(BuiltInRegistries.X, ...)` + `Properties.setId(...)`（云瓶/云块模式）。
- **新物品必须双文件**：`assets/quirky/items/<id>.json`（`{"model": {"type": "minecraft:model", "model": "quirky:item/<id>"}}`）+ `assets/quirky/models/item/<id>.json`；方块另需 `blockstates/` + `models/block/`。对照 `bottled_cloud` 逐项核对。
- 包结构：`dev.quirky.*`（通用/服务端）、`dev.quirky.client.*`（客户端）；一个机制一个包。
- 配置读取一律走 `QuirkyConfigHolder.get().<field>`；服务端机制入口先检查开关再执行（`QuirkyMod` 现有惯例）。
- mixin 类命名 `XxxMixin`，客户端 accessor `XxxAccessor`；注入点以本地 26.2 反编译源码为准。
- 单测：`src/test/java/dev/quirky/<机制>/...Test.java`，JUnit 5 + Mockito；服务端逻辑用 `TestBootstrap.boot()`（需要组件初始化的物品调 `TestBootstrap.bindItem(item)`）；`gradle test` 必须通过。
- 渲染/视觉类机制（tooltip 绘制、HUD、镜头、模型替换）以 build + 单测（逻辑部分）+ 代码审查验证，桌面端手动验证清单见 spec 第 8 节。
- 提交前缀：`feat:` / `fix:` / `chore:` / `docs:`；每个任务一个逻辑提交。
- 客户端 config 开关默认 `true`；参数默认值见各任务。

---

### Task 1: QuirkyConfig 新增 15 个开关与参数

**Files:**
- Modify: `src/main/java/dev/quirky/config/QuirkyConfig.java`
- Test: `src/test/java/dev/quirky/config/QuirkyConfigDefaultsTest.java`

**Interfaces:**
- Produces: 以下字段（后续所有任务消费，名字必须一致）：
  - `boolean soulLighting, greenerGrass, shulkerTooltip, foodTooltip, attributeTooltip, usageTicker, deathCam, longPick, ladderSnap, offhandSwap, goldButton, ironButton, obsidianPlate, torchArrow, woodenHopper`（toggles 分类，默认 true）
  - `float grassMultiplier = 1.0F`（滑条 0.5~1.5）、`boolean grassAffectLeaves = true`
  - `int tickerHoldTicks = 50`（BoundedDiscrete 20~200）、`int tickerAnimTicks = 5`（2~20）
  - `int deathCamDuration = 50`（40~100）
  - `int pickRangeCreative = 100`（16~256）、`int pickRangeSurvival = 12`（4~64）
  - `float ladderSnapStrength = 0.5F`（滑条 0.1~1.0）

- [ ] **Step 1: 写默认值测试**

```java
// src/test/java/dev/quirky/config/QuirkyConfigDefaultsTest.java（追加测试方法）
@Test
void clientQolTogglesDefaultOn() {
    QuirkyConfig config = new QuirkyConfig();
    assertTrue(config.soulLighting && config.greenerGrass && config.shulkerTooltip
        && config.foodTooltip && config.attributeTooltip && config.usageTicker
        && config.deathCam && config.longPick && config.ladderSnap && config.offhandSwap
        && config.goldButton && config.ironButton && config.obsidianPlate
        && config.torchArrow && config.woodenHopper);
    assertEquals(100, config.pickRangeCreative);
    assertEquals(12, config.pickRangeSurvival);
    assertEquals(50, config.deathCamDuration);
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `gradle test --tests dev.quirky.config.QuirkyConfigDefaultsTest`
Expected: FAIL（字段不存在，编译错误）

- [ ] **Step 3: 实现字段**

在 `QuirkyConfig.java` 的 toggles 分类末尾追加 15 个 `@ConfigEntry.Category("toggles")` 布尔字段；新增 `client_qol` 分类（或并入 toggles 后的参数区，跟随现有 totem 参数分类风格）放参数字段，参数带 `@ConfigEntry.Gui.Tooltip` 与 `@ConfigEntry.BoundedDiscrete`/`@ConfigEntry.Gui.Slider` 注解。

- [ ] **Step 4: 运行测试确认通过**

Run: `gradle test --tests dev.quirky.config.QuirkyConfigDefaultsTest`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add src/main/java/dev/quirky/config/QuirkyConfig.java src/test/java/dev/quirky/config/QuirkyConfigDefaultsTest.java
git commit -m "feat: add config toggles and params for client QoL features"
```

---

### Task 2: 潜影盒 tooltip

**Files:**
- Create: `src/main/java/dev/quirky/tooltips/ShulkerTooltipComponent.java`
- Create: `src/client/java/dev/quirky/client/tooltips/ClientShulkerTooltipComponent.java`
- Create: `src/main/java/dev/quirky/mixin/TooltipDetailsMixin.java`（`@Mixin(Item.class)`，`getTooltipImage` HEAD 注入——与 MapTooltipMixin 同模式，独立 mixin 负责潜影盒/食物/属性三分支，MapTooltipMixin 保持地图专属）
- Modify: `src/main/resources/quirky.mixins.json`（`mixins` 数组追加 `TooltipDetailsMixin`）
- Test: `src/test/java/dev/quirky/client/tooltips/ClientShulkerTooltipComponentTest.java`

**Interfaces:**
- Consumes: `QuirkyConfigHolder.get().shulkerTooltip`、`MapTooltipMixin` 的注入点（`Item.getTooltipImage`，26.2 签名以 mcsrc 为准）
- Produces: `ShulkerTooltipComponent(ContainerComponent contents)`（record，实现 `TooltipComponent`）；`ClientShulkerTooltipComponent(ShulkerTooltipComponent)`（实现 `ClientTooltipComponent`，`getWidth/getHeight` 按 3x9 网格 = 3*16+8、9*16+8，`extractImage` 逐格 `graphics.renderItem(...)`——API 以 mcsrc 验证）

- [ ] **Step 1: 验证 26.2 API**

Run: `rg -n "getTooltipImage|renderItem" $HOME/.cache/mcsrc/net/minecraft/world/item/Item.java $HOME/.cache/mcsrc/net/minecraft/client/gui/GuiGraphicsExtractor.java`
确认：`Item.getTooltipImage(ItemStack, Item.TooltipContext)` 返回值类型、`GuiGraphicsExtractor` 绘制 ItemStack 的方法签名，记入实现。

- [ ] **Step 2: 写测试**

```java
// src/test/java/dev/quirky/client/tooltips/ClientShulkerTooltipComponentTest.java
class ClientShulkerTooltipComponentTest {
    @BeforeAll
    static void bootStrap() { TestBootstrap.boot(); }

    @Test
    void layoutIsThreeByNine() {
        ShulkerTooltipComponent component = new ShulkerTooltipComponent(ContainerComponent.EMPTY);
        ClientShulkerTooltipComponent client = new ClientShulkerTooltipComponent(component);
        Font font = mock(Font.class);
        assertEquals(3 * 16 + 8, client.getWidth(font));
        assertEquals(9 * 16 + 8, client.getHeight(font));
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

Run: `gradle test --tests dev.quirky.client.tooltips.ClientShulkerTooltipComponentTest`
Expected: FAIL（类不存在）

- [ ] **Step 4: 实现**

服务端 `ShulkerTooltipComponent`（record + `ContainerComponent`）；`TooltipDetailsMixin`：`Item.getTooltipImage` HEAD 注入（MapTooltipMixin 同款签名 `(ItemStack, CallbackInfoReturnable<Optional<TooltipComponent>>)`），开关 `shulkerTooltip` 开且 `stack.get(DataComponents.CONTAINER)` 非空 → 返回 `ShulkerTooltipComponent`；客户端转换注册进 `QuirkyModClient` 的 `ClientTooltipComponentCallback`；`ClientShulkerTooltipComponent.extractImage` 遍历容器槽，空格跳过，非空 `renderItem(stack)` + 数量绘制。

- [ ] **Step 5: 运行测试确认通过**

Run: `gradle test --tests dev.quirky.client.tooltips.ClientShulkerTooltipComponentTest`
Expected: PASS；随后 `gradle build` 通过。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/dev/quirky/tooltips src/client/java/dev/quirky/client/tooltips src/main/java/dev/quirky/mixin src/client/java/dev/quirky/client/QuirkyModClient.java src/main/resources src/client/resources src/test
git commit -m "feat: show shulker box contents in tooltip"
```

---

### Task 3: 食物 tooltip

**Files:**
- Create: `src/main/java/dev/quirky/tooltips/FoodTooltipComponent.java`
- Create: `src/client/java/dev/quirky/client/tooltips/ClientFoodTooltipComponent.java`
- Modify: `src/main/java/dev/quirky/mixin/TooltipDetailsMixin.java`（Task 2 创建，追加食物分支）
- Modify: `src/client/java/dev/quirky/client/QuirkyModClient.java`
- Test: `src/test/java/dev/quirky/client/tooltips/ClientFoodTooltipComponentTest.java`

**Interfaces:**
- Consumes: `QuirkyConfigHolder.get().foodTooltip`、`DataComponents.FOOD`（`FoodProperties`：`nutrition()`、`saturation()`）
- Produces: `FoodTooltipComponent(FoodProperties food)`；`ClientFoodTooltipComponent` 绘制行：`+N` + 鸡腿图标 + `+M` + 饱和度图标（图标 sprite 用原版 HUD hunger 图标，`Identifier` 以 mcsrc 确认）

- [ ] **Step 1: 写测试**

```java
@Test
void foodValuesReadFromComponent() {
    FoodProperties food = mock(FoodProperties.class);
    when(food.nutrition()).thenReturn(6);
    when(food.saturation()).thenReturn(7.2F);
    FoodTooltipComponent component = new FoodTooltipComponent(food);
    assertEquals(6, component.food().nutrition());
}
```

- [ ] **Step 2: 运行确认失败 → Step 3: 实现**（组件 + mixin 分支：`stack.get(DataComponents.FOOD)` 非空且开关开 → `FoodTooltipComponent`；客户端组件 `extractImage` 画图标 + 文本；无 food 组件的物品走原版）
- [ ] **Step 4: 运行确认通过**（单测 + `gradle build`）
- [ ] **Step 5: 提交** `feat: show food values in tooltip`

---

### Task 4: 属性图标 tooltip

**Files:**
- Create: `src/main/java/dev/quirky/tooltips/AttributeTooltipComponent.java`
- Create: `src/main/java/dev/quirky/tooltips/EnchantedDamageCalculator.java`（纯逻辑，可单测）
- Create: `src/client/java/dev/quirky/client/tooltips/ClientAttributeTooltipComponent.java`
- Create: 6 张图标：`src/main/resources/assets/quirky/textures/gui/quirky/attribute/{attack_damage,attack_speed,armor,toughness,knockback,movement}.png`（16x16，金色系 Quark 风格）
- Modify: `src/main/java/dev/quirky/mixin/TooltipDetailsMixin.java`（追加属性分支）
- Modify: `src/client/java/dev/quirky/client/QuirkyModClient.java`
- Test: `src/test/java/dev/quirky/tooltips/EnchantedDamageCalculatorTest.java`

**Interfaces:**
- Consumes: `QuirkyConfigHolder.get().attributeTooltip`、`DataComponents.ATTRIBUTE_MODIFIERS`、`EnchantmentEffectComponents`/`EnchantmentHelper`
- Produces:
  - `record AttributeLine(Identifier icon, String text)`
  - `AttributeTooltipComponent(List<AttributeLine> lines)`
  - `EnchantedDamageCalculator.addEnchantmentDamage(float base, ItemStack stack, HolderLookup.Provider registries)` —— 锋利/亡灵杀手/节肢杀手按 26.2 公式（每级 +0.5，具体以 mcsrc 的 `Enchantments`/伤害计算源码为准）返回最终值

- [ ] **Step 1: 写测试**

```java
// EnchantedDamageCalculatorTest
@Test
void sharpnessAddsPerLevel() {
    // 用测试物品 + Sharpness 附魔的 ItemStack（TestBootstrap.bindItem 绑定测试物品后 applyEnchantment）
    ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
    // 锋利 V：base 7 → 7 + 2.5 = 9.5（公式以 26.2 源码为准，测试值随公式调整）
    assertEquals(9.5F, EnchantedDamageCalculator.addEnchantmentDamage(7.0F, sword, registries()));
}
```

- [ ] **Step 2: 运行确认失败 → Step 3: 实现**（计算器读 `EnchantmentHelper.getItemEnchantmentLevel` 各附魔求和；组件按槽位过滤 `ATTRIBUTE_MODIFIERS` 收集攻击伤害/攻速/护甲/韧性/击退抗性/移速六项，攻击伤害过计算器；图标 sprite 引用；`ClientAttributeTooltipComponent` 逐行画 16x16 图标 + 文本；`Screen.hasShiftDown()` 时 mixin 返回 null 走原版）
- [ ] **Step 4: 运行确认通过 → Step 5: 提交** `feat: show attribute icons with real damage in tooltip`

---

### Task 5: 使用量挂件

**Files:**
- Create: `src/client/java/dev/quirky/client/usage_ticker/TickerSnapshot.java`（纯逻辑：背包快照对比，可单测）
- Create: `src/client/java/dev/quirky/client/usage_ticker/TickerElement.java`（状态机：slideIn/hold/slideOut + Quark 动画曲线）
- Create: `src/client/java/dev/quirky/client/usage_ticker/UsageTickerHud.java`（渲染 + tick 驱动）
- Create: `src/client/java/dev/quirky/client/usage_ticker/ArmorTicker.java`（护甲耐久检测）
- Create: `src/client/java/dev/quirky/client/mixin/GuiRenderMixin.java`（或走 `HudRenderCallback`，以 Fabric API 26.2 可用性为准）
- Modify: `src/client/java/dev/quirky/client/QuirkyModClient.java`、`src/main/resources/quirky.client.mixins.json`（`mixins` 数组追加新 mixin）
- Test: `src/test/java/dev/quirky/client/usage_ticker/TickerSnapshotTest.java`、`TickerElementTest.java`

**Interfaces:**
- Consumes: `QuirkyConfigHolder.get().usageTicker/tickerHoldTicks/tickerAnimTicks`
- Produces:
  - `record SlotSnapshot(int slot, Item item, int count)`；`TickerSnapshot.diff(List<SlotSnapshot> before, List<SlotSnapshot> after)` → `Optional<TickerEvent>`（`record TickerEvent(Item item, int newCount, int delta)`；变化槽 == 1 才返回，≥2 返回 empty）
  - `TickerElement`：`tick(boolean active)`、`render(GuiGraphics, int x, int y, float partialTick)`、`isVisible()`；动画 `yOffset = -p*(p-2)*20`（Quark 同款），p 由 `liveTicks` 计算（保持 `tickerHoldTicks`，动画 `tickerAnimTicks`）

- [ ] **Step 1: 写测试**

```java
// TickerSnapshotTest
@Test
void singleSlotGainReportsPickup() {
    List<SlotSnapshot> before = List.of(new SlotSnapshot(0, Items.COBBLESTONE, 10));
    List<SlotSnapshot> after = List.of(new SlotSnapshot(0, Items.COBBLESTONE, 13));
    Optional<TickerEvent> event = TickerSnapshot.diff(before, after);
    assertTrue(event.isPresent());
    assertEquals(3, event.get().delta());
    assertEquals(13, event.get().newCount());
}

@Test
void multiSlotChangeIsInventoryShuffle() {
    List<SlotSnapshot> before = List.of(new SlotSnapshot(0, Items.COBBLESTONE, 10), new SlotSnapshot(1, Items.DIRT, 5));
    List<SlotSnapshot> after = List.of(new SlotSnapshot(0, Items.DIRT, 5), new SlotSnapshot(1, Items.COBBLESTONE, 10));
    assertTrue(TickerSnapshot.diff(before, after).isEmpty());
}
```

- [ ] **Step 2: 运行确认失败 → Step 3: 实现**
  - `TickerSnapshot`：每 tick 由客户端收集 41 槽 `(itemId, count)`（主手/副手/盔甲槽含耐久另列），与上帧 diff。
  - 左侧挂件：拾取/消耗事件 → 显示图标 + 背包总数（遍历背包 `isSameItemSameComponents` 求和，`max(total, count)`）；BlockItem 不可堆叠也显示。
  - `ArmorTicker`：4 盔甲槽 damage 变化 → 右侧横排 4 条（图标 + 耐久条 `fill` 渐变）；无变化 60 tick 后 slideOut。
  - `UsageTickerHud`：`tick()` 驱动所有元素；`render` 在 `HudRenderCallback`（或 `Gui.render` mixin 尾部）绘制，位置：左/右侧对齐快捷栏高度，元素间距 20px。
- [ ] **Step 4: 运行确认通过**（单测 + build）→ **Step 5: 提交** `feat: add usage ticker HUD with slide animation`

---

### Task 6: 死亡电影镜头

**Files:**
- Create: `src/main/java/dev/quirky/deathcam/DeathCamPayload.java`
- Create: `src/main/java/dev/quirky/deathcam/DeathCamServer.java`
- Create: `src/main/java/dev/quirky/mixin/DeathCamServerMixin.java`（`@Mixin(ServerPlayer.class)`，`die` 方法 `@At("RETURN")` 注入——与现有 ServerPlayerMixin（totem，`dropAllDeathLoot` BEFORE 注入）互不干扰；quirky.mixins.json 追加）
- Create: `src/client/java/dev/quirky/client/deathcam/DeathCamClient.java`（状态机：`start(pos, yaw) / tick / active() / skip()`）
- Create: `src/client/java/dev/quirky/client/deathcam/DeathCamTimeline.java`（纯逻辑：t → 相机位置/旋转插值，可单测）
- Create: `src/client/java/dev/quirky/client/mixin/CameraSetupMixin.java`、`src/client/java/dev/quirky/client/mixin/DeathScreenDelayMixin.java`（`LocalPlayer.die`/死亡界面路径，注入点以 mcsrc 为准）
- Modify: `src/main/java/dev/quirky/QuirkyMod.java`（`DeathCamServer.init()` 追加进 `onInitialize`，EquipSwapServer.init 同款：payload 注册 + receiver）、`src/client/java/dev/quirky/client/QuirkyModClient.java`、`src/main/resources/quirky.mixins.json`、`src/main/resources/quirky.client.mixins.json`
- Test: `src/test/java/dev/quirky/client/deathcam/DeathCamTimelineTest.java`

**Interfaces:**
- Consumes: `QuirkyConfigHolder.get().deathCam/deathCamDuration`
- Produces:
  - `DeathCamPayload(Vec3 pos, float yaw, float pitch)`（`CustomPayload` + stream codec，注册到 `PayloadTypeRegistry.play()` 双端）
  - `DeathCamTimeline.position(float t)` → `Vec3`（半径 2→6 格、yaw 0→360°、pitch -10°→-25° 环绕插值）
  - `DeathCamClient`：`active()`、`tick()`、`skip()`

- [ ] **Step 1: 写测试**

```java
// DeathCamTimelineTest
@Test
void timelineCirclesDeathPoint() {
    DeathCamTimeline timeline = new DeathCamTimeline(50);
    Vec3 start = timeline.position(0.0F);
    Vec3 end = timeline.position(1.0F);
    assertEquals(2.0, start.horizontalDistance(), 0.01);
    assertEquals(6.0, end.horizontalDistance(), 0.01);
    assertTrue(end.y > start.y); // 拉远且上升
}
```

- [ ] **Step 2: 运行确认失败 → Step 3: 实现**
  - `DeathCamPayload`/`DeathCamServer`：`ServerPlayerMixin` 在 `die` 尾部发 `DeathCamPayload(player.position(), yaw, pitch)`（检查开关）。
  - 客户端注册 receiver：`DeathCamClient.start(...)`（仅自己死亡时，非旁观）。
  - `CameraSetupMixin`：`DeathCamClient.active()` 时用 `DeathCamTimeline.position(progress)` 覆盖相机位置/旋转（第三人称视角渲染）。
  - `DeathScreenDelayMixin`：死亡到进死亡界面之间延迟 `deathCamDuration` tick（`LocalPlayer` 死亡→`Minecraft` 显示 DeathScreen 的路径，注入点以 mcsrc 为准）；期间 `KeyboardHandler`/`MouseHandler` 忽略视角输入，Esc 调 `DeathCamClient.skip()` 立即进入死亡界面；镜头结束自动进入。
  - 安全退出：切换维度/断线/重生时 `active()` 置 false。
- [ ] **Step 4: 运行确认通过**（单测 + build）→ **Step 5: 提交** `feat: play cinematic death camera before death screen`

---

### Task 7: 灵魂光源

**Files:**
- Create: `src/client/java/dev/quirky/client/soul_lighting/SoulLightingHelper.java`（纯逻辑：判定 + 模型 key 映射，可单测）
- Create: `src/client/java/dev/quirky/client/soul_lighting/SoulLightingModels.java`（缓存 soul torch/lantern/蜡烛模型引用）
- Create: `src/client/java/dev/quirky/client/mixin/BlockModelShaperMixin.java`（或等价模型选择点，以 mcsrc 为准）
- Create: `src/client/java/dev/quirky/client/mixin/FlameParticleMixin.java`（火焰粒子换 sprite）
- Create: `src/main/resources/assets/quirky/models/block/soul_candle.json`、`soul_candle_lit.json`（复制原版蜡烛模型 + 火焰纹理换 `textures/block/quirky_soul_candle_flame.png`）
- Create: `src/main/resources/assets/quirky/textures/block/quirky_soul_candle_flame.png`（16x16 青色火焰，自绘）
- Modify: `src/main/resources/quirky.client.mixins.json`
- Test: `src/test/java/dev/quirky/client/soul_lighting/SoulLightingHelperTest.java`

**Interfaces:**
- Consumes: `QuirkyConfigHolder.get().soulLighting`、`Blocks.SOUL_SAND/SOUL_SOIL`
- Produces: `SoulLightingHelper.resolve(BlockState state, BlockState below)` → `@Nullable Identifier`（torch/wall_torch → `minecraft:block/soul_torch`；lantern → `minecraft:block/soul_lantern`；candle/candle_lit → `quirky:block/soul_candle[_lit]`；其余 null）

- [ ] **Step 1: 写测试**

```java
@Test
void torchOnSoulSandResolvesToSoulTorch() {
    assertEquals(Identifier.of("minecraft:block/soul_torch"),
        SoulLightingHelper.resolve(Blocks.TORCH.defaultBlockState(), Blocks.SOUL_SAND.defaultBlockState()));
}
@Test
void torchOnDirtStaysVanilla() {
    assertNull(SoulLightingHelper.resolve(Blocks.TORCH.defaultBlockState(), Blocks.DIRT.defaultBlockState()));
}
@Test
void candleOnSoulSoilResolvesToCustom() {
    assertNotNull(SoulLightingHelper.resolve(Blocks.CANDLE.defaultBlockState(), Blocks.SOUL_SOIL.defaultBlockState()));
}
```

- [ ] **Step 2: 运行确认失败 → Step 3: 实现**
  - `SoulLightingHelper.resolve`：方块 ∈ {torch, wall_torch, lantern, candle, candle_lit, 各色蜡烛} 且正下方（y-1）∈ {soul_sand, soul_soil} → 返回灵魂模型 id。
  - `BlockModelShaperMixin`（`getBlockModel`）：返回前检查——开关开且 `resolve` 非空 → 用 `ModelManager` 的对应 `BakedModel` 替换；销毁后自动恢复（按下方方块动态判定，无状态）。
  - `FlameParticleMixin`：火把/蜡烛粒子创建时检查所在方块下方是否灵魂方块 → sprite 换 `soul_fire_flame`。
  - 蜡烛自定义模型：复制 26.2 原版 candle 模型结构（以 mcsrc/资源为准），火焰面纹理替换为自绘青色火焰。
- [ ] **Step 4: 运行确认通过**（单测 + build + 桌面视觉验证清单）→ **Step 5: 提交** `feat: torch lantern candle turn soul on soul sand`

---

### Task 8: 草地增绿

**Files:**
- Create: `src/main/java/dev/quirky/client_color/GrassColorMatrix.java`（纯逻辑：3x3 矩阵卷积，可单测）
- Create: `src/client/java/dev/quirky/client/greener_grass/GreenerGrassClient.java`（Fabric `ClientBlockColorProviderCallback` 注册包装 provider）
- Modify: `src/client/java/dev/quirky/client/QuirkyModClient.java`
- Test: `src/test/java/dev/quirky/client_color/GrassColorMatrixTest.java`

**Interfaces:**
- Consumes: `QuirkyConfigHolder.get().greenerGrass/grassMultiplier/grassAffectLeaves`
- Produces: `GrassColorMatrix.convolve(int argb)` → int；默认矩阵 `[0.89,0,0, 0,1.11,0, 0,0,0.89]`，对角项乘以 `grassMultiplier`（1.0 = Quark 默认）

- [ ] **Step 1: 写测试**

```java
@Test
void defaultMatrixGreensUp() {
    GrassColorMatrix matrix = new GrassColorMatrix(1.0F);
    int color = 0xFF91BD59;
    int out = matrix.convolve(color);
    // R*0.89、G*1.11、B*0.89（每通道 clamp 0~255）
    assertEquals(0xFF, (out >> 24) & 0xFF);
    assertEquals(Math.round(0x91 * 0.89), (out >> 16) & 0xFF);
    assertEquals(Math.min(255, Math.round(0xBD * 1.11)), (out >> 8) & 0xFF);
}
```

- [ ] **Step 2: 运行确认失败 → Step 3: 实现**
  - `GrassColorMatrix.convolve`：ARGB 分解，RGB 乘矩阵（对角矩阵简化：R'=R*rr, G'=G*gg, B'=B*bb），clamp 255。
  - `GreenerGrassClient`：`ClientBlockColorProviderCallback`（26.2 Fabric API 名称以 mcsrc/API 验证）为草方块/短草/蕨/大型蕨/甘蔗/盆栽蕨 + 树叶/藤蔓（`grassAffectLeaves`）注册包装 provider：原 provider 取色 → 开关开时 `convolve`。包装需在首次 tick 后注册（给其他 mod 注册机会，Quark 同策略）。
- [ ] **Step 4: 运行确认通过**（单测 + build）→ **Step 5: 提交** `feat: greener grass color matrix option`

---

### Task 9: 远距中键拾取

**Files:**
- Create: `src/client/java/dev/quirky/client/pick_range/PickRangeHelper.java`（纯逻辑：距离选择 + 射线，可单测距离部分）
- Create: `src/client/java/dev/quirky/client/mixin/PickBlockMixin.java`（`Minecraft.pickBlock`，注入点以 mcsrc 为准）
- Modify: `src/main/resources/quirky.client.mixins.json`
- Test: `src/test/java/dev/quirky/client/pick_range/PickRangeHelperTest.java`

**Interfaces:**
- Consumes: `QuirkyConfigHolder.get().longPick/pickRangeCreative/pickRangeSurvival`
- Produces: `PickRangeHelper.rangeFor(boolean creative)` → int；`PickRangeHelper.isEnabled(boolean creative)`（范围 > 原版才启用）

- [ ] **Step 1: 写测试**

```java
@Test
void creativeRangeIsConfigValue() {
    assertEquals(100, PickRangeHelper.rangeFor(true));
    assertEquals(12, PickRangeHelper.rangeFor(false));
}
```

- [ ] **Step 2: 运行确认失败 → Step 3: 实现**
  - `PickBlockMixin`：`pickBlock` 开头，开关开且目标超出原版范围时，用 `rangeFor` 重新 `level.clip`（BlockRaycast，忽略流体同原版），命中结果替换本地 hitResult 后继续原逻辑；未命中/更远无目标时走原版。
- [ ] **Step 4: 运行确认通过**（单测 + build + 桌面验证）→ **Step 5: 提交** `feat: extend middle-click pick block range`

---

### Task 10: 爬梯吸附

**Files:**
- Create: `src/client/java/dev/quirky/client/ladder_snap/LadderSnapHelper.java`（纯逻辑：吸附修正向量，可单测）
- Create: `src/client/java/dev/quirky/client/mixin/LocalPlayerAIStepMixin.java`（`LocalPlayer.aiStep`，注入点以 mcsrc 为准）
- Modify: `src/main/resources/quirky.client.mixins.json`
- Test: `src/test/java/dev/quirky/client/ladder_snap/LadderSnapHelperTest.java`

**Interfaces:**
- Consumes: `QuirkyConfigHolder.get().ladderSnap/ladderSnapStrength`
- Produces: `LadderSnapHelper.correction(double playerX, double playerZ, double centerX, double centerZ, double strength)` → `Vec2`（指向中心的修正速度分量；偏移 < 0.01 时返回零向量）

- [ ] **Step 1: 写测试**

```java
@Test
void correctionPointsToLadderCenter() {
    Vec2 correction = LadderSnapHelper.correction(0.3, 0.0, 0.5, 0.5, 0.5F);
    assertTrue(correction.x > 0); // 向 +x 中心修正
    assertTrue(correction.y > 0);
}
@Test
void centeredPlayerGetsNoCorrection() {
    Vec2 correction = LadderSnapHelper.correction(0.5, 0.5, 0.5, 0.5, 0.5F);
    assertEquals(0.0, correction.x, 1e-6);
}
```

- [ ] **Step 2: 运行确认失败 → Step 3: 实现**
  - `LocalPlayerAIStepMixin`：`climbing()` 为真、`left/right` 输入为 0、开关开 → 计算所在方块（`blockPosition` 附近含梯子的方块）中心，`player.setDeltaMovement` 的 x/z 叠加 `correction`（clamp 到当前速度量级，防瞬移）。
- [ ] **Step 4: 运行确认通过**（单测 + build + 桌面手感验证）→ **Step 5: 提交** `feat: snap player to ladder center when not steering`

---

### Task 11: 装备替换·副手扩展

**Files:**
- Modify: `src/client/java/dev/quirky/client/equip_swap/EquipSwapClient.java`
- Modify: `src/main/java/dev/quirky/equip_swap/EquipSwapServer.java`
- Modify: `src/main/java/dev/quirky/config/QuirkyConfig.java`（`offhandSwap` 已含于 Task 1）
- Test: `src/test/java/dev/quirky/equip_swap/EquipSwapServerTest.java`（追加）

**Interfaces:**
- Consumes: `QuirkyConfigHolder.get().equipSwap/offhandSwap`
- Produces: `EquipSwapServer.trySwap` 扩展：目标槽 = 副手（`inventoryIndexFor` 增加 `OFFHAND → 40`；`player.getInventory().getItem(40)`）；判定 `isOffhandSwapItem(stack)`：`Items.SHIELD` 或 `Items.TORCH`

- [ ] **Step 1: 写测试**

```java
@Test
void shieldSwapsIntoOffhand() {
    ServerPlayer player = creativePlayer();
    ItemStack shield = new ItemStack(Items.SHIELD);
    player.getInventory().setItem(9, shield);
    when(player.getEquipmentSlotForItem(shield)).thenReturn(EquipmentSlot.OFFHAND);
    when(player.isEquippableInSlot(shield, EquipmentSlot.OFFHAND)).thenReturn(true);

    assertTrue(EquipSwapServer.trySwap(player, 0, 9));
    assertEquals(shield, player.getInventory().getItem(40));
    assertTrue(player.getInventory().getItem(9).isEmpty());
}

@Test
void torchSwapsIntoOffhand() {
    ServerPlayer player = creativePlayer();
    ItemStack torch = new ItemStack(Items.TORCH);
    player.getInventory().setItem(9, torch);
    assertTrue(EquipSwapServer.trySwap(player, 0, 9));
    assertEquals(torch, player.getInventory().getItem(40));
}
```

- [ ] **Step 2: 运行确认失败 → Step 3: 实现**
  - 服务端：`trySwap` 中当 `isOffhandSwapItem(stack)` 时目标槽强制 `OFFHAND`（绕过 `getEquipmentSlotForItem` 对火把返回 null 的问题），并跳过 `EQUIPPABLE` 组件检查；交换逻辑复用现有 `setByPlayer` 路径。
  - 客户端：右键拦截条件加入 `isOffhandSwapItem`（`Items.SHIELD`/`Items.TORCH`）。
- [ ] **Step 4: 运行确认通过**（追加测试 + build）→ **Step 5: 提交** `feat: right-click shield or torch into offhand`

---

### Task 12: 金按钮与铁按钮

**Files:**
- Create: `src/main/java/dev/quirky/block/MetalButtonBlock.java`
- Modify: `src/main/java/dev/quirky/ModBlocks.java`、`ModItems.java`
- Create: `src/main/resources/data/quirky/recipe/gold_button.json`、`iron_button.json`
- Create: 资源（金/铁各一份）：`blockstates/gold_button.json`、`models/block/gold_button.json`、`models/item/gold_button.json`、`items/gold_button.json`、`textures/block/gold_button.png`（iron 同理）
- Modify: `src/main/resources/assets/quirky/lang/en_us.json`、`zh_cn.json`
- Test: `src/test/java/dev/quirky/block/MetalButtonBlockTest.java`

**Interfaces:**
- Consumes: `QuirkyConfigHolder.get().goldButton/ironButton`（服务端入口检查开关）
- Produces: `MetalButtonBlock extends ButtonBlock`（构造传入 `BlockSetType` 与 `holdTicks`）；`ModBlocks.GOLD_BUTTON`（holdTicks=2）、`IRON_BUTTON`（holdTicks=100）；BlockItem 注册 + `CreativeModeTabs.REDSTONE_BLOCKS`

- [ ] **Step 1: 写测试**

```java
@Test
void goldButtonPulseIsTwoTicks() {
    // 通过反射或公开 getter 验证 holdTicks（26.2 ButtonBlock 的私有字段以 mcsrc 为准）
    assertEquals(2, MetalButtonBlock.holdTicksOf(ModBlocks.GOLD_BUTTON));
    assertEquals(100, MetalButtonBlock.holdTicksOf(ModBlocks.IRON_BUTTON));
}
```

- [ ] **Step 2: 运行确认失败 → Step 3: 实现**
  - `MetalButtonBlock`：`ButtonBlock` 子类暴露 `holdTicks`（26.2 `ButtonBlock` 构造 `(BlockSetType, int holdTicks, Properties)`，字段名以 mcsrc 为准）；新建金属 `BlockSetType`（金属音效、`canOpenByHand=false`，箭不触发）。
  - 注册：`ModBlocks`（`BlockBehaviour.Properties.of().setId(...).strength(...)`）+ `ModItems` BlockItem + 创造页签；配方：金 = `#minecraft:wooden_buttons` + `minecraft:gold_nugget`（shapeless）；铁 = `minecraft:stone_button` + `minecraft:iron_nugget`。
  - 纹理自绘（金色/铁色钮体，16x16）；blockstate 变体 `facing/powered` 参照原版按钮。
- [ ] **Step 4: 运行确认通过**（单测 + build + 服务端冒烟：`give` 按钮、点击验证时长）→ **Step 5: 提交** `feat: add gold and iron buttons`

---

### Task 13: 黑曜石压力板

**Files:**
- Create: `src/main/java/dev/quirky/block/ObsidianPressurePlateBlock.java`
- Modify: `src/main/java/dev/quirky/ModBlocks.java`、`ModItems.java`
- Create: `src/main/resources/data/quirky/recipe/obsidian_pressure_plate.json`
- Create: 资源：`blockstates/obsidian_pressure_plate.json`、`models/block/...`、`models/item/...`、`items/...`、`textures/block/obsidian_pressure_plate.png`
- Modify: lang 文件
- Test: `src/test/java/dev/quirky/block/ObsidianPressurePlateBlockTest.java`

**Interfaces:**
- Consumes: `QuirkyConfigHolder.get().obsidianPlate`
- Produces: `ObsidianPressurePlateBlock extends PressurePlateBlock`，实体判定仅 `Player`（覆写 `getSignalForState`/实体列表收集处，26.2 方法名以 mcsrc 为准）

- [ ] **Step 1: 写测试**

```java
@Test
void onlyPlayersTrigger() {
    ObsidianPressurePlateBlock plate = ModBlocks.OBSIDIAN_PLATE;
    // 用 mock Level/实体列表：Player → 信号 15；Cow → 0（判定方法以 mcsrc 为准）
    assertTrue(plate.signalFor(playersOnlyEntityList()));
}
```

- [ ] **Step 2: 运行确认失败 → Step 3: 实现**
  - `PressurePlateBlock` 子类覆写实体检测：`level.getEntitiesOfClass(...)` 过滤 `entity instanceof Player`；配方 2 黑曜石横排；黑曜石质感纹理（深紫黑 + 斑点）。
- [ ] **Step 4: 运行确认通过**（单测 + build + 服务端冒烟）→ **Step 5: 提交** `feat: add obsidian pressure plate (player only)`

---

### Task 14: 火把箭

**Files:**
- Create: `src/main/java/dev/quirky/torch_arrow/TorchArrowEntity.java`（extends `Arrow`）
- Create: `src/main/java/dev/quirky/torch_arrow/TorchArrowItem.java`（extends `ArrowItem`）
- Modify: `src/main/java/dev/quirky/ModEntities.java`（追加 `TORCH_ARROW`：`EntityType.Builder.of(TorchArrowEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).build(TORCH_ARROW_ID)`，ModEntities 现有 TOTEM 同款模式）、`ModItems.java`
- Create: `src/client/java/dev/quirky/client/torch_arrow/TorchArrowRenderer.java`（extends `ArrowRenderer`，箭头处叠加火把渲染）
- Create: `src/main/resources/data/quirky/recipe/torch_arrow.json`
- Create: 资源：`items/torch_arrow.json`、`models/item/torch_arrow.json`、`textures/item/torch_arrow.png`（自绘箭+火把）
- Modify: `src/client/java/dev/quirky/client/QuirkyModClient.java`、lang
- Test: `src/test/java/dev/quirky/torch_arrow/TorchArrowEntityTest.java`

**Interfaces:**
- Consumes: `QuirkyConfigHolder.get().torchArrow`（服务端入口检查）
- Produces: `TorchArrowEntity`：`onHitBlock` 放置火把（`Blocks.TORCH`，可替换方块才放，否则 `spawnAtLocation`）、`onHitEntity` `igniteForSeconds(3)`；`TorchArrowItem.createArrow(Level, ItemStack, LivingEntity)` 返回实体；配方 1 火把 + 1 箭（shapeless，输出 1）

- [ ] **Step 1: 写测试**

```java
@Test
void hitBlockPlacesTorchOnReplaceable() {
    // mock Level/BlockHitResult：目标为空气 → 设置火把（验证 setBlockAndUpdate 调用 + 粒子/音效）
    TorchArrowEntity arrow = new TorchArrowEntity(EntityType.ARROW, level);
    arrow.onHitBlockResult(blockHitResult(air()));
    verify(level).setBlockAndUpdate(eq(pos), any(BlockState.class));
}
```

- [ ] **Step 2: 运行确认失败 → Step 3: 实现**
  - 实体：继承 `Arrow`，构造用 `EntityType`（注册 `ModEntities.TORCH_ARROW`，`FabricDefaultAttributeRegistry` 不需要——非 LivingEntity）；覆写命中逻辑（`onHitBlock`/`onHitEntity` 26.2 方法名以 mcsrc 为准）；基础伤害 1.0；`pickup` 行为同箭。
  - 渲染：`TorchArrowRenderer` 复用箭模型 + 火把 item 叠加（`ItemRenderer`），26.2 API 以 mcsrc 验证；不可行则退化为纯箭模型（spec 5.14 风险条款）。
  - 配方/资源/lang；`TorchArrowItem` 进 `CreativeModeTabs.COMBAT`。
- [ ] **Step 4: 运行确认通过**（单测 + build + 服务端冒烟：射僵尸点燃、射方块放火把、发射器）→ **Step 5: 提交** `feat: add torch arrow`

---

### Task 15: 木漏斗

**Files:**
- Create: `src/main/java/dev/quirky/block/WoodenHopperBlock.java`（extends `HopperBlock`）
- Create: `src/main/java/dev/quirky/block/be/WoodenHopperBlockEntity.java`（extends `HopperBlockEntity`）
- Modify: `src/main/java/dev/quirky/ModBlocks.java`、`ModItems.java`（含 `fuel` 组件）
- Create: `src/main/resources/data/quirky/recipe/wooden_hopper.json`
- Create: 资源：`blockstates/wooden_hopper.json`、`models/block/wooden_hopper.json`、`models/item/wooden_hopper.json`、`items/wooden_hopper.json`、`textures/block/wooden_hopper.png`（木纹漏斗，含顶面纹理 `wooden_hopper_top.png` 等，参照原版 hopper 纹理命名）
- Modify: `src/client/java/dev/quirky/client/QuirkyModClient.java`（BE 渲染器不需要——HopperBlockEntity 无 BESR，客户端不需要注册）、lang
- Test: `src/test/java/dev/quirky/block/WoodenHopperBlockEntityTest.java`

**Interfaces:**
- Consumes: `QuirkyConfigHolder.get().woodenHopper`
- Produces: `WoodenHopperBlockEntity`：`transferCooldown` 基数为 32（覆写冷却初始化处，26.2 `HopperBlockEntity` 方法名以 mcsrc 为准）、锁定判断恒 false（红石锁不住）；`WoodenHopperBlock` 注册 `BlockEntityType`（`ModBlocks` 或新 `ModBlockEntityTypes`）

- [ ] **Step 1: 写测试**

```java
@Test
void cooldownIsFourTimesVanilla() {
    // 验证冷却字段初始值 = 32（以 mcsrc 确认字段/方法后断言）
    WoodenHopperBlockEntity hopper = new WoodenHopperBlockEntity(pos, state);
    assertEquals(32, hopper.quirky$transferCooldown());
}
```

- [ ] **Step 2: 运行确认失败 → Step 3: 实现**
  - BE：`transferCooldown` 初始化 32（覆写 `slimTick`/`pushAndPullItems` 前的冷却逻辑，以 mcsrc 为准）；`isPowered`（或 `getBlockState().getValue(ENABLED)` 检查处）恒 false。
  - 方块：`HopperBlock` 子类，`BlockEntityType` 注册 + `newBlockEntity`；配方 5 木板 + 箱子（原版漏斗形状）；物品 `fuel` 组件 300 tick；木纹纹理。
- [ ] **Step 4: 运行确认通过**（单测 + build + 服务端冒烟：4 倍慢传输、红石不锁）→ **Step 5: 提交** `feat: add wooden hopper (slower, redstone-immune)`

---

## 执行顺序与依赖

- Task 1 先行（所有任务消费 config 字段）。
- Task 2 → 3 → 4 顺序执行（共享 `getTooltipImage` mixin，Task 2 建立注入点）。
- Task 5、6 独立；Task 7、8 独立；Task 9、10、11 独立（11 依赖 1 的 `offhandSwap`）。
- Task 12、13、14、15 相互独立，可并行（共享 ModBlocks/ModItems/lang 文件，**同一工作区禁止并行修改同一文件**，需串行或分文件提交）。
- 每个任务结束 `gradle build` 通过 + `gradle test` 通过；视觉类任务附桌面验证清单（spec 第 8 节）。

## Self-Review 记录

- **Spec 覆盖**：15 项 → Task 2/3/4（tooltip）、5/6（HUD）、7/8（渲染）、9/10/11（交互）、12/13/14/15（内容）；spec 第 6 节配置 → Task 1；spec 第 7 节资源清单分布在对应任务；验收标准 → 各任务 Step 4。
- **占位符检查**：无 TBD；"以 mcsrc 为准"为 26.2 API 验证动作（项目既有惯例，非占位符）；测试代码均为可编译骨架，实现步骤给出类/方法/算法要点。
- **类型一致性**：config 字段名在 Task 1 锁定并在全部任务中复用同一标识符；`TickerEvent`/`SlotSnapshot`/`AttributeLine`/`DeathCamPayload` 等跨任务接口在 Produces 中统一定义。
