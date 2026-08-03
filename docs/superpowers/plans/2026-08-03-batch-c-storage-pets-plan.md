# 批 C 实现计划 — 收纳与伙伴

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现批 C 三项机制——箭袋（弹药容器）、末影袋（便携末影箱）、宠物口哨（召集/坐定指挥/夜间幻翼嘲讽三功能）。

**Architecture:** 箭袋 = DataComponent 容器物品（复用原版 `ItemContainerContents` 与 `DYED_COLOR` 组件，零自研编解码）。末影袋 = 纯物品 + 原版末影箱打开模式，零 mixin。宠物口哨 = 纯物品事件（宠物部分）+ Phantom mixin（嘲讽部分，唯一风险点，带降级预案）。

**Tech Stack:** Fabric/Minecraft 26.2 official mappings, Java 25, JUnit 5, Mockito, system Gradle。构建命令：`JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk PATH=...:$PATH gradle build --no-daemon --console=plain`。

**对应设计文档：** `docs/superpowers/specs/2026-08-03-batch-c-storage-pets-design.md`（含全部边界场景与音效表，实现前先读）。

## Global Constraints

- Work only in `/data/data/com.termux/files/home/minecraft/.worktrees/batch-c-storage-pets` on branch `feat/batch-c-storage-pets`。
- 每项机制的可修改文件范围在该任务下列出，**不得越界改任务外文件**。
- TDD：箭袋装入/取出/容量截断、口哨过滤谓词、幻翼选择抽纯函数先写失败测试。
- mixin 交付前过 `quirky-mixin-runtime-audit`；新物品过 `quirky-new-item-checklist`；tooltip 走项目统一 `Item.getTooltipImage` 扩展模式。
- 音效零新增（全部复用已验证原版 SoundEvents）。
- 贴图先示意稿用户确认后落正式资源。

## 已验证的 26.2 API 锚点

| 用途 | API | mcsrc 位置 |
|---|---|---|
| 容器组件类型 | `DataComponents.CONTAINER`（`ItemContainerContents` 值类型） | `core/component/DataComponents.java:315` |
| 组件注册模式 | `Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id, DataComponentType.builder()...)` | `DataComponents.java:435` |
| 皮革染色组件 | `DataComponents.DYED_COLOR` + `DyedItemColor.applyDyes(ItemStack, List<DyeColor>)` | `item/component/DyedItemColor.java` |
| 炼药锅洗色 | 对任意带 DYED_COLOR 物品自动生效 | `core/cauldron/CauldronInteractions.java:263-268` |
| 染色配方 | 26.2 数据驱动 `DyeRecipe`（target + dye 字段） | `item/crafting/DyeRecipe.java:25-26` |
| 末影库存 | `player.getEnderChestInventory()` → `PlayerEnderChestContainer extends SimpleContainer` | `world/inventory/PlayerEnderChestContainer.java:13` |
| 打开末影菜单 | `player.openMenu(new SimpleMenuProvider((id,inv,p) -> ChestMenu.threeRows(id,inv,container), title))` | `EnderChestBlock.java:85-94`；`ServerPlayer.openMenu` 返回 `OptionalInt`（ServerPlayer.java:1318） |
| 驯服判定 | `TamableAnimal.isOwnedBy(LivingEntity)` | `entity/TamableAnimal.java:181` |
| 坐定切换 | `TamableAnimal.setInSittingPose(boolean)`（public）；持久化字段 `orderedToSit` private（:44） | `TamableAnimal.java:146` |
| 寻路 | `Mob.getNavigation().moveTo(target, speed)` | `entity/Mob.java:214` |
| 宠物类路径 | `Wolf`（animal/wolf/:92）、`Cat`（animal/feline/:73）、`Parrot`（animal/parrot/） | 均 extends TamableAnimal（鹦鹉经 ShoulderRidingEntity） |
| 幻翼结构 | `Phantom extends Mob`：`attackPhase`(CIRCLE/SWOOP)、私有 `anchorPoint`（:49，NBT `anchor_pos`）、goals=AttackStrategy:1/SweepAttack:2/CircleAroundAnchor:3（:70-73）、`PhantomMoveControl` | `entity/monster/Phantom.java` |
| 幻翼锁定目标 | `Mob.setTarget(LivingEntity)` | `Mob.java:249` |
| 音效 | `ARMOR_EQUIP_LEATHER`、`ENDERMAN_STARE`、`GOAT_HORN_SOUND_VARIANTS`（8 变体）、`PHANTOM_SWOOP`、`ENDERMAN_TELEPORT` | 均已验证 |

---

## Task 0: 配置与 lang 脚手架

**Files:**
- Modify: `src/main/java/dev/quirky/config/QuirkyConfig.java`
- Modify: `src/main/resources/assets/quirky/lang/*.json`

**Steps:**
- [ ] 新增字段：`quiverEnabled`、`quiverCapacity`(int 1-8, BoundedDiscrete)、`enderPouchEnabled`、`enderPouchEnderResonance`、`petWhistleEnabled`、`petWhistleRadius`(int 8-64)、`petWhistleTeleportBeyondRadius`、`petWhistleTauntPhantoms`、`petWhistlePhantomMax`(int 1-5)。
- [ ] 补全配置翻译键 + `@Tooltip` 后缀键。
- [ ] Run `gradle build --no-daemon --console=plain`。

---

## Task 1: 箭袋

**Files:**
- New: `src/main/java/dev/quirky/quiver/QuiverContents.java`（组件类型注册 + 存取助手）
- New: `src/main/java/dev/quirky/quiver/QuiverLogic.java`（纯函数：吸入/倒出/容量）
- New: `src/main/java/dev/quirky/item/QuiverItem.java`
- New: `src/main/java/dev/quirky/tooltips/QuiverTooltipComponent.java` + 客户端组件（走项目 `getTooltipImage` 模式）
- New: `src/test/java/dev/quirky/quiver/QuiverLogicTest.java`
- Modify: `src/main/java/dev/quirky/ModItems.java`、`src/main/java/dev/quirky/QuirkyMod.java`（组件注册时机）、`QuirkyModClient.java`（tooltip 回调注册）
- New 资源: items/quiver.json、models/item/quiver.json、textures/item/quiver.png（染色层，先示意稿）
- New: `data/quirky/recipe/quiver.json`（皮革×3+线）、`data/quirky/recipe/quiver_dye.json`（DyeRecipe：target=quiver, dye=染料 tag）
- Modify: lang（item + tooltip 键）

**Interfaces:**
- `QuiverLogic.absorb(ItemContainerContents current, List<ItemStack> inventory, int capacity)` → 新内容 + 各槽消耗。
- `QuiverLogic.extractOne(ItemContainerContents current)` → 取出的一组 + 新内容。
- 白名单谓词：`stack.is(ItemTags.ARROWS) || stack.is(Items.FIREWORK_ROCKET)`。

**Steps:**
- [ ] 写失败测试：吸入截断（容量 4 组上限）、混合箭种顺序稳定、取出优先组、空袋取出、烟花兼容、非弹药拒收。
- [ ] 实现 `QuiverLogic`（纯数据操作 ItemContainerContents）。
- [ ] `QuiverContents`：`DataComponentType<ItemContainerContents>` 注册（仿 CONTAINER：builder().persistent(ItemContainerContents.CODEC).networkSynchronized(ItemContainerContents.STREAM_CODEC)），`QuirkyMod.onInitialize` 触发注册。
- [ ] `QuiverItem`：
  - 潜行+右键 → absorb（全背包弹药入袋，`ARMOR_EQUIP_LEATHER` + 粒子）；主手/副手持袋都响应（检查两手）。
  - 普通右键 → extractOne：优先副手空位 → 背包空位 → 掉落脚下（`popResource`，**先查 `BLOCK_DROPS` 游戏规则**——项目教训：规则关闭时先 instanceof ServerLevel 检查）。
  - 支持 `DataComponents.DYED_COLOR`（物品属性挂组件默认值=皮革原色；染色走 dye_recipe，洗色炼药锅自动）。
- [ ] tooltip：`getTooltipImage` HEAD 注入模式输出 `QuiverTooltipComponent`（4 格缩略+数量；空袋显示"空"文本），客户端注册进 `ClientTooltipComponentCallback`（项目现有管线）。
- [ ] **场景自查**：满袋再吸、死亡掉落内容保留（组件随物品）、火烧销毁内容损失（tooltip 文案提醒）、创造模式。
- [ ] 资源过 `quirky-new-item-checklist`；创造页签 TOOLS_AND_UTILITIES。
- [ ] Run focused test。

**Verification:** 单测全绿；桌面客户端装/取/满/空/染色/洗色/tooltip/死亡掉落。

---

## Task 2: 末影袋

**Files:**
- New: `src/main/java/dev/quirky/item/EnderPouchItem.java`
- Modify: `src/main/java/dev/quirky/ModItems.java`
- New 资源: items/ender_pouch.json、models/item/ender_pouch.json、textures/item/ender_pouch.png（先示意稿）
- New: `data/quirky/recipe/ender_pouch.json`（末影珍珠+皮革×2+线）
- Modify: lang

**Steps:**
- [ ] `EnderPouchItem.use`（服务端）：
  - 普通右键：`player.openMenu(new SimpleMenuProvider((id, inv, p) -> ChestMenu.threeRows(id, inv, player.getEnderChestInventory()), 末影箱标题))`；开袋播放末影箱开盖音效 + 2-3 个 `REVERSE_PORTAL` 粒子。
  - 潜行+右键：另一只手物品 `container.addItem(stack)` 塞入第一个空位；成功清空手持、播放收纳音；失败（满）低沉音效，物品留手。
  - **末影共鸣**（配置开）：16 格内 `getEntitiesOfClass(Enderman.class, ...)` 非空时 10% 概率挑一只 `setTarget(player)` + 播放 `ENDERMAN_STARE`。
  - 副手持袋同样响应。
- [ ] 注册物品/配方/lang/创造页签（TOOLS_AND_UTILITIES）。
- [ ] **场景自查**：打开中死亡、与实体末影箱同开双向同步、旁观/创造、快速收纳满箱分支。
- [ ] Run `gradle build`。

**Verification:** 桌面客户端打开/存取/快速收纳/共鸣触发（多次开袋蹲概率）/共享同步。

---

## Task 3: 宠物口哨

**Files:**
- New: `src/main/java/dev/quirky/whistle/WhistleLogic.java`（纯函数：过滤谓词、幻翼随机选择）
- New: `src/main/java/dev/quirky/item/PetWhistleItem.java`
- New: `src/main/java/dev/quirky/mixin/PhantomTauntMixin.java`
- New: `src/test/java/dev/quirky/whistle/WhistleLogicTest.java`
- Modify: `src/main/java/dev/quirky/ModItems.java`、`src/main/resources/quirky.mixins.json`
- New 资源: items/pet_whistle.json、models/item/pet_whistle.json、textures/item/pet_whistle.png（先示意稿）
- New: `data/quirky/recipe/pet_whistle.json`（木棍+线+铁粒）
- Modify: lang

**Interfaces:**
- `WhistleLogic.selectPhantoms(int available, int maxCount, RandomSource)` → 本次嘲讽数量（1→max 随机）。

**Steps:**
- [ ] 写失败测试：owner 匹配、半径边界、维度过滤（谓词）、幻翼数量范围（注入随机源）。
- [ ] 实现 `WhistleLogic`。
- [ ] `PetWhistleItem.use`（服务端）：
  - **召集**：`level.getEntitiesOfClass(TamableAnimal.class, AABB.ofSize(playerPos, 2r, 2r, 2r), e -> e.isOwnedBy(player))`（level 查询天然同维度）；半径内 → `setInSittingPose(false)`（坐着的先起身）+ `getNavigation().moveTo(player, 1.5)` + 头顶 `HEART` 粒子；超半径且 `teleportBeyondRadius` 开 → 传送到玩家身边 1-2 格空位 + `PORTAL` 粒子 + `ENDERMAN_TELEPORT`（0.3）。
  - **坐定指挥**（潜行+右键）：半径内狼/猫 `setInSittingPose(!isInSittingPose())`；同步持久化需一并翻转 `orderedToSit`（private 字段——用 `@Accessor` 于 PhantomTauntMixin 同包的新 Accessor mixin，过审计；或接受 pose-only 语义并在代码注释说明差异）。
  - **幻翼嘲讽**（夜晚 `!level.isDay()` + 配置开）：48 格内幻翼列表 → `WhistleLogic.selectPhantoms` 定数量 → 每只：传送到玩家周围 3-5 格地面位置、`setTarget(player)`、写入 NBT `quirky:taunt_until = gameTime + 600`；播放 `PHANTOM_SWOOP` + 落地扬尘。
  - 哨音：`GOAT_HORN_SOUND_VARIANTS` 随机一变体；坐定指挥用高音高短促变体区分。
- [ ] `PhantomTauntMixin`：`@Mixin(Phantom.class)`，`@Shadow private @Nullable BlockPos anchorPoint;`（本类字段 :49，安全）+ TAIL 注入 `tick`：读 NBT taunt 时间戳，激活期内每 tick `anchorPoint = 目标玩家位置.above(5)` 并兜底 `setTarget`；过期清除。**mixin 审计**：tick 描述符逐字核对；若实测盘旋半径过大/不俯冲，启用降级预案（每 20 tick 位置拉回玩家上方）。
- [ ] 注册/配方/lang/创造页签（TOOLS_AND_UTILITIES）。
- [ ] **场景自查**：别人的宠物不响应、跨维度无效、船上宠物、肩头鹦鹉不重复召集、白天/无幻翼夜晚静默。
- [ ] Run focused test。

**Verification:** 单测全绿；桌面客户端：狗/猫/鹦鹉寻路与传送、坐定切换、夜晚幻翼嘲讽→俯冲可打→掉膜→30 秒恢复（或降级行为）。

---

## 批内完成标准

- [ ] 三机制全部实现，`gradle build --no-daemon --console=plain` 通过
- [ ] 单测全绿，mixin 运行时审计清单逐项过
- [ ] 音效/粒子按设计文档逐项手动验证
- [ ] 报告：Status + 实现内容对照设计文档 + 测试结果 + 文件变更清单
