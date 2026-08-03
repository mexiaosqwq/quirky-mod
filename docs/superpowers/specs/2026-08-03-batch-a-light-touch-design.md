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

- 任意箭矢（原版箭、药箭、火把箭等所有 `AbstractArrow` 子类）命中**生物**（LivingEntity）时，在命中点播放一声清脆的"叮"。
- 命中方块、命中非生物实体（画、矿车、物品展示框）不响。
- **暴击命中**（`arrow.isCritArrow()`）播放更高音高的变体，形成精准奖励感。
- 目标**举盾成功格挡**时不响（被盾挡掉的箭不配拥有成就感）。
- **击杀确认**：这一箭直接击杀目标时，"叮"声音量 ×1.2 + 一小簇亮晶晶粒子——收尾反馈，射猎手感的高光时刻。
- 声音由服务端 `level.playSound(null, pos, ...)` 播放，自然距离衰减。

### 1.2 音效与粒子

| 事件 | 音效 | 音高 | 音量 |
|---|---|---|---|
| 普通命中 | `SoundEvents.NOTE_BLOCK_BELL`（26.2 已验证；bell 音色即经典 pling） | 1.5 | 配置值（默认 0.6） |
| 暴击命中 | 同上 | 1.8 | 配置值 |

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
- 营火熄灭（水浇/铲子扑灭/拆除重放）清除颜色，重新点燃后是无色烟。
- 未点燃的营火右键染色无效（按原版行为处理）。
- 信号火（下方干草堆）的加高烟柱同样支持染色。

### 2.2 音效与粒子

| 事件 | 反馈 |
|---|---|
| 染色成功 | `SoundEvents.FIRE_EXTINGUISH`（已验证；音量 0.3，"丢进火里嘶一声"的手感）+ 一股对应颜色的烟雾粒子爆发 |
| 持续冒烟 | 原版烟粒子替换为染色版本（颜色 = 染料 RGB，透明度与原版烟一致） |

### 2.3 实现要点

- **数据存储**：mixin 给 `CampfireBlockEntity` 追加 `smokeColor`（int ARGB，-1 = 无色），写入 BE 的 save/load（26.2 BE 数据格式），并通过 BE update packet 同步客户端（客户端 `animateTick` 需要读到颜色）。
- **粒子替换**：`CampfireBlock.animateTick` 中生成烟粒子的位置注入（mcsrc `CampfireBlock.java:235-237` 附近），有颜色时改用自定义粒子 `quirky:dyed_campfire_smoke`。
- **自定义粒子**：复制原版 `CampfireSmokeParticle` 行为并支持颜色参数（若原版类 final 或构造器不兼容，则独立实现一份，约 60 行；粒子行为=缓慢上升+轻微漂移，与原版一致）。
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
- 落地/命中：**50% 概率孵出 1 只幼年鹦鹉**（五种颜色随机），失败则只有碎壳效果。
- **双胞胎彩蛋**：孵化成功时有 1/32 概率孵出两只（稀有惊喜，不破坏收集节奏）。
- 碎壳粒子颜色跟随孵出的鹦鹉羽色（孵化失败时用随机色），细节上先"看到壳色"再"看到鹦鹉"。
- 不产生投掷者归属的战斗记录（与鸡蛋一致）。

### 3.2 音效与粒子

| 事件 | 反馈 |
|---|---|
| 投掷 | `SoundEvents.EGG_THROW`（已验证） |
| 落地（无论成败） | 碎壳粒子（原版蛋壳同款粒子）+ `SoundEvents.ITEM_BREAK`（已验证）轻量变体 |
| 孵化成功 | 幼年鹦鹉生成 + `SoundEvents.PARROT_AMBIENT`（已验证，啾啾叫）+ 少量爱心粒子 |

### 3.3 获取途径

- **仅合成获取**：鸡蛋 + 羽毛（无序合成）→ 1 个鹦鹉蛋。不做"驯服鹦鹉下蛋"（避免改鹦鹉 AI，保持轻量）。
- 平衡性：鹦鹉本身是易获取的宠物，此物品只是省去跑丛林，可接受。

### 3.4 实现要点

- 新实体 `ParrotEggEntity`（仿鸡蛋实体 `ThrownEggProjectile`），注册进 `ModEntities`。
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
  - **缓降（Slow Falling）8 秒**：防止"床边悬崖"坠落惨案。
- 仅服务端执行；创造/旁观者同样获得（无副作用，不特判）。
- 重复起床刷新时长。

### 4.2 音效与粒子

| 事件 | 反馈 |
|---|---|
| 保护生效 | 原版缓降药水粒子（浅灰飘羽）自然呈现；加一声轻柔的 `SoundEvents.NOTE_BLOCK_CHIME`（音高 1.2，音量 0.3） |

### 4.3 实现要点

- Mixin `Player.stopSleepInBed(boolean forcefulWakeUp, boolean updateLevelList)`（已验 mcsrc `Player.java:1321`）TAIL，客户端/服务端都会调用，需 `!level.isClientSide` 守卫只在服务端加效果。
- 效果时长/等级走配置；效果应用用原版 `addEffect(new MobEffectInstance(...))`，env 来源标为普通。

### 4.4 配置

- `wakeUpProtectionEnabled`（bool，默认 true）
- `wakeUpSlowFallingSeconds`（int，0-60，默认 8）

### 4.5 验证

- 单测：起床事件→效果参数映射函数。
- 手动：桌面客户端睡觉→起床→确认 buff 与时长；悬崖边起床验证缓降救命场景。

---

## 5. 公共约定（批内通用）

- 所有配置项进 `QuirkyConfig`，经 `QuirkyConfigHolder` 读取（沿用 Cloth Config 适配经验：`BoundedDiscrete` 等）。
- 新物品全部过 `quirky-new-item-checklist`；所有 mixin 交付前过 `quirky-mixin-runtime-audit`。
- lang 键：`item.quirky.*`、`tooltip.quirky.*`、配置键沿用现有命名风格。
- 非目标：不做进度（advancement）集成、不做统计、不做音效资源替换框架。
