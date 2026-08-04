# 批 A 设计文档 — 轻量手感与小点缀

日期：2026-08-03
状态：待用户审阅
定调：实用、不影响平衡、轻量；每个机制必须有音效/粒子/反馈等手感细节。

## 0. 清单变更说明

原清单含"安眠规则（按比例入睡跳过夜晚）"。经查证 26.2 原版已有 `players_sleeping_percentage` gamerule（mcsrc `GameRules.java:66`，默认 100，0=任意一人入睡即跳过），该机制属重复造轮子，**移除**。替换为**起床保护**（单人可实测、同样轻量实用）。

批 A 最终机制：弓箭命中"叮"声、营火染色烟、鹦鹉蛋、起床保护。

---

## 1. 弓箭命中"叮"声

### 1.1 行为

- 任意箭矢（原版箭、药箭、火把箭等所有 `AbstractArrow` 子类）命中**生物**（LivingEntity）时，播放一声清脆的"叮"。
- **播放位置以射手为中心**（玩家射手 → 射手坐标全音量播放，远射也听得清；非玩家射手如骷髅 → 保持命中点）——用户实测反馈：原版式命中点播放+距离衰减导致远射听不见，故改为射手收听中心。
- 命中方块、命中非生物实体（画、矿车、物品展示框）不响。
- **暴击命中**（`arrow.isCritArrow()`）播放更高音高的变体，形成精准奖励感。
- **材质听感分层（有机扩展）**：命中无甲/皮革目标 = 清脆"叮"；命中**穿金属盔甲**的目标改用金属"哢"（`SoundEvents.ARMOR_EQUIP_IRON`，已验证）——射重甲敌人和射僵尸的反馈不一样，耳朵能"听出"目标类型。
- 目标**举盾成功格挡**时不响（被盾挡掉的箭不配拥有成就感）。
- **击杀确认**：这一箭直接击杀目标时，"叮"声音量 ×1.2 + 一小簇亮晶晶粒子——收尾反馈，射猎手感的高光时刻。
- 声音由服务端 `level.playSound(null, pos, ...)` 播放（pos = 玩家射手位置，非玩家射手为命中点）；射手自身全音量，无远射衰减问题。

### 1.2 音效与粒子

| 事件 | 音效 | 音高 | 音量 |
|---|---|---|---|
| 普通命中 | `SoundEvents.NOTE_BLOCK_BELL`（26.2 已验证；bell 音色即经典 pling） | 1.5 | 配置值（默认 0.6） |
| 暴击命中 | 同上 | 1.8 | 配置值 |
| 命中金属甲目标 | `SoundEvents.ARMOR_EQUIP_IRON`（已验证） | 1.2 | 配置值 |

粒子：暴击命中时额外 3-5 个 `CRIT` 粒子（原版暴击已有粒子，此处不重复添加；仅在原版未产生粒子时补足——实现时二选一，不叠加）。

### 1.3 实现要点

- Mixin：`@Inject` TAIL 注入 `AbstractArrow.onHitEntity(EntityHitResult)`，仅当 `hitResult.getEntity() instanceof LivingEntity` 时播放。
- 不加新声音资源（复用原版音符盒音色，符合"轻量"定调）；若实机试听不满意，备选 `SoundEvents.AMETHYST_BLOCK_CHIME`（已验证存在）。

### 1.4 配置

- `arrowDingEnabled`（bool，默认 true）
- `arrowDingVolume`（float，0.0-1.0，默认 0.6）

### 1.5 验证

- 单测：触发条件判定函数（生物/非生物/暴击分支）抽纯函数测试。
- 手动：桌面客户端射生物、射方块、暴击各验证一次。

---

## 2. 营火染色烟

### 2.1 行为

- 手持染料对**点燃的**营火右键：消耗 1 个染料，烟柱变为该染料颜色，**持续到被重新染色或营火熄灭**。
- 也支持**把染料丢进营火**：染料物品实体接触营火方块时被吞掉并同样染色（贴合原始叙事，两种交互都保留）。
- **白色染料 = 复原**：染白色恢复无色烟，提供不灭火的清除手段。
- **荧光石粉 = 夜光火星（有机扩展）**：丢入荧光石粉，营火在夜晚发出可见的微光火星（远距离信号灯），与颜色叠加生效；同样持续到重染/熄灭。2026-08-04 玩家反馈“特效太少”，夜光火星加密为每 tick 4 颗向上飘散。
- **火药 = 烟爆+火星（有机扩展）**：丢入火药，营火喷出一股猛烟爆（若已染色则用该色）+ FLAME/LAVA 火星 + 烟花爆响（FIREWORK_ROCKET_BLAST），一次性表演，不改变底色。2026-08-04 玩家反馈后加猛。
- **烈焰粉 = 火焰星花（有机扩展，2026-08-04 新增）**：丢入烈焰粉，营火迸发 FLAME + LAVA 火星（像拨旺火堆）+ BLAZE_SHOOT 响，暖橙一次性表演，不染色不夜光。
- 营火熄灭（水浇/铲子扑灭/拆除重放）清除颜色，重新点燃后是无色烟。
- 未点燃的营火右键染色无效（按原版行为处理）。
- 信号火（下方干草堆）的加高烟柱同样支持染色。

### 2.2 音效与粒子

| 事件 | 反馈 |
|---|---|
| 染色成功 | `SoundEvents.FIRE_EXTINGUISH`（已验证；音量 0.3，"丢进火里嘶一声"的手感）+ 一股对应颜色的烟雾粒子爆发 |
| 持续冒烟 | 原版烟粒子替换为染色版本（颜色 = 染料 RGB，透明度与原版烟一致） |

### 2.3 实现要点（均已对照 mcsrc/fabric jar 验证）

- **数据存储**：mixin 给 `CampfireBlockEntity` 追加 `smokeColor`（int ARGB，-1 = 无色），存取用 26.2 的 `loadAdditional(ValueInput)` / `saveAdditional(ValueOutput)`（已验 CampfireBlockEntity.java:130/147），并通过 BE update packet 同步客户端（客户端粒子生成需要读到颜色）。
- **粒子替换注入点（已验）**：烟粒子由**静态方法** `CampfireBlock.makeParticles(Level, BlockPos, boolean, boolean)` 生成（CampfireBlock.java:233），两个调用方：`CampfireBlock.java:194`（animateTick，smoking=true）与 `CampfireBlockEntity.java:104`（BE tick，smoking=false）。策略：@Inject HEAD 该方法，读 pos 处 BE 的 smokeColor，有颜色则生成染色粒子并 `ci.cancel()`，无颜色走原版。
- **自定义粒子（已验必要性）**：原版 `CampfireSmokeParticle extends SingleQuadParticle`，但**构造器私有**（已验），无法直接复用；自写 `DyedCampfireSmokeParticle`（复制原版行为：scale 3、lifetime 80-130/信号火 280-330、gravity 3e-6、上升漂移，+setColor），约 60 行。粒子类型注册仿 `ParticleTypes.BLOCK` 的 codec/streamCodec 模式（已验 ParticleTypes.java:15），选项类携带 ARGB int；工厂端用 fabric `ParticleProviderRegistry.getInstance().register(ParticleType, ParticleProvider)`（已 javap 验证 fabric-particles-v1 5.0.18）。
- **熄灭清色**：mixin `CampfireBlock` 的熄灭路径（`douse` 等）重置 `smokeColor`。
- 染料 → RGB 用 `DyeColor.getTextureDiffuseColor()`（已验 mcsrc `DyeColor.java:90`）。

### 2.4 配置

- `dyedCampfireSmokeEnabled`（bool，默认 true）

### 2.5 验证

- 手动：桌面客户端验证染色/换色/熄灭清色/信号火高烟/多人同步（BE 数据包路径）。
- 无复杂逻辑，不写单测。

---

## 3. 鹦鹉蛋

### 3.1 行为

- 新物品 `quirky:parrot_egg`，堆叠上限 16，投掷手感与鸡蛋一致（抛出弧线、无伤害、命中生物轻微击退归零伤害）。
- 落地/命中：**50% 概率孵出 1 只鹦鹉**（五种颜色随机，构造器自动选色），失败则只有碎壳效果。
  - **注意（已验 mcsrc）**：`Parrot.canBeABaby()` 返回 false（Parrot.java:157）——鹦鹉无幼年形态，孵出即成体；`setVariant` 为 private，但构造器已随机选色，无需手动设色。
- **双胞胎彩蛋**：孵化成功时有 1/32 概率孵出两只（稀有惊喜，不破坏收集节奏）。
- **筑巢本能（有机扩展）**：落在**丛林树叶/丛林原木**上的蛋孵化率提升（50%→75%）——在丛林里往树上扔蛋更划算，鼓励场景化玩法。
- 碎壳粒子颜色跟随孵出的鹦鹉羽色（孵化失败时用随机色），细节上先"看到壳色"再"看到鹦鹉"。
- 不产生投掷者归属的战斗记录（与鸡蛋一致）。

### 3.2 音效与粒子

| 事件 | 反馈 |
|---|---|
| 投掷 | `SoundEvents.EGG_THROW`（已验证） |
| 落地（无论成败） | 碎壳粒子（原版蛋壳同款粒子）+ `SoundEvents.ITEM_BREAK`（已验证）轻量变体 |
| 孵化成功 | 鹦鹉生成 + `SoundEvents.PARROT_AMBIENT`（已验证，啾啾叫）+ 少量爱心粒子 |

### 3.3 获取途径

- **仅合成获取**：鸡蛋 + 羽毛（无序合成）→ 1 个鹦鹉蛋。不做"驯服鹦鹉下蛋"（避免改鹦鹉 AI，保持轻量）。
- 平衡性：鹦鹉本身是易获取的宠物，此物品只是省去跑丛林，可接受。

### 3.4 实现要点

- 新实体 `ParrotEggEntity`：仿 26.2 鸡蛋实体 `ThrownEgg`（已验路径 `entity/projectile/throwableitemprojectile/ThrownEgg.java`：孵化用 `EntityTypes.X.create(level, EntitySpawnReason.TRIGGERED)` + `snapTo`，碎壳粒子走 `handleEntityEvent(byte 3)` 同款 ItemParticleOption 模式），注册进 `ModEntities`。
- 新物品资源走双文件清单（`items/parrot_egg.json` + `models/item/` + 贴图 + lang，对照 `bottled_cloud` 清单）。

### 3.5 配置

- `parrotEggEnabled`（bool，默认 true）
- `parrotEggHatchChance`（float，0-1，默认 0.5）
- `parrotEggTwinChance`（float，0-0.2，默认 0.03）

### 3.6 验证

- 单测：孵化概率判定函数（注入随机源）与投掷参数。
- 手动：桌面客户端投掷、孵化、失败分支、命中生物无伤害。

---

## 4. 起床保护

### 4.1 行为

- 玩家**从床上醒来**（自然天亮醒、手动起床、被怪物惊醒均算）后获得短暂增益：
  - **缓降（Slow Falling）**：防止"床边悬崖"坠落惨案。
  - **深睡加成（有机扩展）**：睡满整晚（自然醒）= **12 秒**；中途主动起床/被惊醒 = **4 秒**。好好睡觉的人得到更多保护，与原版"睡眠是正经机制"的态度一致。
- 仅服务端执行；创造/旁观者同样获得（无副作用，不特判）。
- 重复起床刷新时长。

### 4.2 音效与粒子

| 事件 | 反馈 |
|---|---|
| 保护生效 | 原版缓降药水粒子（浅灰飘羽）自然呈现；加一声轻柔的 `SoundEvents.NOTE_BLOCK_CHIME`（音高 1.2，音量 0.3） |

### 4.3 实现要点

- Mixin `Player.stopSleepInBed(boolean forcefulWakeUp, boolean updateLevelList)`（已验 mcsrc `Player.java:1321`），客户端/服务端都会调用，需 `!level.isClientSide` 守卫只在服务端加效果。
- **深睡判定（已验 API）**：`Player.isSleepingLongEnough()`（Player.java:1335，`isSleeping() && sleepCounter >= 100`）——必须在起床**前**读（方法 TAIL 时已不在睡眠状态），实现用双注入：HEAD 存 `@Unique` 快照、TAIL 消费。深睡 = `!forcefulWakeUp && 快照为 true`；中途主动起床（counter<100）与被惊醒（forceful=true）都拿 1/3 时长。
- 效果时长/等级走配置；效果应用用原版 `addEffect(new MobEffectInstance(...))`，env 来源标为普通。

### 4.4 配置

- `wakeUpProtectionEnabled`（bool，默认 true）
- `wakeUpSlowFallingSeconds`（int，0-60，默认 12）——深睡（自然醒）时长；被打断的睡眠按 1/3 计

### 4.5 验证

- 单测：起床事件→效果参数映射函数。
- 手动：桌面客户端睡觉→起床→确认 buff 与时长；悬崖边起床验证缓降救命场景。

---

## 5. 公共约定（批内通用）

- 所有配置项进 `QuirkyConfig`，经 `QuirkyConfigHolder` 读取（沿用 Cloth Config 适配经验：`BoundedDiscrete` 等）。
- 新物品全部过 `quirky-new-item-checklist`；所有 mixin 交付前过 `quirky-mixin-runtime-audit`。
- lang 键：`item.quirky.*`、`tooltip.quirky.*`、配置键沿用现有命名风格。
- 非目标：不做进度（advancement）集成、不做统计、不做音效资源替换框架。
