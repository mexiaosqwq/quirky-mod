# 批 C 设计文档 — 收纳与伙伴

日期：2026-08-03
状态：待用户审阅
定调：实用、不影响平衡、轻量；每个机制必须有音效/粒子/反馈等手感细节。

## 1. 箭袋（Quiver）

### 1.1 定位

存箭容器，省背包格子。弓箭手出门带 1-2 个装满的箭袋，需要时倒进背包/副手，弓照常从背包取箭。**不做"弓自动从箭袋抽箭"**（那需要深度 mixin 原版弹药查找链，复杂度和风险不配"轻量"定调）。

### 1.2 行为

- 新物品 `quirky:quiver`：堆叠 1，无耐久，内部容量 **4 组**（每组按物品自身 maxStackSize，箭类 64）。
- 可存入的物品：`#minecraft:arrows` tag 内的物品（原版箭、光灵箭、药箭、火把箭自动兼容——火把箭已按教训补过该 tag）。
- **烟花火箭兼容（有机扩展）**：箭袋也收**烟花火箭**（弩的弹药），弩手同样受益——"弹药袋"名副其实。烟花与箭共用 4 组容量（不额外扩容，避免背包失衡）。
- **装入**：手持箭袋 **潜行+右键** → 把玩家背包里所有箭类物品吸入箭袋（直到装满 4 组），播放吸入音效。
- **取出**：手持箭袋**右键**（不潜行）→ 从箭袋取出一组箭，优先放进副手空位，否则放进第一个背包空位，都满则掉落在玩家脚下（不吞物品）。
- **清空**：手持箭袋对容器（箱子/漏斗等）无特殊处理——箭袋不是容器方块，不与容器交互。
- **可染色（已验 26.2 机制）**：直接用原版 `DataComponents.DYED_COLOR`（`DyedItemColor` record，含 `applyDyes(ItemStack, List<DyeColor>)` 静态助手）——物品带此组件即自动获得炼药锅水洗褪色（已验 CauldronInteractions.java:263-268 对任意带 DYED_COLOR 的物品生效）；染色配方走 26.2 数据驱动的 `DyeRecipe`（target+dye 字段，已验 DyeRecipe.java），新增一条 dye_recipe JSON 即可。

### 1.3 数据存储

- 自定义 `DataComponent`：`quirky:quiver_contents`——值类型直接用原版 `ItemContainerContents`（自带 codec/streamCodec，项目潜影盒 tooltip 已用过该类型），注册仿 `DataComponents.CONTAINER` 模式（已验 DataComponents.java:315/435：`Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, ...)`）。
- 物品 tooltip 显示内容摘要：复用项目 tooltip 扩展模式（`Item.getTooltipImage` → 服务端组件 → 客户端渲染），画 4 格缩略图+数量，风格对齐潜影盒 tooltip。
- 箭袋物品掉落/死亡掉落时内容随组件保留（component 天然跟随 ItemStack）。

### 1.4 音效与粒子

| 事件 | 反馈 |
|---|---|
| 装入 | `SoundEvents.ARMOR_EQUIP_LEATHER`（已验证）+ 少量 `CRIT` 风格小粒子（箭支入袋的"簌簌"感） |
| 取出一组 | `SoundEvents.ARMOR_EQUIP_LEATHER`（音高略高） |
| 装满后再吸 | 只吸能吸的，已满则无动作无声音 |

### 1.5 配方与贴图

- 合成：皮革×3 + 线×1（竖排皮革+线缠绕的形状合成），贴合箭袋形象。
- 贴图建议：斜背式皮质箭袋（先示意稿后落地）。

### 1.6 边界场景清单

| 场景 | 行为 |
|---|---|
| 箭袋在副手时潜行+右键 | 主手若是箭→装入副手箭袋（兼容双持习惯）；实现上：检查主手/副手中的箭袋，谁持有谁响应 |
| 创造模式 | 装入照常消耗（创造玩家用创造栏补给），不做特判 |
| 把装箭的箭袋丢在地上再捡回 | 内容保留（组件随物品） |
| 箭袋被火烧/仙人掌销毁 | 内容物损失（与潜影盒背包内销毁一致，不做溢出保护——避免特判复杂度；tooltip 提醒玩家） |
| 空箭袋 tooltip | 显示"空"字样而非 4 个空格子图 |

### 1.7 配置

- `quiverEnabled`（bool，默认 true）
- `quiverCapacity`（int，1-8 组，默认 4）

### 1.8 实现要点与风险

- 纯物品 + DataComponent + tooltip 组件，无 mixin，风险集中在 component 编解码（26.2 API 需 javap/mcsrc 核对）。
- "吸箭"遍历背包的函数抽纯逻辑单测（容量截断、混合箭种、顺序稳定性）。

### 1.9 验证

- 单测：装入/取出/容量截断纯逻辑。
- 手动：桌面客户端装/取/满/空/tooltip 显示/死亡掉落内容保留。

---

## 2. 末影袋（Ender Pouch）

### 2.1 定位

随身携带的末影箱入口：右键打开**玩家自己的末影箱库存**（与放置的末影箱共享同一库存，这是原版 `Player.getEnderChestInventory()` 的天然语义）。省去"回家放末影箱"的跑图。

### 2.2 行为

- 新物品 `quirky:ender_pouch`：堆叠 1，无耐久。
- 右键（任意手）：服务端打开末影箱菜单（GENERIC_9x3 原版箱子界面，标题"末影箱"）。
- **快速收纳**：手持末影袋**潜行+右键**，把另一只手上的物品直接塞进末影箱第一个空位；末影箱满则失败反馈（低沉唔声），物品留在手上——探险时捡到大件不用开界面直接收。
- 无冷却、无消耗、无距离限制（末影箱本就是跨维度共享）。
- **末影共鸣（有机扩展）**：开启袋子是在撕开末影空间——**16 格内的末影人有小概率（默认 10%）被惊动**：播放凝视音效（`SoundEvents.ENDERMAN_STARE`，已验证）并可能激怒。主题自洽的风险调味（用末影的力量就要承担被末影注意），有独立配置可关，默认开。
- 移动中、疾跑中均可打开（与原版箱子菜单一致，不限制）。

### 2.3 音效与粒子

| 事件 | 反馈 |
|---|---|
| 打开 | 末影箱开盖音效（优先原版末影箱音效，无则箱子音效）+ 少量末影粒子（`PORTAL`/`REVERSE_PORTAL` 2-3 个）在玩家手部 |
| 关闭 | 原版关盖音效（菜单关闭自动处理） |

### 2.4 配方与贴图

- 合成：末影珍珠×1 + 皮革×2 + 线×1（形状合成：皮革上下夹珍珠，线在侧），成本≈1 颗珍珠，合理。
- 贴图建议：深紫色小袋+末影粒子纹（先示意稿后落地）。

### 2.5 边界场景清单

| 场景 | 行为 |
|---|---|
| 副手持袋、主手持剑 | 副手右键正常打开 |
| 打开中死亡 | 菜单关闭，库存无损（末影箱库存独立于掉落） |
| 与已放置末影箱同时打开 | 同一库存，双向同步（原版菜单语义天然支持） |
| 旁观/创造 | 均可打开，无副作用 |

### 2.6 配置

- `enderPouchEnabled`（bool，默认 true）
- `enderPouchEnderResonance`（bool，默认 true）——末影共鸣激怒开关

### 2.7 实现要点与风险

- 无 mixin：`Item.use` 服务端分支用已验证的原版末影箱打开模式（mcsrc `EnderChestBlock.java:85-94`）：
  - `PlayerEnderChestContainer container = player.getEnderChestInventory();`（类在 `world/inventory/PlayerEnderChestContainer.java`，extends SimpleContainer，已验）
  - `player.openMenu(new SimpleMenuProvider((id, inv, p) -> ChestMenu.threeRows(id, inv, container), 标题))`
  - `ServerPlayer.openMenu(MenuProvider)` 返回 `OptionalInt`（mcsrc `ServerPlayer.java:1318`），客户端收到后自动弹原版箱子界面。
- 快速收纳用 `container.addItem(stack)`（Container 接口默认方法，返回剩余），满则失败反馈。
- 风险点：末影库存是 `PlayerEnderChestContainer`（非普通 Container），`ChestMenu.threeRows` 与其 27 槽匹配，参照原版即可，无自定义槽位逻辑。

### 2.8 验证

- 手动：桌面客户端打开/存取/与实体末影箱共享验证；副手使用。
- 无复杂逻辑，不写单测。

---

## 3. 宠物口哨（Pet Whistle）

### 3.1 定位

一声哨响，把自己的宠物召集到身边。解决"宠物卡在远处/坐在那里忘了带"的痛点。白天是召集哨，**夜晚是猎膜号角**：能强制把空中的幻翼掼到地面近战，把被动挨抓变成主动狩猎。纯便利玩具+风险自平衡的小战斗玩法。

### 3.2 行为

- 新物品 `quirky:pet_whistle`：堆叠 1，无耐久。
- 右键：以玩家为中心、半径 **24 格** 内搜索**该玩家拥有**的已驯服宠物：
  - **狼（狗）、猫**：取消坐定状态 → 导航寻路走向玩家（`Navigation.moveTo(player, 1.5)`）。
  - **鹦鹉**：站在地上的走向玩家；站在投掷者肩上的不做任何事（已在身边）。
- **召唤兜底**：24 格外**或** 3 秒后仍未到达 2 格内的宠物，直接传送到玩家身边 1-2 格空位（防卡墙），传送播放末影粒子。
  - 简化实现决策：不做"3 秒监视"，直接两段式——右键瞬间：24 格内的寻路走，24 格外的立即传送。手感直接、逻辑简单。
- 只响应**自己驯服**的宠物（owner UUID 匹配），别人的宠物无反应。
- **坐定指挥（双功能）**：手持口哨**潜行+右键**，切换半径内狼/猫的坐定状态（坐着的站起来、站着的坐下；鹦鹉不受影响）——不用一个个右键去按，远程管理宠物状态。
- **夜间幻翼嘲讽（三重功能）**：**夜晚**且周围天空存在幻翼时，吹哨（普通右键）除了召集宠物，还会从 **48 格**半径内的空中幻翼里**随机选 1-3 只**强制掼到玩家身边地面：
  - 被选中的幻翼传送到玩家周围 3-5 格的地面位置，进入**嘲讽状态（30 秒）**：锁定玩家为目标持续近身扑击（接触伤害照旧），不再升空盘旋。
  - 嘲讽状态结束或幻翼死亡恢复正常行为。
  - 设计意图：幻翼膜是修鞘翅的刚需，但幻翼只在俯冲瞬间能打；掼到地面后可以用剑稳定输出——风险（主动引怪近身）与收益（稳定刷膜）自平衡。

### 3.3 音效与粒子

| 事件 | 反馈 |
|---|---|
| 吹哨 | 山羊角音色：`SoundEvents.GOAT_HORN_SOUND_VARIANTS`（已验证，8 种变体，每次吹哨随机选一种——有收集感和辨识度；音量 1.0、传播远一点，符合"召集"语义） |
| 幻翼被掼下 | 每只幻翼 `SoundEvents.PHANTOM_SWOOP`（已验证）+ 落地扬尘粒子 |
| 宠物响应 | 每只宠物头顶 3-5 个 `HEART` 粒子 |
| 远距离传送到达 | `PORTAL` 粒子 + `SoundEvents.ENDERMAN_TELEPORT`（已验证；音量 0.3） |
| 范围内无宠物 | 哨音照吹，无粒子（玩家自知） |

### 3.4 配方与贴图

- 合成：木棍 + 线 + 铁粒（形状合成，小哨子形象），廉价。
- 贴图建议：小木哨（先示意稿后落地）。

### 3.5 边界场景清单

| 场景 | 行为 |
|---|---|
| 宠物坐在船上/矿车里 | 寻路可能失败 → 不会到达 → 下次吹哨传送兜底（可接受） |
| 宠物在被围死的房间里 | 寻路失败 → 传送到玩家身边（这是 feature：救卡住的宠物） |
| 宠物在另一维度 | 不在 24 格球内 → 传送判定需同维度限制：跨维度不传送（防滥用），哨响无效。设计决策：口哨只作用于同维度宠物 |
| 驯服的鹦鹉站在肩上 | 不做任何事（已在身边），不产生粒子 |
| 多人服中别人吹哨 | 只召集吹哨者的宠物 |
| 潜行+右键坐定指挥 | 只切换状态不召集，哨音用短促变体（音高更高）区分两种用法 |
| 白天吹哨 | 无幻翼嘲讽（幻翼白天本来就会燃烧/消失），只召集宠物 |
| 无幻翼的夜晚 | 嘲讽部分静默跳过，只召集宠物 |
| 被嘲讽的幻翼被玩家击杀 | 正常掉膜（原版战利品表），嘲讽状态随实体消失 |
| 幻翼处于另一维度 | 不参与选择（同维度限制，与宠物规则一致） |

### 3.6 配置

- `petWhistleEnabled`（bool，默认 true）
- `petWhistleRadius`（int，8-64，默认 24）
- `petWhistleTeleportBeyondRadius`（bool，默认 true）——关闭则只寻路不传送
- `petWhistleTauntPhantoms`（bool，默认 true）——夜间幻翼嘲讽开关
- `petWhistlePhantomMax`（int，1-5，默认 3）——单次嘲讽幻翼数上限（随机 1→此值）

### 3.7 实现要点与风险

- 宠物部分无 mixin：`Item.use` 服务端 `level.getEntitiesOfClass(TamableAnimal/具体类, AABB, owner 过滤)`。
- 目标类型：狼 `Wolf`（`animal/wolf/Wolf.java:92`）、猫 `Cat`（`animal/feline/Cat.java:73`）、鹦鹉 `Parrot`——均 `extends TamableAnimal`（鹦鹉经 `ShoulderRidingEntity`），`isOwnedBy(LivingEntity)` 定义在 `TamableAnimal.java:181`，均已验。
- 坐定切换：`TamableAnimal.setInSittingPose(boolean)`（:146，public）；持久化字段 `orderedToSit` 为 private（:44），若需同步持久化用 @Accessor（实现时定，过 mixin 审计）；寻路用 `Mob.getNavigation().moveTo(player, speed)`（Mob.java:214）。
- **幻翼嘲讽实现路径（已验 Phantom 结构，mcsrc `entity/monster/Phantom.java`）**：`Phantom extends Mob`，持 `attackPhase`（CIRCLE/SWOOP）、私有字段 `anchorPoint`（:49，NBT 键 `anchor_pos`）、自研 goal 链（AttackStrategy:1 → SweepAttack:2 → CircleAroundAnchor:3，registerGoals :70-73）与 `PhantomMoveControl`。主方案：被选中幻翼传送到玩家身边 3-5 格（一次性）+ `setTarget(吹哨玩家)`（Mob.setTarget :249）+ @Shadow `anchorPoint` 重设为玩家位置上方 5 格（仿 :156 原版赋值）+ NBT 记 taunt 截止时间；嘲讽期 mixin `Phantom.tick` TAIL 每 tick 刷新 anchor/target 跟随玩家，使盘旋与俯冲都发生在玩家头顶——俯冲接触伤害走原版 SweepAttack 路径，可用剑稳定输出。**降级预案**：若 goal 行为实测不驯（盘旋半径过大/不俯冲），降级为嘲讽期内每 20 tick 把幻翼拉回玩家上方（位置强约束），行为仍成立。

### 3.8 验证

- 单测：过滤谓词（owner 匹配、维度、半径）、幻翼随机数量与选择范围纯函数。
- 手动：桌面客户端驯狗/猫/鹦鹉各一，验证寻路、超半径传送、别人宠物不响应；**夜晚造几只幻翼验证嘲讽落地、30 秒恢复、击杀掉膜**。

---

## 4. 公共约定

- 新物品过 `quirky-new-item-checklist`；tooltip 走项目统一扩展模式。
- 三项互不依赖，可并行开发。
- 非目标：不做饰品槽/Curios 兼容、不做箭袋自动供弹、不做末影袋冷却或限制。
