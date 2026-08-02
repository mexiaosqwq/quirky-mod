# Quirky 客户端实用功能合集设计文档

- 日期：2026-08-02
- 状态：待用户审阅
- 目标环境：Minecraft 26.2 / Fabric / Java 25
- 依赖：Fabric API `0.155.2+26.2`、cloth-config、modmenu（均已有）
- 定位：以 Quark 客户端/红石小功能为蓝本的一批实用小机制，全部原创实现；优先保证"一个能用的整体"

## 1. 目标

在 Quirky 现有机制（地图预览、时钟 tooltip、右键换装、双开门、云瓶、保留图腾等）之外，新增 15 个实用小功能，覆盖：tooltip 扩展、HUD 交互、渲染 tweak、交互 tweak、新方块/物品。全部可独立开关（复用 cloth-config 开关体系）。

## 2. v2 范围（15 项）

| 分组 | 功能 |
|---|---|
| Tooltip 扩展 | 潜影盒 tooltip、食物 tooltip、属性图标 tooltip |
| HUD 交互 | 使用量挂件、死亡电影镜头 |
| 渲染 tweak | 灵魂光源、草地增绿 |
| 交互 tweak | 远距中键拾取、爬梯吸附、装备替换·副手扩展 |
| 新方块/物品 | 金按钮、铁按钮、黑曜石压力板、火把箭、木漏斗 |

## 3. 非目标

- 不做截图模式（Camera Mode，PENDING，见第 11 节）。
- 不做服务端验证"26.2 是否已有同类 mod"——本批次按需求直接实现。
- 不改动已验收的 8 个现有机制。
- 不引入新依赖（仍只有 Fabric API + cloth-config + modmenu）。

## 4. 架构约定（对齐现有实现）

- 注册：`ResourceKey.create(Registries.BLOCK/ITEM, QuirkyMod.id(...))` + `Registry.register(BuiltInRegistries...)` + `Properties.setId(...)`（云块/云瓶模式）。
- 网络包：`PayloadTypeRegistry.serverboundPlay().register` + `ServerPlayNetworking.registerGlobalReceiver`；客户端 `ClientPlayNetworking.send`（equip_swap payload 模式）。
- Tooltip 组件：服务端 `TooltipComponent`（经 `Item.getTooltipImage` 注入）+ 客户端 `ClientTooltipComponent`（`ClientTooltipComponentCallback` 注册，见 `ClientMapTooltipComponent`）；26.2 绘制走 `extractImage(Font, x, y, w, h, GuiGraphicsExtractor)`。
- 配置：`QuirkyConfig` 加字段（`@ConfigEntry.Category("toggles")` 布尔开关 + 数值参数），`QuirkyConfigHolder.get().xxx` 读取；不做独立配置文件。
- 新方块/物品资源必须双文件：`assets/quirky/items/<id>.json`（新格式 `{"model": {"type": "minecraft:model", "model": "quirky:item/<id>"}}`）+ `assets/quirky/models/item/<id>.json`（对照 bottled_cloud 逐项核对，已知陷阱）。
- 方块还须：`blockstates/<id>.json` + `models/block/<id>.json` + `models/item/<id>.json`（parent 指向 block 模型）+ 纹理。

## 5. 功能规格

### 5.1 潜影盒 tooltip（客户端）

行为：
- 悬停任意潜影盒（16 色 + 普通）时，tooltip 内显示盒内 3x9 内容物网格；空格子留空。
- 内容从物品 `DataComponents.CONTAINER` 读取（实施时以 26.2 反编译源码确认组件路径）。
- 显示 27 格物品图标 + 数量；与地图预览同样式背景。
- 仅客户端，服务端无需改动。

实现：
- `Item.getTooltipImage` mixin（MapTooltipMixin 模式）：潜影盒返回 `ShulkerTooltipComponent(containerList)`。
- 客户端 `ClientShulkerTooltipComponent implements ClientTooltipComponent`：`extractImage` 内逐格绘制物品图标与数量（`GuiGraphicsExtractor` 的物品绘制 API 以本地源码为准），宽高按 3x9 网格计算（每格 16px + 内边距）。
- `QuirkyModClient` 的 `ClientTooltipComponentCallback` 注册转换。

验收：悬停潜影盒出现 3x9 内容预览；空格无图标；带物品数量显示；其他物品 tooltip 不变。

### 5.2 食物 tooltip（客户端）

行为：
- 悬停可食用物品时，tooltip 追加一行（Quark 风格图标行）：鸡腿图标 + 回复饥饿值 + 饱和度图标 + 饱和度数值。
- 读取 `DataComponents.FOOD`（nutrition、saturation）；食物效果（如金苹果）不展开列表，只显示基础两值。
- 创造模式与生存一致显示。

实现：
- 沿用 tooltip 组件体系：`FoodTooltipComponent`（自定义）+ 客户端渲染，鸡腿/饱和度图标复用原版 HUD sprite（`GuiSprites` 中 hunger/saturation 图标，实施时确认 26.2 的 sprite 引用方式）。
- 也可退化为 tooltip lines 文本行（若 sprite 引用不可行）；优先图标方案。

验收：悬停面包/牛排/金苹果出现鸡腿+饱和度数值行；无 food 组件的物品不变。

### 5.3 属性图标 tooltip（客户端）

行为：
- 悬停武器/工具/盔甲时，以图标行显示装备属性：
  - ⚔ 攻击伤害（含附魔加成后的实际值）
  - ⚡ 攻击速度
  - 🛡 护甲值、韧性
  - 击退抗性、移动速度（有则显示）
- 附魔伤害计算：锋利/亡灵杀手/节肢杀手按 26.2 原版公式计入攻击伤害（实施时以反编译源码与实测验证公式）。
- 按住 Shift 时隐藏自定义行、显示原版 tooltip（对照查看）。

实现：
- 解析 `DataComponents.ATTRIBUTE_MODIFIERS`（按槽位过滤主手/护甲槽修饰符）+ 物品基础属性（`Item.getDefaultAttributeModifiers`）。
- 图标为自绘 16x16 sprite：`textures/gui/quirky/attribute/{attack_damage,attack_speed,armor,toughness,knockback,movement}.png`。
- 渲染：自定义 `AttributeTooltipComponent`（图标 + 数值文本行）；Shift 判定用 `Screen.hasShiftDown()`。

验收：钻石剑显示 7 攻击伤害 + 1.6 攻速；锋利 V 显示 9.5（7+2.5，公式以实测为准）；钻石胸甲显示 8 护甲 + 2 韧性；Shift 显示原版文本。

### 5.4 使用量挂件（客户端）

行为（对齐 Quark UsageTicker 源码细节 + 用户定制）：
- 快捷栏左侧：背包中**任一物品数量发生变化**（拾取增加 / 使用消耗减少）时，滑出显示该物品图标 + 背包总数；持续约 2.5 秒后滑回。
- 快捷栏右侧：4 件盔甲槽**耐久下降**时，滑出横排 4 条耐久条（图标 + 耐久条）；耐久连续不变 3 秒后滑回。
- 动画（Quark 同款）：从下方滑入 20px，ease-out 曲线 `-p*(p-2)*20`；显示周期 60 tick（动画各 5 tick + 保持 50 tick）；滑出为反向动画。
- 背包整理导致的多槽同时变化不触发（见实现）。

实现：
- 纯客户端快照方案：客户端每 tick 记录背包（36+4+1 槽）`(itemId, count)` 快照并对比；单槽 count 增→拾取事件，单槽减→消耗事件，同帧变化槽数 ≥ 2 判定为整理/交换不触发。
- 总数统计（Quark 同款）：遍历背包按 `isSameItemSameComponents` 求和；`max(总数, 当前槽数量)`；BlockItem 即使不可堆叠也显示。
- 护甲耐久：每 tick 读 4 盔甲槽 damage，变化则刷新右侧挂件。
- 渲染：`HudRenderCallback`/`Gui.render` mixin 绘制；位置：左/右两侧对齐快捷栏高度（`window.getGuiScaledHeight()` 基准）；元素间隔 20px。
- config：`tickerHoldTicks`（20~200，默认 50）、`tickerAnimTicks`（2~20，默认 5）。

验收：拾取 3 个圆石左侧滑出圆石总数；吃面包后左侧滑出剩余面包数；盔甲被攻击掉耐久右侧滑出横排 4 条耐久条；3 秒无变化收回；整理背包不闪挂件；动画为自下而上 ease-out。

### 5.5 死亡电影镜头（客户端 + 轻服务端）

行为：
- 玩家死亡瞬间不直接进死亡界面：镜头切第三人称，从死亡点附近开始，环绕旋转 360° 并缓慢拉远（约 2.5 秒，config 可调 2~5s），展示尸体/掉落物/凶手方位后，进入原版死亡界面。
- 镜头播放期间屏蔽输入（不响应 WASD/视角）；可按 Esc 提前跳过。
- 纯视觉：不影响服务端死亡流程、不掉落、不复活。

实现：
- 服务端：`ServerPlayer.die` 处（mixin，MelonSeedMixin 同文件域）发送 `DeathCamPayload`（死亡位置、维度、朝向）。
- 客户端：收到 payload 后启动镜头状态机：
  - mixin `Camera.setup`：镜头播放期间用插值位置/旋转（死亡点 + 半径 2→6 格环绕，yaw 0→360°，pitch 缓慢 -10°→-25°）覆盖相机；
  - mixin `GameRenderer` 或 `Minecraft`：播放期间暂停死亡界面显示（原版 `LocalPlayer.die` 触发的死亡屏幕），结束后正常进入；
  - `KeyboardHandler`/`MouseHandler`：播放期间忽略视角输入。
- 状态结束回调：`Minecraft.setScreen(DeathScreen)` 由原版流程继续。

验收：死亡后先播放 ~2.5s 环绕镜头再出现死亡界面；镜头内掉落物可见；Esc 可跳过；创造/旁观不触发。

### 5.6 灵魂光源（客户端渲染）

行为：
- 火把（含墙上火把）、灯笼放置在**灵魂沙/灵魂土正上方**（下表面接触）时，渲染为灵魂变体：火焰与灯体呈灵魂青色。
- 蜡烛放置在灵魂方块上时：火焰部分渲染为灵魂青色（原版无灵魂蜡烛，自定义火焰着色）。
- 破坏/移动后恢复原样（渲染按下方方块动态判定，无状态存储）。
- 火焰粒子（火把/蜡烛粒子）在灵魂方块上时同样呈青色。

实现（实施时以 26.2 渲染源码验证，备选方案见风险）：
- 首选：mixin `BlockModelShaper.getBlockModel`（或等价模型选择点），当 state 为 torch/wall_torch/lantern 且正下方为 soul_sand/soul_soil 时返回 soul_torch/soul_lantern 的 BakedModel；蜡烛返回自定义"灵魂蜡烛"模型（复制原版蜡烛模型 + 替换火焰纹理为自绘灵魂火焰 `textures/block/quirky_soul_candle_flame.png`）。
- 粒子：mixin 火焰粒子创建点（`ParticleEngine`/`FlameParticle`），按所在方块邻居判定替换为 `soul_fire_flame` sprite。
- config：`soulLighting` 开关。

验收：火把插在灵魂沙上呈青色火焰（与灵魂火把一致）；灯笼同理；蜡烛火焰青色；敲掉后恢复橙色；灵魂方块本身不受影响。

### 5.7 草地增绿（客户端）

行为（对齐 Quark GreenerGrass 源码）：
- 对草地相关方块的颜色做 3x3 颜色矩阵卷积（Quark 同款：默认对角矩阵 R×0.89、G×1.11、B×0.89——压红蓝提绿，恢复 Alpha/Beta 鲜艳风）。
- 作用方块：草方块、短草、蕨、大型蕨、甘蔗、盆栽蕨 + 树叶/藤蔓（affectLeaves 开关）。
- 强度滑块（0.5~1.5，默认 1.0）缩放矩阵对角强度；1.0 = Quark 默认效果，拉低趋近原版。
- 只影响渲染着色，不改数据。

实现：
- Fabric `ClientBlockColorProviderCallback` 事件：对目标方块注册包装 provider（委托原 provider 取色 + 矩阵卷积），26.2 对应 API 以本地反编译源码为准。
- config：`greenerGrass` 开关、`grassMultiplier`（滑条 0.5~1.5，默认 1.0）、`grassAffectLeaves`（默认 true）。

验收：滑块拉高后草地/丛林树叶更鲜艳；1.0 时呈现 Quark 默认增绿；0.5 时接近原版；不影响水色/天空；只改渲染不改存档。

### 5.8 远距中键拾取（客户端）

行为：
- 中键拾取（pick block）距离扩展：创造默认 100 格、生存默认 12 格，config 可调（创造 16~256、生存 4~64）。
- 其余拾取行为与 26.2 原版一致（创造取方块/生存切同种物品）。

实现：
- mixin `Minecraft.pickBlock`（或原版拾取射线入口）：启用时用扩展距离对 `level.clip` 重新射线，命中结果进入原版拾取逻辑。
- config：`pickRangeCreative`、`pickRangeSurvival`。

验收：创造模式中键可拾取 100 格外的方块；生存模式中键可拾取 12 格外背包已有的同种方块；config 生效。

### 5.9 爬梯吸附（客户端）

行为：
- 玩家在梯子/藤蔓上爬行且未按左右键时，身体自动平滑吸附到梯子所在方块中心线（x/z 向），不再歪着爬。
- 按住左右键时以手动控制优先，不干预。

实现：
- mixin `LocalPlayer.aiStep`（或移动 tick）：`climbing()` 为真且左右输入为 0 时，计算与所在方块中心偏移，给 deltaMovement 施加指向中心的修正分量（修正强度 config 可调）。
- config：`ladderSnap` 开关、`ladderSnapStrength`。

验收：爬梯时松左右键自动居中；按左/右仍可正常移动离开梯子；不影响非爬梯状态。

### 5.10 装备替换·副手扩展（客户端 + 服务端）

行为：
- 现有背包右键装备/替换扩展：右键背包中的**盾牌**或**火把**时，与副手槽（offhand）直接交换。
- 其他物品右键行为不变（走原版 EQUIPPABLE 装备逻辑）。
- 继承现有约束：光标持物不触发、创造/生存一致、防脱卸附魔保护。

实现：
- 客户端 `EquipSwapClient`：右键拦截条件放宽——`DataComponents.EQUIPPABLE` 存在，**或**物品为盾牌/火把（`Items.SHIELD`、`Items.TORCH`/`ItemTags.TORCHES`）。
- 服务端 `EquipSwapServer.trySwap`：目标槽计算扩展——盾牌/火把 → 副手槽（inventoryIndex 40 / `EquipmentSlot.OFFHAND`）；交换逻辑复用现有 `setByPlayer` 路径。
- config：`offhandSwap`（并入 `equipSwap` 开关或独立，独立更清晰）。

验收：右键背包盾牌与副手盾牌互换；右键火把装入副手；副手已有物品时互换；非盾牌/火把物品仍走主手逻辑。

### 5.11 金按钮 / 5.12 铁按钮（服务端 + 资源）

行为：
- 金按钮：激活 2 红石刻（0.1s）短脉冲；合成 = 任意木按钮 + 金粒（无序）。
- 铁按钮：激活 5 秒（100 刻）长脉冲；合成 = 石质按钮 + 铁粒（无序）。
- 其余按钮行为与原版一致（可放置 6 面、红石输出、活塞/箭不可触发、音效取金属音）。

实现：
- 方块注册（ModBlocks 模式）：`ButtonBlock(BlockSetType, holdTicks, Properties)`；holdTicks 金=2、铁=100；新建金属 BlockSetType（金属音效、不可箭触发），两个按钮共用。
- 合成配方 JSON（`data/quirky/recipe/`）：无序（`crafting_shapeless`），金 = `item: wooden button tag` + `minecraft:gold_nugget`；铁 = `minecraft:stone_button` + `minecraft:iron_nugget`。
- BlockItem 注册 + 创造页签（`CreativeModeTabs.REDSTONE_BLOCKS`）。
- 资源：blockstates（facing/powered 变体）、models/block、models/item、items/ 双文件、textures（自绘金色/铁色按钮）、lang 键。

验收：金按钮点按 0.1s 后熄灭；铁按钮亮 5s；配方可合成；红石比较器/中继器正常响应。

### 5.13 黑曜石压力板（服务端 + 资源）

行为：
- 只有**玩家**踩上才输出信号（Quark 原版行为，不做动物扩展）；实体/动物/掉落物不触发。
- 合成：2 黑曜石横排（同原版压力板形状）。

实现：
- `PressurePlateBlock` 子类：实体检测仅匹配 `Player`；其余（形状、信号强度 15、音效）复用压力板逻辑。
- 资源：blockstates、models、items 双文件、黑曜石质感纹理、lang。

验收：玩家踩上输出满信号；牛/猪/掉落物踩上无信号；配方可合成。

### 5.14 火把箭（服务端 + 资源）

行为：
- 新物品 `quirky:torch_arrow`：可被弓射出、可从发射器射出。
- 命中实体：将其点燃（着火 ~3 秒，数值实施时校准）。
- 命中方块：在命中位置尝试放置火把；放不下（非替换方块/无支撑）时掉落为物品。
- 合成：1 火把 + 1 箭（无序，输出 1）。

实现：
- 物品：`ArrowItem` 子类（createArrow 返回自定义实体）。
- 实体：`TorchArrowEntity extends Arrow`（ModEntities 注册）；覆写 `onHitBlock`（`level.setBlockAndUpdate` 火把 or `spawnAtLocation`）与 `onHitEntity`（`entity.igniteForSeconds(3)`）；基础伤害低于普通箭（如 1.0，实施时校准）；飞行轨迹粒子复用箭。
- 渲染：客户端 `TorchArrowRenderer extends ArrowRenderer`，箭杆 + 箭头处叠加火把 item 渲染（`ItemRenderer`），验证 26.2 渲染 API。
- 资源：items/ + models/item/ + textures/item/（自绘箭+火把图标）、lang。

验收：弓/发射器均可射出；射中僵尸点燃；射中泥土放置火把；射中空气落地处无法放置时掉落火把箭物品；可拾取。

### 5.15 木漏斗（服务端 + 资源）

行为（对齐 Quark 经典行为）：
- 传输速度：铁漏斗的 1/4（每 32 tick 移动 1 个物品，铁为 8 tick）。
- 红石锁定无效：被红石信号激活时**不暂停**传输（与原版铁漏斗相反）。
- 合成：5 木板 + 1 箱子（原版漏斗形状）。
- 木制可作熔炉燃料：物品挂 `minecraft:fuel` 组件，燃烧时长 ~15 秒（300 tick）。

实现：
- `WoodenHopperBlock extends HopperBlock` + `WoodenHopperBlockEntity extends HopperBlockEntity`：`transferCooldown` 基数 32；覆写锁定判断恒为 false。
- 方块实体注册（`BlockEntityType`，ModEntities 或独立 BE 注册）；网络/同步沿用 HopperBlockEntity。
- 资源：blockstates（facing/enabled 变体）、models（木纹漏斗）、items 双文件、纹理、lang。

验收：木漏斗 4 倍慢传输；红石信号下不停止；与箱子/熔炉/漏斗矿车正常交互；破坏掉落内容物。

## 6. 配置项（QuirkyConfig 新增）

toggles 分类新增布尔开关：`soulLighting`、`greenerGrass`、`shulkerTooltip`、`foodTooltip`、`attributeTooltip`、`usageTicker`、`deathCam`、`longPick`、`ladderSnap`、`offhandSwap`、`goldButton`、`ironButton`、`obsidianPlate`、`torchArrow`、`woodenHopper`。

参数分类（按功能分组，附 tooltip）：
- `grassMultiplier`：滑条 0.5~1.5，默认 1.0（Quark 默认增绿强度）；`grassAffectLeaves`：默认 true。
- `tickerHoldTicks`（20~200，默认 50）、`tickerAnimTicks`（2~20，默认 5）。
- `deathCamDuration`（40~100 tick，默认 50）。
- `pickRangeCreative`（16~256，默认 100）、`pickRangeSurvival`（4~64，默认 12）。
- `ladderSnapStrength`（滑条 0.1~1.0，默认 0.5）。

## 7. 数据与资源清单

新物品/方块（每项含 items/ 双文件，对照 bottled_cloud 逐项核对）：
- `gold_button`、`iron_button`、`obsidian_pressure_plate`、`wooden_hopper`：blockstates/ + models/block/ + models/item/ + items/ + textures/block/ + lang。
- `torch_arrow`：items/ + models/item/ + textures/item/ + lang。
- 属性图标：`textures/gui/quirky/attribute/*.png`（6 张 16x16）。
- 灵魂蜡烛火焰：`textures/block/quirky_soul_candle_flame.png`。
- 配方：`data/quirky/recipe/{gold_button,iron_button,obsidian_pressure_plate,torch_arrow,wooden_hopper}.json`。
- lang：en_us.json、zh_cn.json 全部新键（物品名 + tooltip.quirky.* 说明键）。

## 8. 验收标准

- `gradle build`（JDK 25）通过，产出 `build/libs/quirky-<version>.jar`。
- 服务端机制（按钮/压力板/火把箭/木漏斗/副手交换）经 dedicated server 冒烟或逻辑单测验证。
- 客户端机制（tooltip/HUD/渲染/交互）：编译通过 + 代码审查；视觉效果按桌面端手动验证清单（Termux 无 GUI）：
  - 潜影盒/食物/属性 tooltip 显示正确；
  - 使用量挂件滑入滑出动画流畅、护甲耐久停止后收回；
  - 死亡镜头播放与跳过；
  - 灵魂光源四类方块表现正确、破坏还原；
  - 草地增绿滑块效果；
  - 远距拾取、爬梯吸附手感。

## 9. 风险与边界

- 26.2 渲染 API 细节（`GuiGraphicsExtractor` 绘制物品、`BlockModelShaper`、`FlameParticle`、相机覆写）以本地反编译源码为准，实施时先验证再写。
- 灵魂光源的模型替换若在 chunk 重建路径不可行，备选方案：仅粒子 + 火焰面 tint（范围缩小为"火焰青色"而非整灯体灵魂化），spec 不降级验收但实施时二选一并与用户确认。
- 死亡镜头与 26.2 死亡界面/重生流程的时序需实测；镜头期间若出现异常（如被踢出/切换维度）需安全退出状态机。
- 火把箭实体渲染（叠加火把 item）在 26.2 的 API 形态需验证；不可行时退化为纯箭模型 + 命中效果（验收项相应调整）。
- 属性 tooltip 的附魔伤害公式以 26.2 实际公式为准（锋利等），测试用例固定期望值。
- 使用量挂件的快照方案对"同帧多槽变化"（整理）抑制，可能漏报"同帧拾取+消耗"，可接受。
- 客户端视觉效果仍需桌面端手动验证；build 成功不等于手感达标。

## 10. 已确认决策

- 金/铁按钮数值对齐 Quark：金 2 刻、铁 5 秒；合成按用户要求用**粒**（金粒/铁粒），金=木按钮、铁=石质按钮。
- 黑曜石压力板仅玩家触发（删去"大体型动物"扩展），合成 2 黑曜石。
- 火把箭对齐 Quark：命中实体点燃、命中方块放火把（放不下掉落）、发射器可射、合成火把+箭。
- 木漏斗对齐 Quark 经典行为：1/4 速度、红石锁不住；合成 5 木板+箱子。
- 装备替换副手仅支持盾牌与火把。
- 使用量挂件为事件驱动（拾取/消耗/掉耐久才出现），带自下而上滑入滑出动画，耐久停止 3 秒收回。
- 本批次全部为纯客户端或"客户端+轻服务端通知/内容"，不做重服务端机制。

## 11. PENDING：截图模式（Camera Mode）

- 目标行为：F12 进入构图模式（三分法/黄金螺旋网格、黑白/复古滤镜、隐藏 HUD、微调视角）。
- 原因：滤镜/网格渲染管线成本最高，与"实用小功能"定位不搭；高清截图已有 Fabrishot 覆盖。
- 条件：本批次 15 项验收通过后单独 brainstorm/spec 再实施。
