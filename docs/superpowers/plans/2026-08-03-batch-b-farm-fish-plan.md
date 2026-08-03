# 批 B 实现计划 — 田园渔猎

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现批 B 两项机制——播种袋（泛化种植袋）与鱼饵球（打窝加速咬钩）。均为"实用省时间、不破平衡"的田园/钓鱼辅助。

**Architecture:** 播种袋是纯交互物品（无 mixin，`Item.useOn` 扫描+种植）。鱼饵球是投掷物品+诱鱼区实体+`FishingHook` mixin（咬钩倒计时额外递减）。扫描/选种与区域判定抽纯函数单测。

**Tech Stack:** Fabric/Minecraft 26.2 official mappings, Java 25, JUnit 5, Mockito, system Gradle。构建命令：`JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk PATH=...:$PATH gradle build --no-daemon --console=plain`。

**对应设计文档：** `docs/superpowers/specs/2026-08-03-batch-b-farm-fish-design.md`（含全部边界场景与音效表，实现前先读）。

## Global Constraints

- Work only in `/data/data/com.termux/files/home/minecraft/.worktrees/batch-b-farm-fish` on branch `feat/batch-b-farm-fish`。
- 每项机制的可修改文件范围在该任务下列出，**不得越界改任务外文件**。
- TDD：扫描选种、区域包含、雨天时长纯函数先写失败测试。
- mixin 交付前过 `quirky-mixin-runtime-audit`；新物品过 `quirky-new-item-checklist`。
- 音效零新增（复用原版 SoundEvents）；**不做自动播种/自动钓鱼**（平衡红线）。
- 贴图先示意稿（`build/previews/`）用户确认后落正式资源。

## 已验证的 26.2 API 锚点

| 用途 | API | mcsrc 位置 |
|---|---|---|
| 物品右键方块 | `Item.useOn(UseOnContext)` | `item/Item.java:179` |
| 种子=作物 BlockItem | `WHEAT_SEEDS = BlockItem(Blocks.WHEAT)` | `item/Items.java:1027` |
| 作物存活判定 | `CropBlock.canSurvive`（光照+基质） | `block/CropBlock.java:151` |
| 种植音效 | `SoundEvents.CROP_PLANTED` / `NETHER_WART_PLANTED` | 项目 `HarvestFx.java:25-27` 已用 |
| 收割参考模式 | fabric `UseBlockCallback`（无 mixin） | 项目 `HarvestHandler.java` |
| 咬钩倒计时字段 | `FishingHook.timeUntilLured`（private int），同组 `nibble`/`timeUntilHooked` | `entity/projectile/FishingHook.java:64-66` |
| 递减语句 | `timeUntilLured -= fishingSpeed`（fishingSpeed 局部量 :299） | `FishingHook.java:349` |
| tick 方法 | `FishingHook.tick()`，服务端分支在 `!level().isClientSide()` 内 | `FishingHook.java:153/160` |
| 下雨判定 | `Level.isRaining()` | `world/level/Level.java:948` |
| 水面判定 | `FluidState.is(FluidTags.WATER)` | `FishingHook.java:174` 同款用法 |
| 投掷物基类 | `ThrowableItemProjectile`（仿 `ThrownEgg`） | `entity/projectile/throwableitemprojectile/ThrownEgg.java` |
| 音效 | `SNOWBALL_THROW`、`GENERIC_SPLASH`、`ITEM_BREAK`、`BUBBLE` 粒子 | 均已验证存在 |

---

## Task 0: 配置与 lang 脚手架

**Files:**
- Modify: `src/main/java/dev/quirky/config/QuirkyConfig.java`
- Modify: `src/main/resources/assets/quirky/lang/*.json`

**Steps:**
- [ ] 新增字段（沿用 `@ConfigEntry.Category("toggles")` + `@ConfigEntry.Gui.Tooltip` 风格）：`seedPouchEnabled`(bool)、`seedPouchRadius`(int 0-2，`BoundedDiscrete`)、`fishBaitEnabled`(bool)、`fishBaitDurationSeconds`(int 10-300)、`fishBaitRadius`(int 2-8)、`fishBaitRainBonus`(bool)。
- [ ] 补全配置翻译键与 `@Tooltip` 后缀键（无缺键回退，必须写全）。
- [ ] Run `gradle build --no-daemon --console=plain`。

---

## Task 1: 播种袋

**Files:**
- New: `src/main/java/dev/quirky/seedpouch/SeedPouchPlanter.java`（纯逻辑：扫描+选种+消耗清单）
- New: `src/main/java/dev/quirky/item/SeedPouchItem.java`
- New: `src/test/java/dev/quirky/seedpouch/SeedPouchPlanterTest.java`
- Modify: `src/main/java/dev/quirky/ModItems.java`（注册+创造页签）
- New 资源: `assets/quirky/items/seed_pouch.json`、`models/item/seed_pouch.json`、`textures/item/seed_pouch.png`（先示意稿）
- New: `data/quirky/recipe/seed_pouch.json`（皮革+线×2+小麦种子无序）
- Modify: lang（`item.quirky.seed_pouch` + `tooltip.quirky.seed_pouch`）

**Interfaces:**
- `SeedPouchPlanter.plan(List<BlockSnapshot> area, List<ItemStack> inventory, int radius)` → `PlanResult`（每格种哪个 BlockItem + 消耗哪个槽位 1 个）。
- `BlockSnapshot`：位置 + 目标格上方可替换性 + 基地方块状态。纯数据，可 mock。

**Steps:**
- [ ] 写失败测试：
  - 3×3 耕地+单一小麦种子 → 9 格全种；
  - 种子不足 → 种能种的，其余跳过；
  - 多种种子混合 → 按遍历顺序分配；
  - 甘蔗：沙+水旁可种、远离水不种（canSurvive 泛化）；
  - 精准模式（radius=0）只种 1 格；
  - 石头区域 → 空计划。
- [ ] 实现 `SeedPouchPlanter`：遍历区域格，对每格按背包顺序找第一个 `BlockItem` 且 `defaultBlockState.canSurvive(level, pos.above())` 且目标格空气可替换的种子。
- [ ] `SeedPouchItem.useOn`：服务端执行计划 → 逐格 `level.setBlock(pos.above(), crop.defaultBlockState(), 3)`；消耗背包对应槽位；播放 `CROP_PLANTED`（参照 `HarvestFx` 分支：地狱疣用 `NETHER_WART_PLANTED`），音高随数量微调；每格 `HAPPY_VILLAGER` 粒子；挥臂。空计划返回 fail（不挥臂）。创造不消耗。潜行=精准模式。
- [ ] **场景自查**（项目纪律）：副手持袋、干/湿耕地、背包满、混合种子、与收割连用（收割后原地补种 vs 播种袋区域新种互不干扰）。
- [ ] 资源过 `quirky-new-item-checklist`；注册进 `ModItems.register` + 创造页签（TOOLS_AND_UTILITIES）。
- [ ] Run focused test → `gradle test --no-daemon --console=plain`。

**Verification:** 单测全绿；桌面客户端 3×3/精准/混合/甘蔗/创造各验。

---

## Task 2: 鱼饵球

**Files:**
- New: `src/main/java/dev/quirky/fishbait/BaitZoneLogic.java`（纯函数：包含判定、雨天时长）
- New: `src/main/java/dev/quirky/item/FishBaitItem.java`
- New: `src/main/java/dev/quirky/fishbait/BaitZoneEntity.java`
- New: `src/main/java/dev/quirky/mixin/FishingHookBaitMixin.java`
- New: `src/test/java/dev/quirky/fishbait/BaitZoneLogicTest.java`
- Modify: `src/main/java/dev/quirky/ModItems.java`、`src/main/java/dev/quirky/ModEntities.java`、`src/main/resources/quirky.mixins.json`
- New 资源: `assets/quirky/items/fish_bait.json`、`models/item/fish_bait.json`、`textures/item/fish_bait.png`（先示意稿）
- New: `data/quirky/recipe/fish_bait.json`（小麦+腐肉无序→4）
- Modify: lang

**Interfaces:**
- `BaitZoneLogic.isInside(Vec3 bobber, Vec3 zone, double radius)` → boolean。
- `BaitZoneLogic.durationTicks(int baseSeconds, boolean raining, boolean rainBonusEnabled)` → 雨天 ×5/3。

**Steps:**
- [ ] 写失败测试：边界（半径内/外/恰在边界）、雨天 90→150 秒、开关关闭时雨天不加成。
- [ ] 实现 `BaitZoneLogic`。
- [ ] `BaitZoneEntity`：无重力、无碰撞、不可见、不渲染；tick 倒计时自毁；客户端侧每 tick 低概率 `BUBBLE` 粒子（密度随剩余时间线性变稀）；NBT 存剩余 tick。
- [ ] `FishBaitItem.use` 投掷（仿 ThrownEgg）：`FishBaitEntity extends ThrowableItemProjectile`，`onHit` 服务端判落点流体 `FluidTags.WATER` → 生成 BaitZoneEntity + `GENERIC_SPLASH`（音量 0.4）+ `SPLASH` 粒子；陆地 → `ITEM_BREAK` + 碎屑粒子，不生成区域。
- [ ] `FishingHookBaitMixin`：
  - `@Mixin(FishingHook.class)`，`@Shadow private int nibble; @Shadow private int timeUntilHooked; @Shadow private int timeUntilLured;`（三个字段均声明在 FishingHook 本类 :64-66，已验——@Shadow 本类字段安全）。
  - `@Inject(method="tick", at=@At("HEAD"))`：`@Unique quirky$decrementing = (nibble==0 && timeUntilHooked==0 && timeUntilLured>0)`。
  - `@Inject(method="tick", at=@At("RETURN"))`：若 `quirky$decrementing && !level().isClientSide()` 且配置开，`level().getEntities(ModEntities.BAIT_ZONE, AABB 半径判定)` 非空 → `timeUntilLured = Math.max(0, timeUntilLured - 1)`（二进制：多区域不叠加）。
  - **mixin 审计**：tick 无重载（单一 `(V)` 方法，已验 :153）；@Shadow 字段名与 mojmap 一致（jar 保留 mojmap，Loom 不内联字段名——本类字段无 remap 风险，但仍按审计清单核对）。
- [ ] 注册实体/物品/配方/lang/创造页签（INGREDIENTS 或 COMBAT，实现时与批 A 保持一致风格）。
- [ ] Run focused test → `gradle test`。

**Verification:** 单测全绿；桌面客户端：水面/陆地投掷分支、气泡密度随时间变稀、雨天时长、打窝前后咬钩间隔主观对比、多区域不叠加。

---

## 批内完成标准

- [ ] 两机制全部实现，`gradle build --no-daemon --console=plain` 通过
- [ ] 单测全绿，mixin 运行时审计清单逐项过
- [ ] 音效/粒子按设计文档逐项手动验证
- [ ] 报告：Status + 实现内容对照设计文档 + 测试结果 + 文件变更清单
