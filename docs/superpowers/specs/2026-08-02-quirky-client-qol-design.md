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
| 渲染 tweak | 灵魂光源（仅粒子） |
| 交互 tweak | 远距中键拾取、自动爬梯、装备替换·副手扩展 |
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
- 悬停任意潜影盒（16 色 + 普通）时，tooltip 内显示盒内 9x3 内容物网格（与原版潜影盒 UI 一致，宽 > 高）；空格子留空。
- 内容从物品 `DataComponents.CONTAINER` 读取（实施时以 26.2 反编译源码确认组件路径）；无 CONTAINER 组件的空盒显示空网格。
- **每格画底槽**：盒色暗化（25% 亮度）填充 + 盒色边框（60% 亮度），颜色随 16 色潜影盒外表（`ShulkerBoxBlock.getColor` → `DyeColor`）；普通盒用深灰。一眼可辨是打开的潜影盒 UI。
- **隐藏原版 CONTAINER 文本行**（"包含物品"等）：`ItemStack.addToTooltip` 对 CONTAINER 组件且为潜影盒时取消（ContainerTooltipMixin），避免与网格重复。
- 仅对潜影盒生效（`ItemTags.SHULKER_BOXES`）；箱子/熔炉等其他带 CONTAINER 组件的物品不渲染、文本行保留。
- 仅客户端，服务端无需改动。

实现：
- `Item.getTooltipImage` mixin（MapTooltipMixin 模式）：潜影盒返回 `ShulkerTooltipComponent(contents, color)`（颜色随盒）。
- 客户端 `ClientShulkerTooltipComponent implements ClientTooltipComponent`：`extractImage` 内先画底槽（fill + 边框线）再逐格绘制物品图标与数量（`GuiGraphicsExtractor` 的物品绘制 API 以本地源码为准），宽高按 9x3 网格计算（每格 16px + 内边距）。
- `QuirkyModClient` 的 `ClientTooltipComponentCallback` 注册转换。

验收：悬停潜影盒出现 9x3 内容预览；空格无图标；带物品数量显示；箱子/熔炉等非潜影盒容器 tooltip 不变。

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
- **紧凑横条布局（对齐 Quark AttributeTooltips）**：全部属性排成单行横排 `[9x9 图标][数值]`（16x16 原稿最近邻缩放 9x9，逐图像素网格验证可辨），与食物行共用 {@code TooltipRowMetrics} 度量（16px 行高、垂直居中、图标-文本间距 2、单元格间距 4），不做槽位分组（26.2 原版物品单组即可容纳）。
- **未按 Shift 时隐藏原版 "Attribute Modifiers" 竖排文本段**（`ItemStack.addAttributeTooltips` HEAD 取消，`AttributeTextHideMixin`，客户端 mixin），由横条替代；按住 Shift 时隐藏横条、放行原版文本（对照查看，与 Quark removeAttributeTooltips 一致）。隐藏条件与横条显示条件互斥等价（`AttributeTooltipVisibility`）：横条无替代内容（仅携带非 6 类修饰符的物品，如 max_health/luck）时不隐藏原版段，避免属性信息静默丢失；创造/配方搜索索引路径（`SessionSearchTrees` 传 null player）放行，属性文本保留可搜索。

实现：
- 解析 `DataComponents.ATTRIBUTE_MODIFIERS`（按槽位过滤主手/护甲槽修饰符）+ 物品基础属性（`Item.getDefaultAttributeModifiers`）。
- 图标为自绘 16x16 sprite：`textures/gui/sprites/attribute/{attack_damage,attack_speed,armor,toughness,knockback,movement}.png`（26.2 GUI atlas 只扫描 `textures/gui/sprites/` 目录，sprite id 对应 `quirky:attribute/xxx`，不带 `gui/` 前缀）。
- 渲染：自定义 `AttributeTooltipComponent`（图标 + 数值文本横排单元格）；Shift 判定用 `Minecraft.getInstance().hasShiftDown()`（与 Quark Screen.hasShiftDown 等价）。

验收：钻石剑显示横排 7 攻击伤害 + 1.6 攻速（一行内）；锋利 V 显示 9.5（7+2.5，公式以实测为准）；钻石胸甲显示横排 8 护甲 + 2 韧性；未按 Shift 时无原版属性文本段；Shift 显示原版文本、隐藏横条。

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
- 玩家死亡瞬间不直接进死亡界面：**基岩版式平滑过渡**——镜头从玩家身后贴脸位置（半径 0.8 格、眼睛高度 1.6、水平视角）开始，前 25% 时间快速拉出到 2.5 格（第一人称 → 第三人称的流畅感），随后缓慢拉远到 6 格展示位（高度 1.6→3.0）（约 2.5 秒，config 可调 2~5s），展示尸体/掉落物/凶手方位后，进入原版死亡界面。
- **镜头朝向保持玩家死亡时的 yaw 不变，不环绕旋转**（用户验收：2.5s 转 360° 是"雷霆运镜"感；基岩版为拉出定格展示）。
- 镜头播放期间屏蔽输入（不响应 WASD/视角）；可按 Esc 提前跳过。
- 纯视觉：不影响服务端死亡流程、不掉落、不复活。

实现：
- 服务端：`ServerPlayer.die` 处（mixin，MelonSeedMixin 同文件域）发送 `DeathCamPayload`（死亡位置、维度、朝向）。
- 客户端：收到 payload 后启动镜头状态机：
  - mixin `Camera.alignWithEntity`（26.2 实际注入点）：镜头播放期间用插值位置/旋转覆盖相机（DeathCamTimeline：起始半径 0.8、眼睛高度 1.6、俯角 0°；前 25% 拉出到 2.5，后段缓慢到 6；yaw 恒定；pitch 0°→25°）；
  - `ClientPacketListener.handlePlayerCombatKill` 拦截死亡界面 setScreen，镜头结束/Esc 后打开；
  - `DeathCamSkipMixin`/`GuiDeathScreenDelayMixin`：Esc 跳过与死亡界面补开兜底。

验收：死亡后先播放 ~2.5s 平滑拉出镜头（无视角跳变、无环绕旋转）再出现死亡界面；镜头内掉落物可见；Esc 可跳过；创造/旁观不触发。

### 5.6 灵魂光源（客户端渲染）

行为：
- 火把（含墙上火把）、蜡烛放置在**灵魂沙/灵魂土正上方**（下表面接触）时，**火焰粒子**呈灵魂青色（soul_fire_flame）。灯笼因无原版火焰粒子、且 26.2 模型贴图替换受区块编译缓存限制无法实现，**不在覆盖面内**（见下“已知限制”）。
- 破坏/移动后恢复原样（粒子生成时按下方方块动态判定，无状态存储）。

实现（26.2 实测）：
- 粒子：mixin 火焰粒子创建点（`FlameParticle` 7 参构造器），按所在方块正下方判定替换为 `soul_fire_flame` sprite。
- config：`soulLighting` 开关。

已知限制（已放弃）：
- **方块模型/贴图替换未实现**。26.2 渲染架构为 SectionCompiler 区块网格静态编译，
  `SectionCompiler.compile` 的 tesselateBlock 调用点虽可拿到位置并替换模型，但实测
  放置灵魂方块后上方光源所在 section 的重编译依赖脏区标记与 SectionCopy 缓存快照，
  无法可靠地按邻居方块动态换贴图（粒子走 ClientLevel 实时数据可生效，区块编译走
  SectionCopy 快照不可靠）。模型替换相关代码（SectionCompilerMixin/SoulCandleModel/灵魂蜡烛资源）已删除。

验收：火把插在灵魂沙上火焰粒子青色；敲掉后恢复橙色；方块本体贴图保持原版（不替换）。

### 5.7 草地增绿（客户端）——已移除

> 2026-08-02 用户验收：功能无效，放弃排查直接移除（注册链路静态验证正确但运行时未生效，
> 无日志可定位）。已删除 GreenerGrassClient/GrassColorMatrix 及 config 字段
> （greenerGrass/grassMultiplier/grassAffectLeaves）与 lang 键。如需恢复可从 git 历史找回。

### 5.8 远距中键拾取（客户端）

行为：
- 中键拾取（pick block）距离扩展：创造默认 100 格、生存默认 12 格，config 可调（创造 16~256、生存 4~64）。
- 其余拾取行为与 26.2 原版一致（创造取方块/生存切同种物品）。

实现：
- mixin `Minecraft.pickBlock`（或原版拾取射线入口）：启用时用扩展距离对 `level.clip` 重新射线，命中结果进入原版拾取逻辑。
- config：`pickRangeCreative`、`pickRangeSurvival`。

验收：创造模式中键可拾取 100 格外的方块；生存模式中键可拾取 12 格外背包已有的同种方块；config 生效。

### 5.9 自动爬梯（客户端）

行为（用户定制：基岩版式自动爬梯，无居中吸附）：
- 玩家在**梯子/全部藤蔓**（onClimbable() = #minecraft:climbable ∪ 梯上同向开放活板门，实际覆盖另含梯上活板门）上时，**抬头（pitch < -15°）自动上升、低头（pitch > 15°）自动下降、平视缓慢下滑（不自动爬）**——爬梯只需抬头。
- 按 W/S/空格/Shift 时手动优先，不干预；**仅排除脚手架**——原版脚手架自带空格升/Shift 降/自由走动，抬头自动爬反而会在脚手架塔里莫名上升；藤蔓无原生快爬，自动爬收益最大（用户复核：藤蔓必须覆盖，覆盖判定 `LadderSnapHelper.isExcluded` 单测锁定）。

实现：
- mixin `Player.travel` HEAD（目标类 Player——LocalPlayer 未覆写 travel，26.2 mixin 的 method 选择器只匹配本类方法）：`LadderSnapHelper.climbVelocity(pitch, manual)`（抬头 0.2（净 ≈0.116 b/t）/平视 0.05（缓慢下滑不爬）/低头 −0.15，经重力与竖直摩擦后净上升 ≈0.116 b/t 对齐原版 W 爬梯；travel 内 `handleOnClimbable` 保留 delta.y）。
- config：`ladderSnap` 开关（语义 = 自动爬梯）。

验收：梯子/藤蔓上抬头自动上升、低头下降、平视缓慢下滑不爬；按 W/S 仍可手动爬；脚手架上不自动爬（自由走动/空格升/Shift 降不受干预）。

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
- 边界（review S4）：空覆写 `setBaseDamageFromMob`——生物（如骷髅）与发射器射出的火把箭伤害不随拉弓力量缩放，恒为基础伤害 1.0，属设计选择，勿改。
- 渲染：客户端 `TorchArrowRenderer extends ArrowRenderer`，箭杆 + 箭头处叠加火把 item 渲染（`ItemRenderer`），验证 26.2 渲染 API。
- 资源：items/ + models/item/ + textures/item/（自绘箭+火把图标）、lang。

验收：弓/发射器均可射出；射中僵尸点燃；射中泥土放置火把；射中空气落地处无法放置时掉落火把箭物品；可拾取。

### 5.15 木漏斗（服务端 + 资源）

行为（对齐 Quark 经典行为）：
- 传输速度：铁漏斗的 1/4（每 32 tick 移动 1 个物品，铁为 8 tick）。
- 红石锁定无效：被红石信号激活时**不暂停**传输（与原版铁漏斗相反）。
- 朝向下方且下方为空气、无容器可吐时：把物品从漏斗口漏出为掉落物（原版铁漏斗无此行为，物品会永远积在漏斗里）。仅朝下生效，且受 `block_drops` 游戏规则约束；下方为实心方块或容器满时不漏出。
- 合成：5 木板 + 1 箱子（原版漏斗形状）。
- 木制可作熔炉燃料：物品挂 `minecraft:fuel` 组件，燃烧时长 ~15 秒（300 tick）。

实现：
- `WoodenHopperBlock extends HopperBlock` + `WoodenHopperBlockEntity extends HopperBlockEntity`：`transferCooldown` 基数 32；覆写锁定判断恒为 false。
- 方块实体注册（`BlockEntityType`，ModEntities 或独立 BE 注册）；网络/同步沿用 HopperBlockEntity。
- 资源：blockstates（facing/enabled 变体）、models（木纹漏斗）、items 双文件、纹理、lang。

验收：木漏斗 4 倍慢传输；红石信号下不停止；与箱子/熔炉/漏斗矿车正常交互；破坏掉落内容物；朝下悬空时物品漏出为掉落物。

## 6. 配置项（QuirkyConfig 新增）

toggles 分类新增布尔开关：`soulLighting`、`shulkerTooltip`、`foodTooltip`、`attributeTooltip`、`usageTicker`、`deathCam`、`longPick`、`ladderSnap`、`offhandSwap`、`goldButton`、`ironButton`、`obsidianPlate`、`torchArrow`、`woodenHopper`。

参数分类（按功能分组，附 tooltip）：
- `tickerHoldTicks`（20~200，默认 50）、`tickerAnimTicks`（2~20，默认 5）。
- `deathCamDuration`（40~100 tick，默认 50）。
- `pickRangeCreative`（16~256，默认 100）、`pickRangeSurvival`（4~64，默认 12）。

## 7. 数据与资源清单

新物品/方块（每项含 items/ 双文件，对照 bottled_cloud 逐项核对）：
- `gold_button`、`iron_button`、`obsidian_pressure_plate`、`wooden_hopper`：blockstates/ + models/block/ + models/item/ + items/ + textures/block/ + lang。
- `torch_arrow`：items/ + models/item/ + textures/item/ + lang。
- 属性图标：`textures/gui/sprites/attribute/*.png`（6 张 16x16）。
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
  - 远距拾取、自动爬梯手感。

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
