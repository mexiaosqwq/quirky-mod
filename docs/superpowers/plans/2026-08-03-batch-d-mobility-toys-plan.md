# 批 D 实现计划 — 机动与玩具

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现批 D 两项机制——绳捆（可攀爬/延伸/挂灯照明的绳索）与回旋镖（自定义飞行实体武器/玩具）。批内最重的一批：绳捆为纯方块（风险低，先做），回旋镖为自定义实体+渲染+物理（后做）。

**Architecture:** 绳捆 = 两个新方块（`rope`/`rope_lantern`）+ `#minecraft:climbable` tag + `entityInside` 防摔 + 纯函数连锁掉落。回旋镖 = 自定义物品 + `Projectile` 子类实体（自实现 tick 物理）+ 物品渲染（仿 ThrownItemRenderer）+ 碎块/拾取/远程激活纯逻辑。

**Tech Stack:** Fabric/Minecraft 26.2 official mappings, Java 25, JUnit 5, Mockito, system Gradle。构建命令：`JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk PATH=...:$PATH gradle build --no-daemon --console=plain`。

**对应设计文档：** `docs/superpowers/specs/2026-08-03-batch-d-mobility-toys-design.md`（含全部边界场景与音效表，实现前先读）。

## Global Constraints

- Work only in `/data/data/com.termux/files/home/minecraft/.worktrees/batch-d-mobility-toys` on branch `feat/batch-d-mobility-toys`。
- 每项机制的可修改文件范围在该任务下列出，**不得越界改任务外文件**。
- TDD：连锁掉落段计算、批量铺设停止条件、回旋镖飞行步进/返程转向、碎块可碎判定先写失败测试。
- mixin 交付前过 `quirky-mixin-runtime-audit`；新物品过 `quirky-new-item-checklist`。
- 音效零新增（全部复用已验证原版 SoundEvents）。
- 贴图先示意稿（`build/previews/`）用户确认后落正式资源。

## 已验证的 26.2 API 锚点

| 用途 | API | mcsrc 位置 |
|---|---|---|
| 方块被弹射物命中 | `BlockBehaviour.onProjectileHit(Level, BlockState, BlockHitResult, Projectile)` + BlockStateBase 包装 | `block/state/BlockBehaviour.java:393`、`:878` |
| 实体穿入方块 | `entityInside(BlockState, Level, BlockPos, Entity, InsideBlockEffectApplier, boolean)`（CampfireBlock 同款 6 参） | `CampfireBlock.java:110-120`、基类 `BlockBehaviour.java:376` |
| 重置摔落 | `Entity.resetFallDistance()` | `entity/Entity.java:2893` |
| 攀爬判定 | `LivingEntity.onClimbable()`（由 `#minecraft:climbable` tag 驱动，项目已验） | `entity/LivingEntity.java:1711` |
| tag 覆盖原版先例 | 项目 `data/minecraft/tags/item/arrows.json`（`replace: false`） | 项目资源 |
| 弹射物基类 | `Projectile extends Entity implements TraceableEntity`；`onHit`/`onHitEntity`/`onHitBlock` 可覆写；`tick()` :105 | `entity/projectile/Projectile.java:42/291/310/313` |
| 服务端伤害 | `LivingEntity.hurtServer(ServerLevel, DamageSource, float)` | `entity/LivingEntity.java:1169` |
| 物品耐久 | `Item.Properties.durability(int)` | `item/Item.java:405` |
| 破坏速度 | `BlockState.getDestroySpeed(BlockGetter, BlockPos)` | `block/state/BlockBehaviour.java:644` |
| 实体渲染参照 | `ThrownItemRenderer`（物品模型渲染） | `client/renderer/entity/ThrownItemRenderer.java` |
| 音效 | `WOOL_PLACE`/`WOOL_BREAK`（项目 BottledCloudItem 已验证）、`ITEM_BREAK`、`ITEM_PICKUP`、`BELL_BLOCK`、`GENERIC_SMALL_FALL` | 均已验证 |
| 方块对照 | 项目 `wooden_hopper` 方块资源结构（blockstate/model/items 双文件） | 项目资源 |

---

## Task 0: 配置与 lang 脚手架

**Files:**
- Modify: `src/main/java/dev/quirky/config/QuirkyConfig.java`
- Modify: `src/main/resources/assets/quirky/lang/*.json`

**Steps:**
- [ ] 新增字段：`ropeEnabled`(bool)、`ropeMaxExtendPerUse`(int 1-64, BoundedDiscrete, 默认 32)、`boomerangEnabled`(bool)、`boomerangRange`(int 4-24, 默认 12)、`boomerangDamage`(int 0-4, 默认 2)、`boomerangBreakBlocks`(bool)、`boomerangBreakChance`(float 0-1, 默认 0.05)。
- [ ] 补全配置翻译键 + `@Tooltip` 后缀键。
- [ ] Run `gradle build --no-daemon --console=plain`。

---

## Task 1: 绳捆（rope + rope_lantern）

**Files:**
- New: `src/main/java/dev/quirky/rope/RopeSupportLogic.java`（纯函数：支撑判定、连锁掉落段计算、批量铺设停止）
- New: `src/main/java/dev/quirky/block/RopeBlock.java`、`src/main/java/dev/quirky/block/RopeLanternBlock.java`
- New: `src/main/java/dev/quirky/item/RopeItem.java`（自定放置逻辑：向下延伸/潜行批量铺/挂灯交互放 RopeLanternBlock）
- New: `src/test/java/dev/quirky/rope/RopeSupportLogicTest.java`
- Modify: `src/main/java/dev/quirky/ModBlocks.java`、`ModItems.java`、`QuirkyMod.java`（若需）
- New 资源: `data/minecraft/tags/block/climbable.json`（replace:false + 两方块）、blocks/rope.json+rope_lantern.json、items/rope.json+rope_lantern.json、models/block/rope.json 等、textures/block/rope.png 等（先示意稿）
- New: `data/quirky/recipe/rope.json`（线×3 竖列 → 绳×3）、`rope_lantern.json`（绳+灯笼）
- Modify: lang

**Interfaces:**
- `RopeSupportLogic.isSupported(BlockPos aboveState, BlockState selfState)` → 上方是完整固体底面 / 栅栏·墙·地狱栅栏顶 / 另一段绳。
- `RopeSupportLogic.fallingSegments(List<BlockPos> columnSnapshots, Predicate<BlockPos> supportCheck)` → 连锁掉落段列表（自下而上）。
- `RopeSupportLogic.extendStop(int maxSegments, int available, boolean blocked)` → 本次铺设段数（≤32、≤手持数、撞非空气非水即停）。

**Steps:**
- [ ] 写失败测试：挂点四类合法/非法、连锁掉落中间段断（下方段先掉）、批量铺停止（撞方块/撞水边界/手持耗尽/上限）、world bottom 停止。
- [ ] 实现 `RopeSupportLogic` 纯函数。
- [ ] `RopeBlock`：无碰撞箱（`BlockBehaviour.Properties.of().noCollission()`）、可含水（`BlockStateProperties.WATERLOGGED`）、光照透明；覆写 `entityInside`：非潜行且 `getDeltaMovement().y() < 0` 的实体 → `resetFallDistance()` + 垂直速度钳到 -0.15（防摔缓滑）；覆写 `neighborChanged`/`tick` 双保险触发支撑检查。
- [ ] `RopeLanternBlock`：同 RopeBlock + 亮度 15（`lightLevel`）；放置/破坏与绳互换交互由 `RopeItem` 处理（方块替换保留 waterlogged）。
- [ ] `RopeItem`（`BlockItem` 子类或自定 `useOn`）：
  - 对固体方块底面 → 放第一段。
  - 对已有绳段（非潜行）→ 下方延伸一段（下方空气/水）。
  - 潜行+对绳段 → 批量铺（`extendStop` 逻辑，每段间隔 1 tick 放置音，形成"唰——"下滑声）。
  - 手持灯笼对绳段右键 → 该段变 `rope_lantern`（消耗灯笼）。
  - 放置音 `WOOL_PLACE`（音量 0.6，音高随延伸次数轻微递降）+ 绳纤维粒子；连锁掉落每段 `WOOL_BREAK`（0.4）。
  - 创造不消耗。
- [ ] **场景自查**：挂栅栏/墙顶、水环境延伸、活塞推走支撑、玩家在绳上被打断自然坠落、两个绳柱相邻独立、y=-64 边界、副手操作。
- [ ] 资源过 `quirky-new-item-checklist`；方块过 `wooden_hopper` 对照。
- [ ] Run focused test。

**Verification:** 单测全绿；桌面客户端放置/延伸/批量铺/攀爬/打断连锁/含水/挂灯照明/坠落抓绳防摔（含潜行穿透）。

---

## Task 2: 回旋镖

**Files:**
- New: `src/main/java/dev/quirky/boomerang/BoomerangPhysics.java`（纯函数：飞行步进、返程转向、弧线）
- New: `src/main/java/dev/quirky/boomerang/BoomerangBlockLogic.java`（纯函数：碎块判定、免疫）
- New: `src/main/java/dev/quirky/entity/BoomerangEntity.java`（`extends Projectile`，自实现 tick）
- New: `src/main/java/dev/quirky/item/BoomerangItem.java`
- New: `src/client/java/dev/quirky/client/render/BoomerangRenderer.java`
- New: `src/test/java/dev/quirky/boomerang/BoomerangPhysicsTest.java`、`BoomerangBlockLogicTest.java`
- Modify: `src/main/java/dev/quirky/ModItems.java`、`ModEntities.java`、`src/client/java/dev/quirky/client/QuirkyModClient.java`（EntityRenderers 注册）
- New 资源: `data/quirky/tags/block/boomerang_unbreakable.json`（基岩/黑曜石等）、items/boomerang.json、models/item/boomerang.json、textures/item/boomerang.png（先示意稿）
- New: `data/quirky/recipe/boomerang.json`（木板+铁粒弧线）
- Modify: lang

**Interfaces:**
- `BoomerangPhysics.step(Vec3 pos, Vec3 vel, float dt)` → 新 pos/vel（出程直线+微降弧；返程向玩家转向）。
- `BoomerangPhysics.returnVector(Vec3 pos, Vec3 target, Vec3 vel, double turnRate)` → 转向后的速度。
- `BoomerangBlockLogic.canBreak(state, speed, isImmune, adventureMode, roll)` → 必碎/摇骰/不可碎。
- `BoomerangBlockLogic.canActivate(state)` → 可弹射物激活方块（钟/木按钮等）。

**Steps:**
- [ ] 写失败测试：步进直线积分、返程转向收敛（不超调）、命中同一生物只记一次（Set<UUID>）、碎块三分支（speed==0 必碎 / 免疫不碎 / 冒险不碎 / 其余摇骰 5%）、回收槽位选择（副手→背包→满包失败）。
- [ ] 实现纯函数（`BoomerangPhysics`/`BoomerangBlockLogic`）。
- [ ] `BoomerangEntity extends Projectile`：
  - 构造/`defineSynchedData`（无同步字段或仅状态位）；NBT：出程/返程状态、已命中 Set<UUID>、投掷者 UUID。
  - 覆写 `tick()`（:105 自实现）：出程步进 → 到最远点（range）切返程 → 返程追踪活玩家（UUID 查找，防内存悬挂）→ 回手判定（距离 <1.5 格 → 回收/掉落 + 还耐久）。
  - 命中判定：`projectileHit` 流程——实体：`hurtServer` 伤 2 点（配置可调），同一生物每轮一次；方块：`BoomerangBlockLogic.canBreak` → 可碎则 `level.destroyBlock`（碎块音 `WOOL_BREAK`/`ITEM_BREAK` + 粒子）；不可碎 → 切返程；`BoomerangBlockLogic.canActivate` → `state.onProjectileHit(level, state, hit, this)`（钟/按钮激活）。
  - 拾取物品：命中可拾取实体（`isAlive && !isDeadOrDying`）时把其身上的可拾取物品（如掉落的物品实体）带回——用 `ItemEntity` 吸附，回程带回投掷者，`popResource` 返还。
  - 耐久：每次投掷返回时 `hurtAndBreak(1, ...)`（服务端），250 耐久；耐久耗尽 `ITEM_BREAK` 音。
- [ ] `BoomerangItem.use`：服务端验证未在飞行（同玩家已有活跃实体则拒绝）→ 生成实体 `setOwner(player)`、`snapTo`、设初速（水平抛出，轻微上仰）；`ITEM_PICKUP`/`ARROW_SHOOT` 投掷音。副手/主手均可投。
- [ ] `BoomerangRenderer`（仿 ThrownItemRenderer）：物品模型 + 自转（tick 数驱动旋转角度）+ 用实体实际位置渲染（项目教训：视觉位置=实体位置）。
- [ ] **场景自查**：满背包回收失败掉落、投石墙反复验证约 5% 碎块率、树叶/高草必碎、黑曜石/基岩永不碎（tag 生效）、破坏开关关 → 只反弹不碎、冒险模式不碎、穿透后继续飞、隔空敲钟/打按钮、对空投掷返回空中不接住（回手判定）。
- [ ] 资源过 `quirky-new-item-checklist`；实体注册过 `ModEntities` 现有模式。
- [ ] Run focused test。

**Verification:** 单测全绿；桌面客户端投掷手感（弧线/旋转/回收）、命中一次判定、耐久损坏、满包掉落、碎块率、远程激活。

---

## 批内完成标准

- [ ] 两机制全部实现，`gradle build --no-daemon --console=plain` 通过
- [ ] 单测全绿，mixin 运行时审计清单逐项过
- [ ] 音效/粒子按设计文档逐项手动验证
- [ ] 报告：Status + 实现内容对照设计文档 + 测试结果 + 文件变更清单
