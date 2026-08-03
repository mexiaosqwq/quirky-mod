# 潜影盒内容预览 UI 改版设计

## 背景

当前潜影盒 tooltip 在自带的原版 tooltip 背景内部又绘制了一块不透明紫色面板，并用手动画线绘制 27 个深色槽位。截图中出现的厚重紫色大框、强对比边框和嵌套背景来自这套实现。目标是在保留内容预览功能的前提下，恢复原版容器 UI 的克制观感。

## 已确认的设计

### 视觉

- 继续使用横向 9 列 × 3 行布局，槽位间距为原版容器的 18 px；组件内容区精确为 162×54 px（9×18、3×18），外层 tooltip 的标准边距由 Minecraft tooltip 渲染器负责。
- 不再绘制自定义整块背景；由 Minecraft tooltip 渲染器统一提供标准黑灰半透明背景和边框。
- 每个槽位使用原版 `minecraft:container/slot` GUI sprite，不再使用 `fill`、`horizontalLine`、`verticalLine` 手动画槽位。
- 物品图标从槽位左上角偏移 1 px 绘制，并沿用原版数量装饰绘制。
- 第一版使用原版 sprite 的默认颜色（不传 tint），不按潜影盒染色改变槽位颜色。
- 原版 sprite 技术上支持 ARGB tint，但本次不启用；避免整体染色再次造成紫色/彩色面板感。
- 空槽位仍绘制原版槽位贴图，保证内容预览仍然是明确的背包网格。
- **空盒不渲染 tooltip（2026-08-03 用户确认）：** 空盒无内容时不再显示 9×3 空网格，悬停空盒不出现潜影盒预览；非空盒行为不变。

### 行为

- 普通潜影盒和染色潜影盒均显示相同的原版灰色槽位网格。
- 内容、数量和非潜影盒过滤行为保持不变；**空盒不再显示网格与空状态文案（2026-08-03 用户确认，空盒直接无 tooltip）**。
- 不恢复原版 CONTAINER 文本行，避免文字和网格重复。
- 不新增资源贴图，不改变 tooltip 开关、内容读取或潜影盒过滤行为。由于第一版不再按盒色染色，服务端组件移除仅供该 UI 使用的颜色字段。

## 实现边界

预期修改：

- `src/client/java/dev/quirky/client/tooltips/ClientShulkerTooltipComponent.java`
  - 使用 `Identifier.withDefaultNamespace("container/slot")` 和 `RenderPipelines.GUI_TEXTURED`；
  - 组件 `getWidth` 返回 162、`getHeight` 返回 54，不再增加自绘面板内边距；
  - 每个槽位从组件坐标 `(x + col * 18, y + row * 18)` 开始，物品从槽位坐标偏移 1 px 绘制；
  - 删除自绘背景、槽位填充、边框和颜色混合逻辑。
- `src/main/java/dev/quirky/tooltips/ShulkerTooltipComponent.java`、`TooltipDetailsMixin.java`
  - `ShulkerTooltipComponent` 只保留容器内容；
  - `TooltipDetailsMixin` 保持潜影盒判断和空内容兜底，只删除盒色提取与传递；
  - 保持内容组件和潜影盒判断行为不变。
- `src/test/java/dev/quirky/client/tooltips/ClientShulkerTooltipComponentTest.java`
  - 更新尺寸断言；
  - 验证 27 次原版槽位 sprite 绘制；
  - 验证不再调用自绘填充和线条 API；
  - 用一个带数量的非空物品验证 `graphics.item` 与 `graphics.itemDecorations` 仍按槽位偏移调用；
  - 保留内容组件和空槽位绘制测试；染色盒统一灰色的效果由桌面客户端手动验收，不为已移除的颜色字段伪造单测。

不在本次范围：

- 不修改原版资源文件；
- 不改潜影盒 tooltip 的数据读取、排序或内容容量；
- 不改其他 tooltip（地图、食物、属性）样式；
- 不增加可配置颜色或新的配置项。

## 验收与验证

自动验证：

- 在 worktree 中使用 Java 25 和系统 Gradle 9.6.1 执行 `gradle test --no-daemon --console=plain`；
- 测试覆盖 9×3 尺寸、27 个原版槽位、空槽和物品数量绘制路径。

手动验证：

- 在桌面客户端创造模式中悬停普通潜影盒、至少一种染色潜影盒和空潜影盒；
- 确认 tooltip 只有一层标准黑灰外框，没有紫色大面板；
- 确认槽位与原版容器一致、物品图标/数量不越界；
- 确认普通盒和染色盒的槽位外观一致，颜色不会重新形成彩色面板；
- 确认箱子、熔炉等非潜影盒容器 tooltip 不受影响；
- 记下桌面客户端截图作为最终视觉证据，不能以自动测试通过替代视觉验收。
