# Quirky 细节补全设计文档

- 日期：2026-08-01
- 状态：待用户审阅
- 目标环境：Minecraft 26.2 / Fabric / Java 25
- 依赖：Fabric API `0.155.2+26.2`，不引入其他模组 API
- 关联：`docs/superpowers/specs/2026-08-01-quirky-design.md`（v1 机制总规格）

## 1. 目标

在 v1 机制基础上修复已反馈的问题，并补齐“手感细节”：音效、粒子/挥臂、物品抛掷/拾取延迟、原版地图纸边等。逻辑正确不再视为完成，细节必须写进规格与验收。

## 2. 修复范围

### 2.1 云瓶

行为：

- 使用云瓶获得 20 秒缓慢下落；实际消耗时返还玻璃瓶，创造模式不消耗、不返还。
- 使用云瓶时播放 `minecraft:item.bottle.empty`，音源为玩家，服务端广播到附近玩家。
- 物品必须显示真实贴图，不允许紫黑缺失模型。
- 重画 16x16 贴图，使玻璃瓶轮廓、白色高光与淡蓝云朵清晰可辨。

实施方式：

- 在 `BottledCloudItem.use` 服务端分支调用 `player.playSound(SoundEvents.BOTTLE_EMPTY, 1.0F, 1.0F)`。
- 验收时检查 `build/libs/quirky-0.1.0.jar` 内 `assets/quirky/models/item/bottled_cloud.json` 与 `assets/quirky/textures/item/bottled_cloud.png`；若游戏仍显示紫黑，必须修复资源路径或替换贴图，不能以“jar 里有文件”作为完成依据。

### 2.2 双开门

行为：

- 玩家手动开关一扇可手开门时，相邻匹配门同步开关。
- 村民、僵尸等实体通过 `DoorBlock.setOpen` 开关门时，相邻匹配门同步开关，解决“只开一扇”的问题。
- 风弹爆炸等调用 `setOpen` 的路径同样生效。
- 铁门等不可手开门不联动；红石直接改方块状态的路径保持原版行为。

实施方式：

- 在 `DoorBlock.setOpen` 尾部注入同步逻辑。
- 玩家点击路径在 `DoorBlock.useWithoutItem` 开头预同步，避免 `useWithoutItem` 直接改状态时漏掉 `setOpen`。
- `DoubleDoorHandler` 增加重入保护（如同一线程正在同步则直接返回），防止 A 门同步 B 门、B 门又同步 A 门的死循环。
- 归一化到下半块，按 `FACING` 与 `HINGE` 计算伙伴门。

### 2.3 吃西瓜吐籽

行为：

- 玩家完整吃完 `minecraft:melon_slice` 后，不再尝试放入背包，而是生成 1 个 `minecraft:melon_seeds` 物品实体。
- 物品实体从玩家面向方向轻轻抛出，带约 0.3 倍视线方向速度。
- 物品实体有 40 tick 拾取延迟，记录投掷者为玩家。
- 生成时播放 `minecraft:entity.fox.spit`。
- 创造模式不吐籽，避免无限复制。

实施方式：

- `MelonSeedHandler` 使用 `new ItemEntity(level, x, y, z, seed)`，调用 `setPickUpDelay(40)`、`setThrower(player)` 与 `setDeltaMovement(look.scale(0.3))`。
- 生成位置取玩家眼睛附近，并沿 `player.getLookAngle()` 偏移。
- 保留 `!player.hasInfiniteMaterials()` 检查。

### 2.4 地图 tooltip 纸边

行为：

- 地图悬浮预览使用原版 `textures/map/map_background.png` 作为底图，地图内容绘制在其上。
- 预览整体尺寸由 64x64 调整为 71x71：四周保留原版肉色地图纸边，而不是用纯色块填充。

实施方式：

- `ClientMapTooltipComponent` 中先用 `GuiGraphicsExtractor.blit(RenderPipelines.GUI_TEXTURED, ...)` 绘制 `textures/map/map_background.png`（142x142 源区域），再按 0.5 缩放绘制 `MapRenderState`。
- `getWidth` / `getHeight` 改为 `142 * 0.5 = 71`。

## 3. 音效与反馈总表

| 机制 | 音效 | 可见/物理反馈 |
|---|---|---|
| 云瓶 | `item.bottle.empty` | 无额外动画 |
| 收割/补种 | 方块破坏音；补种成功播 `item.crop.plant`，下界疣播 `item.nether_wart.plant` | `levelEvent(2001)` 破坏粒子 + 点击者挥臂 |
| 西瓜/南瓜/可可豆收割 | 对应果实/可可豆破坏音 | 同上 |
| 吃西瓜吐籽 | `entity.fox.spit` | 物品实体抛出 + 40 tick 拾取延迟 |
| 双开门 | 原版门音效 | 原版开关 |
| 背包换装 | 原版装备音效 | 原版装备动画 |
| 地图/指南针/时钟 | 不加音效 | 地图纸边 |

## 4. 收割粒子与动画验收

- 作物、下界疣、西瓜/南瓜、可可豆四条收割路径都必须先播放破坏粒子，再替换或移除方块。
- 必须给点击收割的玩家发送挥臂动画。
- 验收以实际游戏可见为准；若 `levelEvent(2001)` 在某个客户端路径不显示，实施时改用更直接的粒子方案，不得以“代码已调用”作为完成标准。

## 5. 测试与验收标准

- 单测覆盖：
  - 云瓶使用播放 `BOTTLE_EMPTY`。
  - 吃西瓜生成种子物品实体，且不调用 `Inventory.add`；实体有 40 tick 拾取延迟与投掷者。
  - 非玩家实体调用 `DoubleDoorHandler` 时伙伴门被同步；重入时不递归。
  - 地图 tooltip 尺寸为 71x71，且绘制地图纸边资源。
- 客户端地图纸边与云瓶贴图需要桌面客户端视觉验证；本机 Termux 无 GUI，以构建 + 代码审查 + 桌面验证清单为准。
- 每次任务结束执行：

```bash
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle clean build --no-daemon --console=plain
```

## 6. 非目标

- 不做配置界面。
- 不做保留图腾。
- 不给红石信号直改状态的门做联动。
- 不给地图、指南针、时钟加音效。
- 不做自定义玩家吐籽动画（26.2 无原版玩家吐籽动画，仅做抛掷与音效）。
