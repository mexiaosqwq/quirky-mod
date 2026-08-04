# Blockbench 建模流水线 + 自定义实体基础（Modeling Pipeline v1）

日期：2026-08-04
状态：待用户审阅
前置：无（全新基建，Quirky 现有实体均为无模型功能性实体，`ModelLayers`/`EntityModel`/`LayerDefinition`/`AnimationDefinition` 零使用）
动机：**目标是把几条能走通的线路搭起来，以后方便复用**——① 建模线路（Blockbench 网页版 + Playwright，截图验证）；② 转换线路（.bbmodel → 26.2 Java 模型/动画代码）；③ 接入线路（自定义实体注册/渲染/动画范式）。演示实体 `demo_beast` 只做线路验证载体（最小集：能刷出来、能动、有动画），不做多余扩展。

## 1. 定位

一套跑在 Termux（手机端）上的建模系统 + 一个演示实体：

- **建模系统**：Blockbench 网页版（最新版）跑在 Termux 原生 chromium-browser 149（已装，headless，SwiftShader 软件 WebGL2 已验证可用），由 Playwright（node）驱动调用 Blockbench 全局 API 完成建模/骨骼/关键帧/截图/导出。
- **演示实体**：`quirky:demo_beast`（四足小兽），完整接入 Quirky（注册/属性/生成/AI/模型/动画/渲染/掉落），作为自定义实体的模板。
- **不采用**：jasonjgardner/blockbench-mcp-plugin（`variant:"desktop"`，网页版装不了，已验源码）；proot 跑 Electron 桌面版（无官方 arm64 Linux 构建，仅 Pi-Apps 旧版 4.9.3，已验）；纯手写 Java 模型（无视觉反馈，UV 易错，作为兜底备选）。

## 2. 系统架构

```
建模层   Blockbench 网页版 (web.blockbench.net)
         └─ Termux chromium-browser 149 (headless + --enable-unsafe-swiftshader)
              ▲
              │ Playwright (node) page.evaluate 调 Blockbench 全局 API
工具层   build/tools/blockbench/
         ├─ launch.js   启动/连接 Blockbench 页面（含 WebGL 探测、下载目录）
         ├─ model.js    建项目/骨骼(Group)/立方体(Cube)/UV/贴图
         ├─ anim.js     动画创建（关键帧 position/rotation/scale + 插值）
         ├─ shot.js     视口截图 → build/previews/（视觉验证，先示意后落地）
         └─ export.js   导出 .bbmodel → 解析转 Java 代码
              │
26.2 转换层 .bbmodel JSON → EntityModel/LayerDefinition + AnimationDefinition
              │
接入层    src/main/java/dev/quirky/demobeast/ + src/client/java/dev/quirky/client/demobeast/
         实体类/属性/AI/渲染器/模型/动画/生成蛋
```

- Blockbench 全局 API 依据：MCP 插件源码（jasonjgardner/blockbench-mcp-plugin 的 `server/tools/*.ts` 演示了全部调用方式：`Project`/`Group`/`Cube`/`Animation`/`BoneAnimator`/`Undo`/`Canvas.updateAll()`），可直接照搬其调用模式。
- 若在线 `web.blockbench.net` 加载不稳定 → 降级：git clone Blockbench 源码本地 `npm run dev`（vite dev server），API 相同。

## 3. 建模流水线（工具层）

流程：`launch` → `model`（建骨架/立方体/UV/贴图）→ 每步 `shot` 截图到 `build/previews/` 供用户确认 → `anim`（走路/待机/摇尾动画）→ 确认后 `export`。

- 截图即视觉验证：符合项目"视觉/贴图类改动必须先示意后落地"纪律，模型每阶段截图给用户确认后才进正式资源。
- 纹理：Blockbench 内程序生成 64×64 底纹（或由我按像素规则生成 png），先出预览再落地。
- 导出产物：`.bbmodel`（存档）+ 解析出的模型/动画 JSON 结构（转 Java 的中间态）。

## 4. 26.2 转换层（bbmodel → Java，mcsrc 已验证 API 存在）

| 产出 | 26.2 API（`$HOME/.cache/mcsrc` 已验证存在） |
|---|---|
| 模型 | `net.minecraft.client.model.EntityModel` + `ModelPart` + `LayerDefinition`（`client/model/geom/builders/`，`CubeListBuilder.addBox`） |
| 注册 | `ModelLayers` 注册自定义 layer（`client/model/geom/ModelLayers.java`） |
| 动画 | `net.minecraft.client.animation.AnimationDefinition` + `KeyframeAnimations.animate` + `AnimationChannel`/`Keyframe`（`client/animation/`，`Keyframe` 在 `net.minecraft.util`） |
| 渲染 | `MobRenderer` + `LivingEntityRenderState`（26.2 RenderState 模式，`client/renderer/entity/state/`） |

- 动画时长换算：bbmodel 动画按秒/帧存储，转 Java 时 `Keyframe` 时间单位为秒（`KeyframeAnimations` 语义以 mcsrc 为准，实现时逐项对照）。
- 所有引用的类/方法签名在实现计划阶段用 codegraph + mcsrc 逐项验证，不凭记忆写。

## 5. 演示实体：quirky:demo_beast（四足小兽）

### 5.1 模型结构（约 10 cube，64×64 纹理）

```
root
├─ body（身体，居中，高 8）
├─ head（头，前上方，含 2 耳朵）
├─ leg_front_left / leg_front_right / leg_back_left / leg_back_right（4 腿）
└─ tail（尾巴，可摇）
```

### 5.2 动画（3 条）

| 动画 | 内容 | 循环 |
|---|---|---|
| walk | 四腿交替摆动（对角步态）+ 身体微俯仰 | 是 |
| idle | 待机呼吸（身体/头微起伏） | 是 |
| tail_wag | 尾巴左右摇 | 是 |

- 动画触发：walk 在移动时播（`animationSpeed` 驱动，参照原版动物模式），idle 静止播，tail_wag 常驻叠加。具体驱动方式实现时对照 mcsrc 原版动物（如 `ChickenModel`/`WolfModel`）确定。

### 5.3 实体接入清单（最小集，仅够验证线路）

- 服务端：`ModEntities` 注册 `EntityType`（沿用现有 `ResourceKey` + `EntityType.Builder` 模式）；实体类 `extends Animal` + 属性注册（移动速度/生命）+ 最简 AI（`WanderAroundGoal` 等，26.2 goal 类名以 mcsrc 为准）。不做繁殖/掉落/复杂行为——这些以后需要时沿同一条线路扩展。
- 客户端：`ModelLayers` 注册 + `EntityModel`/`LayerDefinition` + 动画定义 + `EntityRenderers.register` + 纹理 `assets/quirky/textures/entity/demo_beast/*.png`。
- 生成蛋：`SpawnEggItem`（26.2 构造/属性方式以 mcsrc 为准）+ 合成配方。
- 实体所有行为双端语义：服务端权威，客户端只读预测（沿用项目既有实体模式）。

## 6. 边界场景清单

| 场景 | 行为 |
|---|---|
| 网页版加载失败/网络不稳 | 降级本地 dev server（§2） |
| WebGL 不可用 | launch 时探测，报错并提示 `--enable-unsafe-swiftshader`（本机已验证可用） |
| 动画时间轴/插值与 Java 导出不一致 | 以 .bbmodel 数据为准转 Keyframe，插值类型映射（linear/smooth/step/bezier→ 对应 Keyframe.Interpolation）实现时按 mcsrc 对照 |
| 模型 UV 超出纹理范围 | 转换层校验 box UV 坐标，报错定位 |
| 实体生成/动画播放异常 | 游戏内 F3 调试 + 逐项对照 mcsrc 原版动物 |
| 满背包/生成限制 | 生成蛋原版语义，不额外处理 |

## 7. 验证方式（分阶段）

1. **建模流水线**：Playwright 脚本跑通"建骨架→加 cube→截图"，截图出现在 `build/previews/`，用户确认模型形态。
2. **动画**：动画文件生成 + 截图（不同时间点）确认动作合理性。
3. **转换层**：`.bbmodel` → Java 模型/动画代码生成，`gradle build` 通过。
4. **实体接入**：完整接入后 `gradle build` 通过 + 单测（若有可测逻辑）。
5. **游戏内验收（用户执行）**：生成蛋刷出 demo_beast → 走动时有 walk 动画 → 静止时 idle → 尾巴摇动。对照 §1 验收标准"能刷出来、能动、有动画"。

## 9. 线路复用指南（以后加新动物的走法）

1. 建模：`build/tools/blockbench/` 脚本建新项目/模型 → `shot` 截图确认 → `export` 出 `.bbmodel`。
2. 转换：转换层脚本把 `.bbmodel` 转成新的 `EntityModel`/`LayerDefinition` + `AnimationDefinition`（自动生成代码骨架）。
3. 接入：复制 `demobeast` 包为模板，换注册 id/模型类/纹理路径/动画触发即可，接入清单见 §5.3。
4. 验证：`gradle build` + 游戏内生成/走动/动画检查（§7 清单）。

线路的产物形态：工具脚本 + 转换层 + 一个可复制的实体包模板（`demobeast` 本身即模板）。

## 10. 可复用资产（skill + 脚本）

流水线不只一次性跑通，还要沉淀成以后能直接调用的资产：

### 10.1 工具脚本（可执行部分）

`build/tools/blockbench/`（node/playwright）：launch / model / anim / shot / export / convert 六脚本，命令行直接可用（含参数说明）。

### 10.2 项目 skill（程序性步骤）

新建 project-scope skill `quirky-blockbench-modeling`（写入 `~/.pi/agent/projects-memory/minecraft/skills/`，与现有 quirky-mixin-runtime-audit / quirky-new-item-checklist 同层）：

- **when_to_use**：需要给 Quirky 新增/修改带模型动画的自定义实体时；需要手动调模型时。
- **procedure_steps**：按 §9 线路四步（建模 → 转换 → 接入 → 验证），引用具体脚本命令与 demobeast 模板路径。
- **pitfalls**：blockbench-mcp-plugin 仅桌面版（网页版装不了）；WebGL 需 `--enable-unsafe-swiftshader`；截图先示意后落地；26.2 API 一律对照 `$HOME/.cache/mcsrc`（动画时长/插值映射、RenderState 模式）；网页版下载产物走 CDP setDownloadBehavior。
- **verification_steps**：截图出现在 build/previews/；gradle build 通过；游戏内生成/走动/动画清单（§7）。

用法：以后说"给 X 动物建模"→ 我调 skill 按线路执行；用户也可直接跑脚本。

## 8. 风险与备选

- **风险 1**：Blockbench 网页版在 headless + 软件渲染下视口渲染慢 → 截图耗时但可用（已验证 WebGL2 可用）；若不可用，fallback 用 `--use-gl=angle --use-angle=swiftshader` 组合再试。
- **风险 2**：26.2 动画 API 细节（`KeyframeAnimations.animate` 签名、`AnimationChannel.Targets` 枚举）与旧版本知识不符 → 全部以 mcsrc 源码为准，计划阶段逐项验证。
- **风险 3**：网页版文件下载/保存受限 → 用 CDP `Browser.setDownloadBehavior` 配下载目录拿产物（Playwright 原生支持）。
- **备选（兜底）**：若 Playwright+网页版链路不顺，降级纯代码生成 `.bbmodel` JSON + 手写 Java（不装任何东西，但无截图验证能力）。
