# 播种袋重新设计（Seed Pouch v2）

日期：2026-08-04
状态：待用户审阅
前置：替代 `2026-08-03-batch-b-farm-fish-design.md` §1 播种袋章节（v1）
动机：v1 种子来源是"遍历背包取任何能存活的 BlockItem"，导致灯笼/火把等可放置方块被误当种子种下（用户实机发现"播种袋掉落灯笼"）。v2 改为收纳袋式自带容器，只装真种子，从根上修复。

## 1. 定位

收纳袋式种子容器 + 批量播种农具。袋子自带存储（原版 `BundleContents` 组件），玩家把种子放进袋子，右键耕地时从袋内取种批量播撒。与收割补种机制呼应：收割管"收"，播种袋管"种"。

**与 v1 的核心差异**：种子来源从"背包"改为"袋子自带容器"，且袋子只接受真种子（作物方块白名单），从而修掉 v1 误把灯笼/火把当种子的 bug。

## 2. 物品形态

- `quirky:seed_pouch`：堆叠 1，无耐久，无附魔（沿用 v1）。
- 存储：原版 `DataComponents.BUNDLE_CONTENTS`（`BundleContents` 组件，26.2 mcsrc `BundleContents.java:26` 已验证）。
  - **重量模型**（原版语义）：每个物品占 `1/maxStackSize` 重量，总重量 ≤ 1 → 满袋约 64 个 maxStack64 种子。可混装多种种子，按重量分配。
  - 自带 `BundleTooltip` 网格渲染（mcsrc `BundleItem.getTooltipImage` 已验证）、装满度条（`isBarVisible`/`getBarWidth`/`getBarColor` 基于重量）、`selectedItem` 选中格。
- 合成：皮革 + 线×2 + 小麦种子（无序），沿用 v1 不变。
- 贴图：沿用 v1 棕色小布袋贴图，不重做。

## 3. 种地（核心用途）

**右键耕地**（`Item.useOn(UseOnContext)`，世界交互，沿用 v1）：

1. 配置 `seedPouchEnabled` 关闭 → PASS。
2. 半径：潜行右键 = 0（单格精准），否则 `seedPouchRadius`（默认 1 即 3×3）。
3. 扫描候选格（上方空气），逐格从**袋内**按 `BundleContents.items()` 列表顺序找第一个满足条件的种子：
   - 种子判定：`item instanceof BlockItem` 且其方块 `defaultBlockState` 在目标格上方 `canSurvive`（含光照+基质检查，v1 已验证 mcsrc `CropBlock.java:151`）。**来源是袋子，袋子只装真种子（见 §5 放入过滤），故灯笼等不会进入袋子，bug 根治。**
   - canSurvive 泛化保留：甘蔗（沙+水旁）、仙人掌（沙）、竹子、地狱疣（灵魂沙）按各自规则自动兼容。
4. 客户端只做只读预测（返回 SUCCESS 触发挥臂），世界修改与袋子消耗由服务端权威执行（沿用 v1 双端语义）。
5. 服务端：`setBlock(cropPos, cropState, 3)` 种下 + `sendParticles(HAPPY_VILLAGER)` + 从袋内移除该种子 1 个（`BundleContents.Mutable` 重建列表：对应 entry 减 1，归零则移除 entry，写回 `BUNDLE_CONTENTS`）。
6. 音效：`CROP_PLANTED`（地狱疣用 `NETHER_WART_PLANTED`），音高随数量微调（沿用 v1 `HarvestFx` 模式）。
7. 袋子空 / 无可种位置 → FAIL（不挥臂不播音，原版手感，沿用 v1）。
8. 创造模式：种地不消耗袋内种子（`hasInfiniteMaterials()` 守卫，沿用 v1）。

**取种优先级**：袋内列表顺序 = 玩家可控优先级（先放进来的在前）。同格多种子能存活时，列表中靠前的优先。玩家可通过取出重放调整顺序。

## 4. 容器交互（物品栏内，无 GUI，原版 Bundle 风格）

全部走原版 `BundleItem` 的两个 override API（mcsrc `BundleItem.java:59,97` 已验证）。**用原版左键/右键区分方向，不用 Shift**——原版语义天然区分放入/取出/收纳，比自定义 Shift 更原汁原味且实现更简单（原版 `tryInsert`/`tryTransfer`/`removeOne` 全可复用）。

| 操作 | API | ClickAction | 方向 | 行为 |
|---|---|---|---|---|
| 光标拿**种子**左键点**袋子** | `overrideOtherStackedOnMe` | PRIMARY | 格子→袋子（放入） | `tryInsert`：整摞吸入，受重量上限限，放不下留光标；**前置过滤：只接受作物方块白名单（见 §5），非种子拒绝** |
| 光标拿**袋子**左键点**种子堆** | `overrideStackedOnOther` | PRIMARY | 格子→袋子（收纳） | `tryTransfer(slot)`：把该 slot 的种子吸入袋子（原版语义，同样过白名单） |
| 光标拿**袋子**右键点**格子** | `overrideStackedOnOther` | SECONDARY | 袋子→格子（取出） | **自定义**（原版只处理空 slot，我们扩展）：空格→放下袋内第一项一组；已有同种→补充到满；异种→跳过不覆盖 |
| 光标拿**空手**右键点**袋子** | `overrideOtherStackedOnMe` | SECONDARY | 袋子→光标（取出） | 原版 `removeOne`：取袋内第一项一组到光标 |

音效：放入/收纳用原版 `BUNDLE_INSERT`（或项目皮革音，实现时定）；取出用 `BUNDLE_REMOVE_ONE`；失败（满袋/非种子/异种跳过）无声或低沉提示，实现时定。

## 5. 种子白名单（放入/收纳过滤，修 bug 关键）

袋子是种子袋，**放入与收纳时只接受真种子**，拒绝灯笼/火把/脚手架等可放置非种子方块。判定：

```
item instanceof BlockItem blockItem
  && blockItem.getBlock() instanceof CropBlock | NetherWartBlock | SugarCaneBlock
       | CactusBlock | BambooStalkBlock | StemBlock | SweetBerryBushBlock | PitcherCropBlock
```

- 26.2 mcsrc 已验证上述 8 个作物方块类均存在（`CropBlock/NetherWartBlock/SugarCaneBlock/CactusBlock/BambooStalkBlock/StemBlock/SweetBerryBushBlock/PitcherCropBlock`）。
- 模组作物多继承 `CropBlock`，自动兼容（保留 v1 的模组兼容意图）。
- 胡萝卜/马铃薯以作物本身为种子（`Blocks.CARROTS/POTATOES` 是 `CropBlock`），兼容。
- `BambooStalkBlock` 对应 `Blocks.BAMBOO`（mcsrc `Blocks.java:4105` 已验证 `BAMBOO = register(..., BambooStalkBlock::new, ...)`）。

**种地时不再重复白名单判定**——袋子 guaranteed 只装白名单种子，种地只需 `canSurvive` 匹配基质。这样既根治误种，又保留 `canSurvive` 泛化的基质兼容（甘蔗需水旁等）。

## 6. 边界场景清单

| 场景 | 行为 |
|---|---|
| 袋子空 + 右键耕地 | FAIL，不挥臂不播音（沿用 v1） |
| 袋子只够种 2/9 格 | 种能种的，剩余跳过，不报错 |
| 混合种子（小麦+地狱疣）对耕地+灵魂沙混合区 | canSurvive 自然分流：耕地格匹配小麦，灵魂沙格匹配地狱疣 |
| 袋子装入非种子（灯笼） | §5 白名单拒绝，放不进 |
| 满袋放入 | 重量上限拒，留光标 |
| 创造模式种地 | 不消耗袋内种子 |
| 潜行右键 | 单格精准模式（沿用 v1） |
| 副手持播种袋 | 沿用原版 useOn 语义（v1 已验证） |
| 袋子被摧毁（onDestroyed） | 原版 `ItemUtils.onContainerDestroyed` 散落内容（mcsrc `BundleItem.onDestroyed` 已验证） |
| 与收割机制交互 | 收割原地单格补种；播种袋区域新种，互不冲突（沿用 v1） |
| 灯笼/火把等 BlockItem | 放不进袋子（白名单），也不会被种下——**bug 修复** |

## 7. 配置

沿用 v1，无新增字段：
- `seedPouchEnabled`（bool，默认 true）
- `seedPouchRadius`（int，0-2，默认 1）

## 8. 实现要点与风险

- **存储组件**：直接用原版 `DataComponents.BUNDLE_CONTENTS`（`BundleContents`），不自建组件。tooltip/装满度条/selected 全部复用原版，零额外渲染代码。
- **种地消耗**：`BundleContents.Mutable` 无"减指定 index 1 个"API，需手动重建 items 列表（拷贝 → 对应 entry `shrink(1)`/移除 → `new BundleContents(list)` 写回）。纯逻辑抽到 `SeedPouchPlanter` 便于单测。
- **放入过滤**：`overrideOtherStackedOnMe` PRIMARY 分支在 `tryInsert` 前加白名单判定；`overrideStackedOnOther` PRIMARY（收纳）的 `tryTransfer` 同样需限定白名单（原版 `tryTransfer` 不过滤物品类型）。
- **取出扩展**：`overrideStackedOnOther` SECONDARY 自定义——原版只处理空 slot，我们扩展"同种补充/异种跳过"。用 `slot.safeInsert` + 余额退回袋子。
- **纯交互物品，无 mixin**（沿用 v1）。
- **26.2 API 实测要求**：`BundleContents`/`BundleItem` 的 override 签名、`Mutable` API、`BundleTooltip` 注册路径，实现前用 javap/mcsrc 逐项核对（过 `quirky-mixin-runtime-audit` 清单虽然无 mixin，但 API 语义仍需实测）。

## 9. 验证

- **单测**（`SeedPouchPlanter` 纯逻辑改来源）：
  - 袋内多种子混合、逐格匹配能存活、不足、空袋。
  - 白名单过滤（灯笼放不进）。
  - 取出（空格放下/同种补充/异种跳过）纯逻辑。
- **手动**（桌面客户端）：
  - 装种子/取种子/收纳交互手感。
  - 3×3 播种、混合种子、创造模式、与收割连用。
  - **回归**：灯笼放不进袋子、种地不再掉灯笼。

## 10. 与 v1 的差异总结

| 维度 | v1 | v2 |
|---|---|---|
| 种子来源 | 遍历背包 | 袋子自带容器（BundleContents） |
| 种子判定 | `BlockItem + canSurvive`（过宽，误纳灯笼） | 放入时白名单过滤 + 种地 canSurvive |
| 容量 | 背包无限 | 重量模型，满袋 ~64 |
| 容器交互 | 无（直接抓背包） | 原版 Bundle override 三件套 |
| tooltip | 文字提示行 | 原版 Bundle 网格 + 装满度条 |
| 灯笼 bug | 存在 | **修复**（白名单 + 来源改袋子） |
