# 批 A 实现计划 — 轻量手感与小点缀

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现批 A 四项机制——弓箭命中"叮"声、营火染色烟、鹦鹉蛋、起床保护。全部为轻量 vanilla+ 机制，每项自带音效/粒子手感与配置开关。

**Architecture:** 四项互相独立，无共享实体/方块。叮声与起床保护是纯 mixin（无新物品）；鹦鹉蛋是新物品+新弹射物实体；营火染色烟是 mixin 给原版 BE 加字段 + 自定义粒子。配置统一进 `QuirkyConfig`，经 `QuirkyConfigHolder` 读取。

**Tech Stack:** Fabric/Minecraft 26.2 official mappings, Java 25, JUnit 5, Mockito, system Gradle。构建命令：`JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk PATH=...:$PATH gradle build --no-daemon --console=plain`。

**对应设计文档：** `docs/superpowers/specs/2026-08-03-batch-a-light-touch-design.md`（含全部边界场景与音效表，实现前先读）。

## Global Constraints

- Work only in `/data/data/com.termux/files/home/minecraft/.worktrees/batch-a-light-touch` on branch `feat/batch-a-light-touch`。四机制独立，若需并行可各开 worktree；默认单 worktree 串行。
- 每项机制的可修改文件范围在该任务下列出，**不得越界改任务外文件**。
- 遵循 TDD：可抽纯函数的逻辑（命中判定、孵化概率、缓降时长）先写失败测试。
- 所有 mixin 交付前过 `quirky-mixin-runtime-audit` skill 清单；新物品过 `quirky-new-item-checklist`。
- 音效资源零新增（全部复用已验证的原版 SoundEvents）。
- **视觉/贴图先示意后落地**：鹦鹉蛋贴图先出放大预览稿，用户确认后才写正式资源。

## 已验证的 26.2 API 锚点（实现时直接用，勿凭记忆改）

| 用途 | API | mcsrc 位置 |
|---|---|---|
| 箭命中注入点 | `AbstractArrow.onHitEntity(EntityHitResult)` | `entity/projectile/arrow/AbstractArrow.java:419` |
| 暴击判定 | `AbstractArrow.isCritArrow()` | 同上 :228/:448 |
| 起床注入点 | `Player.stopSleepInBed(boolean forcefulWakeUp, boolean updateLevelList)` | `entity/player/Player.java:1321` |
| 缓降效果 | `MobEffects.SLOW_FALLING`（`Holder<MobEffect>`） | `world/effect/MobEffects.java:99` |
| 效果实例 | `new MobEffectInstance(Holder, int duration)` | `MobEffectInstance.java:52` |
| 播放音效（Holder 重载） | `Level.playSound(..., Holder<SoundEvent>, ...)` | `world/level/Level.java:455-468` |
| 蛋弹射物模板 | `ThrownEgg extends ThrowableItemProjectile` | `entity/projectile/throwableitemprojectile/ThrownEgg.java` |
| 孵化模式 | `EntityTypes.X.create(level, EntitySpawnReason.TRIGGERED)` + `snapTo` | `ThrownEgg.java:76` |
| 鹦鹉无幼年 | `Parrot.canBeABaby()` 返回 false；构造器随机选色 | `animal/parrot/Parrot.java:148/157` |
| 营火烟粒子生成 | 静态 `CampfireBlock.makeParticles(Level,BlockPos,boolean,boolean)` | `block/CampfireBlock.java:233`（调用方 :194 与 `CampfireBlockEntity.java:104`） |
| 营火 BE 存取 | `loadAdditional(ValueInput)` / `saveAdditional(ValueOutput)` | `block/entity/CampfireBlockEntity.java:130/147` |
| 营火右键交互 | `CampfireBlock.useItemOn` | `CampfireBlock.java:91` |
| 营火实体接触 | `CampfireBlock.entityInside` | `CampfireBlock.java:116` |
| 营火熄灭 | douse 路径 setBlock LIT=false | `CampfireBlock.java:213` |
| 烟粒子类（参考） | `CampfireSmokeParticle extends SingleQuadParticle`，构造器私有 | `client/particle/CampfireSmokeParticle.java` |
| 粒子类型注册 | `Registry.register(BuiltInRegistries.PARTICLE_TYPE, ...)`，codec/streamCodec 模式 | `Registries.java:216`、`ParticleTypes.java:15` |
| 粒子工厂注册 | `ParticleProviderRegistry.getInstance().register(ParticleType, ParticleProvider)` | fabric-particles-v1（javap 已验） |
| 染色 RGB | `DyeColor.getTextureDiffuseColor()` | `item/DyeColor.java:90` |

音效（均已验证存在于 `SoundEvents`）：叮声 `NOTE_BLOCK_BELL`（Holder）/备选 `AMETHYST_BLOCK_CHIME`；金属 `ARMOR_EQUIP_IRON`；起床 `NOTE_BLOCK_CHIME`；投蛋 `EGG_THROW`；碎壳 `ITEM_BREAK`；鹦鹉 `PARROT_AMBIENT`；染色嘶声 `FIRE_EXTINGUISH`。

---

## Task 0: 配置与 lang 脚手架

**Files:**
- Modify: `src/main/java/dev/quirky/config/QuirkyConfig.java`
- Modify: `src/main/resources/assets/quirky/lang/en_us.json`（及已有其它语言文件）

**Steps:**
- [ ] 按现有 `@ConfigEntry.Category("toggles")` + `@ConfigEntry.Gui.Tooltip` 风格，新增批 A 配置字段：`arrowDingEnabled`、`arrowDingVolume`(float 0-1)、`wakeUpProtectionEnabled`、`wakeUpSlowFallingSeconds`(int)、`parrotEggEnabled`、`parrotEggHatchChance`(float)、`parrotEggTwinChance`(float)、`dyedCampfireSmokeEnabled`、`dyedCampfireGlow`（夜光烟开关）。数值字段用 `@ConfigEntry.BoundedDiscrete`（项目经验：运行时自行 clamp）。
- [ ] 数值型配置如需 UI slider，用 `BoundedDiscrete(min,max)`；不强制反序列化边界。
- [ ] 添加 lang 键：配置项 `text.autoconfig.quirky.option.*` + tooltip `...@Tooltip`（项目经验：GUI tooltip 后缀是 `@Tooltip`，无缺键回退，必须补全）。
- [ ] Run `gradle build --no-daemon --console=plain`，确认编译通过、配置可加载。

**Verification:** 构建通过；启动测试环境 `QuirkyConfigHolder.get()` 返回默认值。

---

## Task 1: 弓箭命中"叮"声

**Files:**
- New: `src/main/java/dev/quirky/ding/ArrowDingLogic.java`（纯函数）
- New: `src/main/java/dev/quirky/mixin/ArrowDingMixin.java`
- New: `src/test/java/dev/quirky/ding/ArrowDingLogicTest.java`
- Modify: `src/main/resources/quirky.mixins.json`（加入 `ArrowDingMixin`）

**Interfaces:**
- `ArrowDingLogic.resolve(TargetKind kind, boolean crit, boolean kill)` → 返回播放参数记录（sound holder、pitch、volume 倍数）或"不播放"。
- `TargetKind` 枚举：LIVING_UNARMORED / LIVING_METAL_ARMOR / SHIELD_BLOCKED / NON_LIVING。

**Steps:**
- [ ] 写失败测试：覆盖金属甲变音、盾挡不响、非生物不响、暴击高音高、击杀音量加成。
- [ ] 实现 `ArrowDingLogic`，让测试通过。
- [ ] 写 `ArrowDingMixin`：`@Mixin(AbstractArrow.class)`，`@Inject(method="onHitEntity", at=@At("TAIL"))`。handler 里 `hitResult.getEntity()` 判 `LivingEntity`；读配置开关/音量；金属甲判定看目标 `ItemStack` 材质（盔甲含铁/金/下界合金 → `ARMOR_EQUIP_IRON`，否则 `NOTE_BLOCK_BELL`）；暴击 `isCritArrow()`；击杀判定 `!entity.isAlive()`（命中后）。服务端 `level.playSound(null, pos, holder, SoundSource.PLAYERS, vol, pitch)`。
- [ ] **mixin 审计**：`onHitEntity` 声明在 `AbstractArrow` 本类（非父类），描述符 `(Lnet/minecraft/world/phys/EntityHitResult;)V`；对照 mcsrc 逐字核对后才算完成。
- [ ] Run focused test → `gradle test --no-daemon --console=plain`。

**Verification:** 单测全绿；桌面客户端射无甲/金属甲生物、射方块、暴击、击杀各验一次听感。

---

## Task 2: 起床保护

**Files:**
- New: `src/main/java/dev/quirky/wakeup/WakeUpLogic.java`（纯函数）
- New: `src/main/java/dev/quirky/mixin/WakeUpMixin.java`
- New: `src/test/java/dev/quirky/wakeup/WakeUpLogicTest.java`
- Modify: `src/main/resources/quirky.mixins.json`

**Interfaces:**
- `WakeUpLogic.durationTicks(boolean deepSleep, int configSeconds)` → 深睡用 configSeconds，非深睡（中途主动起床/被惊醒）用 1/3。
- 深睡判定（已验 API）：`Player.isSleepingLongEnough()`（Player.java:1335）在 stopSleepInBed HEAD 时快照到 `@Unique` 字段（TAIL 时已醒，不能再读），深睡 = `!forcefulWakeUp && 快照`。

**Steps:**
- [ ] 写失败测试：深睡满时长、被打断 1/3、0 秒返回 0。
- [ ] 实现 `WakeUpLogic`。
- [ ] 写 `WakeUpMixin`：`@Mixin(Player.class)`，双注入——`@Inject(method="stopSleepInBed", at=@At("HEAD"))` 快照 `isSleepingLongEnough()` 到 `@Unique` 字段；`at=@At("TAIL")` 消费快照，签名 `(ZZ)V`。handler 守卫 `!level().isClientSide` 与配置开关；`addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, durationTicks))`；播放 `NOTE_BLOCK_CHIME`。
- [ ] **mixin 审计**：`stopSleepInBed` 与 `isSleepingLongEnough` 都声明在 `Player` 本类（已验 Player.java:1321/1335）；HEAD/TAIL 双注入 handler 参数集不同需分别核对；客户端/服务端都会调用，必须 isClientSide 守卫（否则双端叠音效）。
- [ ] Run focused test。

**Verification:** 单测全绿；桌面客户端睡满整晚起床看缓降粒子+听音效；被怪物惊醒时长更短。

---

## Task 3: 鹦鹉蛋

**Files:**
- New: `src/main/java/dev/quirky/item/ParrotEggItem.java`
- New: `src/main/java/dev/quirky/parrotegg/ParrotEggEntity.java`
- New: `src/main/java/dev/quirky/parrotegg/ParrotEggHatchLogic.java`（纯函数）
- New: `src/test/java/dev/quirky/parrotegg/ParrotEggHatchLogicTest.java`
- Modify: `src/main/java/dev/quirky/ModItems.java`（注册物品）
- Modify: `src/main/java/dev/quirky/ModEntities.java`（注册实体）
- New 资源: `assets/quirky/items/parrot_egg.json`、`assets/quirky/models/item/parrot_egg.json`、`assets/quirky/textures/item/parrot_egg.png`（先示意稿）
- New: `data/quirky/recipe/parrot_egg.json`（鸡蛋+羽毛无序）
- Modify: lang（`item.quirky.parrot_egg`）

**Interfaces:**
- `ParrotEggHatchLogic.hatchCount(RandomSource, float hatchChance, float twinChance)` → 0/1/2。

**Steps:**
- [ ] 写失败测试：注入固定随机源，验证 hatchChance/twinChance 边界（0、1、中间值）。
- [ ] 实现 `ParrotEggHatchLogic`。
- [ ] `ParrotEggEntity extends ThrowableItemProjectile`，仿 `ThrownEgg`：`onHit` 里服务端按逻辑孵 0/1/2 只鹦鹉（`EntityTypes.PARROT.create(level, EntitySpawnReason.TRIGGERED)` + `snapTo`，**不设 age**——鹦鹉无幼年），`handleEntityEvent((byte)3)` 碎壳粒子；孵化成功 `PARROT_AMBIENT` + 爱心粒子。
- [ ] `ParrotEggItem.use` 抛出实体、播放 `EGG_THROW`、消耗物品（创造不消耗）。
- [ ] 注册进 `ModItems`/`ModEntities`（参照 `TORCH_ARROW` 模式：`EntityType.Builder`、`setId`、`Registry.register`），加入创造页签。
- [ ] 资源过 `quirky-new-item-checklist`（items/ + models/item/ + textures/item/ + lang 双文件，对照 `bottled_cloud`）。
- [ ] **贴图流程**：先出放大预览稿到 `build/previews/`，用户确认后再落正式 PNG。
- [ ] Run focused test。

**Verification:** 单测全绿；桌面客户端投掷、孵化/双胞胎/失败三分支、命中生物无伤害。

---

## Task 4: 营火染色烟

**Files:**
- New: `src/main/java/dev/quirky/particle/DyedCampfireSmokeOption.java`（ParticleOptions，携 ARGB）
- New: `src/main/java/dev/quirky/ModParticles.java`（注册 ParticleType）
- New: `src/client/java/dev/quirky/client/particle/DyedCampfireSmokeParticle.java`
- New: `src/client/java/dev/quirky/client/particle/DyedCampfireSmokeProvider.java`
- New: `src/main/java/dev/quirky/mixin/CampfireBlockEntityMixin.java`（加 smokeColor 字段+存取）
- New: `src/main/java/dev/quirky/mixin/CampfireBlockMixin.java`（染色交互+粒子替换+熄灭清色）
- Modify: `src/main/resources/quirky.mixins.json`、`quirky.client.mixins.json`
- Modify: `src/client/java/dev/quirky/client/QuirkyModClient.java`（注册粒子工厂）

**Interfaces:**
- `CampfireBlockEntityMixin`：`@Unique int quirky$smokeColor = -1`，暴露 accessor 供 `CampfireBlockMixin` 读写；`saveAdditional`/`loadAdditional` 注入存取（`ValueOutput.putInt` / `ValueInput.getInt`，键 `quirky_smoke_color`）。
- 同步：BE 更新后触发 client sync（`setChanged` + level 发包），客户端粒子生成才读得到颜色。

**Steps:**
- [ ] `DyedCampfireSmokeOption implements ParticleOptions`：携 `int argb` + `boolean signalFire`，写 codec/streamCodec（参照 mcsrc 既有 Option 类）。
- [ ] `ModParticles`：`Registry.register(BuiltInRegistries.PARTICLE_TYPE, QuirkyMod.id("dyed_campfire_smoke"), new ParticleType<>(false, ...))`，在 `QuirkyMod.onInitialize` 触发类加载。
- [ ] `DyedCampfireSmokeParticle`：复制原版 `CampfireSmokeParticle` 行为（scale 3、lifetime、gravity 3e-6、上升漂移、末段淡出）+ 构造时 `setColor(r,g,b)`（从 ARGB 解）。
- [ ] `DyedCampfireSmokeProvider implements ParticleProvider<DyedCampfireSmokeOption>`：用粒子图集的营火烟 sprite 建粒子。
- [ ] `QuirkyModClient` 注册：`ParticleProviderRegistry.getInstance().register(ModParticles.DYED_CAMPFIRE_SMOKE, new DyedCampfireSmokeProvider(...))`。
- [ ] `CampfireBlockMixin`：
  - `useItemOn` HEAD：手持染料 + LIT 营火 → 消耗染料、设 smokeColor、播 `FIRE_EXTINGUISH`、粒子爆发；白染料清色。荧光石粉设 glow 标记，火药触发一次性烟爆。
  - `entityInside` TAIL：染料 ItemEntity 接触 → 同上（贴合"丢进营火"）。
  - `@Inject` HEAD 静态 `makeParticles`：读 pos 处 BE smokeColor，有颜色则生成染色粒子并 `ci.cancel()`。
  - douse/熄灭路径（:213 setBlock LIT=false 处）重置 smokeColor=-1。
- [ ] **mixin 审计**：`makeParticles` 是静态方法，注入用 `@Inject(method="makeParticles", ...)` 且 handler 无 this；`useItemOn`/`entityInside` 对照 mcsrc 描述符逐字核对；`@Unique` 字段不进 remap 问题清单。
- [ ] Run `gradle build`。

**Verification:** 桌面客户端：染色/换色/白染料清色/熄灭清色/信号火高烟/丢染料入火/夜光烟/火药烟爆，逐项过。

---

## 批内完成标准

- [ ] 四机制全部实现，`gradle build --no-daemon --console=plain` 通过
- [ ] 全部单测绿，mixin 运行时审计清单逐项过
- [ ] 音效/粒子手感按设计文档 §各音效表逐项手动验证
- [ ] 报告：Status + 实现内容对照设计文档 + 测试结果 + 文件变更清单
