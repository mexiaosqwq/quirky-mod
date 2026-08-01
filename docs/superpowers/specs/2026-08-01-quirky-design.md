# Quirky 模组设计文档

- 日期：2026-08-01
- 状态：待用户审阅
- 目标环境：Minecraft 26.2 / Fabric / Java 25
- 依赖：Fabric API `0.155.2+26.2`，不引入其他模组 API

## 1. 目标

做一个“夸克风味”的小机制合集模组。机制以 Quark 为灵感，但全部原创实现，不复制其代码。v1 交付 7 个可独立工作的机制，并保证整体可编译、可安装。

## 2. v1 范围

1. 地图悬浮预览：鼠标悬停填绘地图时，在 tooltip 中显示地图内容。
2. 右键收割补种：右键成熟作物自动收割并尽可能补种，兼容西瓜、南瓜和可可豆。
3. 双开门联动：打开一扇门时，相邻匹配的另一扇门同步开关。
4. 时钟悬浮信息：显示游戏内天数和时间。
5. 新物品“云瓶”：使用后获得缓慢下落，消耗云瓶并返还玻璃瓶。
6. 背包右键装备/替换：在背包或带玩家装备栏的容器界面，右键可装备物品，并与已穿戴装备直接交换。
7. 吃西瓜片获得西瓜种子：完整吃完一片西瓜后获得 1 个西瓜种子。

## 3. 非目标

- 不做配置界面或配置文件。
- 不做保留图腾（写入 PENDING，见第 9 节）。
- 不做创造模式“物品列表”中直接穿戴装备的接入。
- 不做新维度、新生物、新方块或新世界生成。
- 不做 Forge/NeoForge 多加载器支持。

## 4. 架构

- 模组 ID：`quirky`；显示名称：`Quirky`。
- 单个 Fabric 模组，沿用模板的 `splitEnvironmentSourceSets`：通用/服务端逻辑放 `src/main`，客户端逻辑放 `src/client`。
- 按机制拆包，避免单一入口类膨胀：
  - `harvest`：右键收割补种
  - `double_doors`：双开门联动
  - `tooltips`：地图、时钟提示
  - `equip_swap`：背包右键装备/替换
  - `item`：云瓶注册与使用逻辑
  - `food`：吃西瓜片获得西瓜种子
- 优先使用 Fabric API 事件；Fabric API 没有对应钩子的地方使用 Mixin，注入点以本地 26.2 反编译源码为准。
- 版本映射以 26.2 官方映射为准；已安装的 `minecraft-modding` skill 主要覆盖 1.21.x/Yarn，仅作为结构参考。

## 5. 机制规格

### 5.1 地图悬浮预览

行为：

- 只对带 `MAP_ID` 组件的填绘地图生效，空地图不显示。
- 任意有物品 tooltip 的界面都生效，包括背包、容器和快捷栏。
- 预览尺寸固定为 64x64，包含地图纹理和玩家/旗帜/物品展示框标记。
- 客户端地图数据不可用时静默跳过，不显示报错文本。

实现方式：

- 在 `MapItem.getTooltipImage` 注入自定义 `TooltipComponent`。
- 客户端通过 Fabric `ClientTooltipComponentCallback` 转换为自定义渲染组件。
- 用 `MapRenderer.extractRenderState` 生成 `MapRenderState`，再用 `GuiGraphicsExtractor.map` 按比例绘制。
- 回调遇到非地图的 `TooltipComponent` 时返回 `null`，沿用原版渲染。

### 5.2 右键收割补种

行为：

- 种子作物：小麦、胡萝卜、马铃薯、甜菜和下界疣。
- 手持物品不限，空手、工具、种子都能触发收割。
- 未成熟作物和未成熟可可豆保持原版行为；骨粉催熟不被拦截。
- 收割掉落按原版方块战利品生成。
- 种子作物：补种优先从背包扣除 1 个种子；背包没有时，从本次掉落列表里扣除 1 个对应种子再补种；都没有则只收割，不留幼苗。胡萝卜和马铃薯以作物本身作为种子。
- 创造模式同样触发收割，但不生成掉落、不消耗种子，只重置作物或移除果实。
- 西瓜/南瓜：右键已连接的茎，按茎的朝向找到果实方块，按原版战利品掉落并移除果实；茎保留并回到 7 龄。
- 西瓜/南瓜：已连接茎旁边没有果实方块时不做任何动作。
- 可可豆：成熟可可豆右键收获可可豆，并把 `AGE` 重置为 0，继续挂在原方块上，不需要消耗种子。

实现方式：

- 服务端使用 Fabric `UseBlockCallback`。
- 先通过 `Block.getDrops` 生成掉落列表，再决定补种扣除来源；剩余掉落用 `Block.popResource` 生成。
- 通过 `CropBlock.isMaxAge` 与 `NetherWartBlock` 的成熟状态判断。
- 补种时 `CropBlock` 使用 `getStateForAge(0)`，`NetherWartBlock` 使用 `AGE=0` 重建状态。
- 西瓜/南瓜匹配 `Blocks.ATTACHED_MELON_STEM` 与 `Blocks.ATTACHED_PUMPKIN_STEM`，按 `FACING` 找到 `Blocks.MELON`/`Blocks.PUMPKIN` 果实；移除果实方块时带更新标志，让茎自动回到 7 龄。
- 可可豆匹配 `CocoaBlock` 且 `AGE == 2`，调用掉落并设置 `AGE=0`。

### 5.3 双开门联动

行为：

- 玩家手动开/关一扇门时，相邻的同一类型、同一朝向、铰链互为另一侧的门同步开/关。
- 铁门等不能手开的门保持原版规则，不强行联动。
- 单独放置的门不受影响。

实现方式：

- 在 `DoorBlock.useWithoutItem` 尾部注入。
- 无论点击上半块还是下半块，都先归一到下半块作为基准。
- 根据 `FACING` 和 `HINGE` 计算相邻门位置。
- 使用原版 `DoorBlock.setOpen` 同步另一半。

### 5.4 时钟悬浮信息

行为：

- 时钟 tooltip 追加一行游戏内时间：`Day X · HH:MM`。
- 其他物品 tooltip 不改变。

实现方式：

- 客户端在 `ItemStack.getTooltipLines` 生成结果后追加信息行。
- 仅对 `minecraft:clock` 生效。

### 5.5 云瓶

行为：

- 物品 ID：`quirky:bottled_cloud`。
- 右键使用获得 20 秒缓慢下落效果。
- 实际消耗云瓶时返还 `minecraft:glass_bottle`；创造模式不消耗、不返还。
- 合成配方：1 个玻璃瓶 + 1 个幻翼膜，无序合成。
- 放入创造模式“工具与实用物品”页签。
- 提供 16x16 物品图标、item model、`en_us` 与 `zh_cn` 文本。

### 5.6 背包右键装备/替换

行为：

- 在生存背包、箱子等包含玩家装备栏的界面中，右键背包里的可装备物品时直接装备。
- 如果对应装备槽已有物品，直接交换，例如胸甲与鞘翅互换。
- 鼠标光标持有物品时不触发，避免与普通物品移动冲突。
- 创造模式背包页签与快捷栏同样支持；创造“物品列表”中的槽位不接入。
- 遵循原版装备限制：不可装备到错误槽位；已穿戴物品带防脱卸附魔且非创造时不允许替换。

实现方式：

- 客户端用 Fabric `ScreenMouseEvents` 拦截右键点击。
- 发送自定义网络包，包含 `containerId` 与源槽位。
- 服务端校验槽位和物品后，通过原版 `ArmorSlot.setByPlayer` 完成交换，保留装备音效与 `onEquipItem` 逻辑。

### 5.7 吃西瓜片获得西瓜种子

行为：

- 玩家完整吃完 `minecraft:melon_slice` 后，获得 1 个 `minecraft:melon_seeds`。
- 仅在实际消耗西瓜片的生存/冒险模式触发；创造模式不触发，避免无限复制。
- 背包满时，种子掉落在玩家位置。
- 服务端判定，不依赖客户端数据。

实现方式：

- 在 `LivingEntity.completeUsingItem` 的 `finishUsingItem` 调用之后、`stopUsingItem` 之前挂钩。
- 校验被食用的物品是 `Items.MELON_SLICE`、实体为 `ServerPlayer`，且玩家没有无限物品权限。
- 使用 `Inventory.add` 放入种子，失败时调用 `player.drop`。

## 6. 数据与资源

- `fabric.mod.json`：使用 `quirky` ID，声明 main/client 入口、依赖和版本。
- 云瓶资源：
  - `assets/quirky/models/item/bottled_cloud.json`
  - `assets/quirky/textures/item/bottled_cloud.png`
  - `data/quirky/recipe/bottled_cloud.json`
  - `assets/quirky/lang/en_us.json`、`zh_cn.json`
- 模组图标沿用模板占位图标，v1 不单独制作启动页图标。

## 7. 验收标准

- 使用 JDK 25 执行 `gradle build` 成功，产出 `build/libs/quirky-<version>.jar`。
- 服务端机制通过 dedicated server 冒烟测试；客户端机制因 Termux 无 GUI，以编译通过和代码审查为准。
- 各机制验收点：
  - 地图：填绘地图 tooltip 出现 64x64 预览；空地图无变化。
  - 收割：不限手持；有种子时从背包扣并补种；背包没有但掉落有种子时少掉 1 个并补种；都没有时只收割；创造模式不生成掉落、不消耗种子；骨粉对未成熟作物不覆盖；下界疣可收割。
  - 西瓜/南瓜：右键已连接茎产出原版掉落，果实方块被移除，茎保留；无果实时不动作。
  - 可可豆：成熟可可豆右键产出可可豆，并重置为幼果继续附着。
  - 双开门：成对木门同步开关；铁门不被强行联动；单门不受影响。
  - 提示：时钟显示信息；其他物品 tooltip 不变。
  - 云瓶：使用获得缓慢下落；实际消耗时返还玻璃瓶，创造模式不消耗、不返还；配方可合成。
  - 装备替换：右键胸甲/鞘翅可互换；光标持物时不触发；箱子界面可用。
  - 吃西瓜：生存模式吃完西瓜片获得 1 个西瓜种子；创造模式不触发；背包满时种子掉落。

## 8. 风险与边界

- 26.2 属于新版本且使用官方映射，部分 API 与 1.21.x 不同，实施时以本地反编译源码为准。
- 地图渲染在 26.2 改为 `GuiGraphicsExtractor`/`MapRenderState` 体系，tooltip 绘制路径需要在实施时验证。
- 客户端视觉效果无法在本机运行 GUI 验证，需要后续在桌面环境运行 `runClient` 检查。
- 创造模式“物品列表”直接穿戴不在范围内；创造模式背包页签/快捷栏换装在 26.2 已实现。

## 9. PENDING：保留图腾

保留图腾（Totem of Holding 思路）不在 v1 实现：

- 目标行为：玩家死亡时把背包存入图腾物品，之后右键取回。
- 原因：涉及死亡事件、物品组件持久化与恢复语义，复杂度高于 v1 的其他机制。
- 条件：v1 的 7 个机制全部通过验收后，单独进入 brainstorm/spec 流程再实施。

## 10. 已确认决策

- 用户确认采用单 Fabric 模组、只依赖 Fabric API、不做配置界面的方案。
- 用户确认 v1 为 7 个机制，保留图腾明确降级为 PENDING。
- 用户确认 Quark 仅作为设计参考，不直接复制代码。
