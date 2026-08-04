# Blockbench 建模流水线 + demo_beast 实体 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭建三条可复用线路（Blockbench 网页版建模 → .bbmodel 转 26.2 Java → demo_beast 实体接入），沉淀脚本 + skill。

**Architecture:** Termux 原生 chromium-browser 149（headless + SwiftShader WebGL2，已验证）跑 Blockbench 网页版；Node/Playwright 脚本通过 `page.evaluate` 调 Blockbench 全局 API 建模型/骨骼/动画并截图；`.bbmodel` JSON 由 convert.js 转成 26.2 Java（EntityModel/LayerDefinition + AnimationDefinition/KeyframeAnimation）；demo_beast（四足小兽）最小集接入 Quirky 验证线路。

**Tech Stack:** Node + Playwright（executablePath 指向 `chromium-browser`）、Blockbench 网页版（web.blockbench.net）、Fabric 26.2（mojmap）、fabric-rendering-v1、JUnit 5（仅可测逻辑）。

## Global Constraints

- 26.2 API 一律以 `$HOME/.cache/mcsrc` 源码为准，不凭记忆（本计划中签名均已验证）。
- 模型/动画形态每阶段先截图到 `build/previews/`，用户确认后才落地正式资源。
- 最小集：不做繁殖/掉落/复杂 AI/实体生成规则。
- 只修改任务清单列出的文件；docs 提交用 `git add -f`（docs/ 在 .gitignore）。
- 动画关键帧：`Keyframe(float timestamp, Vector3fc postTarget, Interpolation)`（`net.minecraft.client.animation.Keyframe`，秒制；注意 `net.minecraft.util.Keyframe` 是另一个类勿混用）。
- `KeyframeAnimations.posVec(x,y,z)` y 取负、`degreeVec` 度→弧度、`scaleVec` 值减 1（mcsrc 已验）。
- 插值仅 `Interpolations.LINEAR` / `CATMULLROM`（mcsrc 已验，26.2 无 STEP——bbmodel 的 step 插值转 LINEAR 并注明）。
- 动画播放：`AnimationDefinition.bake(ModelPart root)` → `KeyframeAnimation`，用 `.apply(AnimationState, ageInTicks)` / `.applyWalk(animationPos, animationSpeed, speedFactor, scaleFactor)` / `.applyStatic()`（mcsrc 已验）。
- 建模脚本调 Blockbench 全局 API 的调用模式依据 jasonjgardner/blockbench-mcp-plugin 源码（`server/tools/*.ts`），对象名 `Project/Group/Cube/Animation/BoneAnimator/Undo/Canvas`；实现时以页面实测为准。
- 不安装 blockbench-mcp-plugin（desktop-only，网页版装不了）。

---

### Task 1: 建模工具脚本骨架（launch.js + shot.js）

**Files:**
- Create: `build/tools/blockbench/package.json`
- Create: `build/tools/blockbench/launch.js`
- Create: `build/tools/blockbench/shot.js`

**Interfaces:**
- Consumes: `chromium-browser`（已装 149）、`web.blockbench.net`（已验证可达）、`npm playwright`（本任务安装）。
- Produces: `launch.js` 输出 `--json` 模式返回 `{browserWs, pageLoaded, webgl}`；`shot.js` 接受 `--out <path>` 截图到指定 png。

- [ ] **Step 1: 初始化工具目录与依赖**

```bash
mkdir -p build/tools/blockbench build/previews
cd build/tools/blockbench
npm init -y >/dev/null
npm install playwright@latest 2>&1 | tail -3   # SKIP_BROWSER_DOWNLOAD=1 环境变量（用系统 chromium）
```

`package.json` 加 `"type": "commonjs"`。

- [ ] **Step 2: 写 launch.js**

```js
// launch.js — 启动 headless chromium 打开 Blockbench 网页版，等待加载完成
const { chromium } = require("playwright");
const CHROME = "/data/data/com.termux/files/usr/bin/chromium-browser";
const URL = process.env.BLOCKBENCH_URL || "https://web.blockbench.net/";

(async () => {
  const browser = await chromium.launch({
    executablePath: CHROME,
    headless: true,
    args: ["--no-sandbox", "--enable-unsafe-swiftshader", "--disable-dev-shm-usage"],
  });
  const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
  // 下载产物目录（导出 .bbmodel 用）
  await page.context().grantPermissions?.([]);
  await page.goto(URL, { waitUntil: "domcontentloaded", timeout: 90000 });
  await page.waitForFunction(() => typeof Blockbench !== "undefined", null, { timeout: 60000 });
  await page.waitForFunction(() => typeof Project !== "undefined" && typeof Canvas !== "undefined", null, { timeout: 60000 });
  const webgl = await page.evaluate(() => {
    const c = document.createElement("canvas");
    return !!(c.getContext("webgl2") || c.getContext("webgl"));
  });
  console.log(JSON.stringify({ ok: true, webgl, title: await page.title() }));
  // 常驻进程，供其他脚本通过 --connect 复用（简化：每脚本独立启动，先这样）
  await browser.close();
})().catch(e => { console.error(JSON.stringify({ ok: false, error: String(e) })); process.exit(1); });
```

- [ ] **Step 3: 写 shot.js（截图当前视口）**

```js
// shot.js — 打开 Blockbench，等待场景就绪后截图到指定文件
const { chromium } = require("playwright");
const CHROME = "/data/data/com.termux/files/usr/bin/chromium-browser";
const out = process.argv[2] || "build/previews/shot.png";
(async () => {
  const browser = await chromium.launch({ executablePath: CHROME, headless: true,
    args: ["--no-sandbox", "--enable-unsafe-swiftshader", "--disable-dev-shm-usage"] });
  const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
  await page.goto("https://web.blockbench.net/", { waitUntil: "domcontentloaded", timeout: 90000 });
  await page.waitForFunction(() => typeof Blockbench !== "undefined" && typeof Canvas !== "undefined", null, { timeout: 60000 });
  await page.waitForTimeout(8000); // 视口首帧渲染
  await page.screenshot({ path: out });
  console.log(`saved ${out}`);
  await browser.close();
})().catch(e => { console.error(String(e)); process.exit(1); });
```

- [ ] **Step 4: 验证**

```bash
cd build/tools/blockbench
node launch.js        # 期望 {"ok":true,"webgl":true,...}
node shot.js ../../previews/bb_launch.png && ls -la ../../previews/bb_launch.png
```

- [ ] **Step 5: Commit**

```bash
git add build/tools/blockbench/package.json build/tools/blockbench/launch.js build/tools/blockbench/shot.js build/previews/bb_launch.png
git commit -m "feat: blockbench modeling tool skeleton (launch + shot via playwright)"
```

### Task 2: model.js（建项目/骨骼/Cube/UV）

**Files:**
- Create: `build/tools/blockbench/model.js`

**Interfaces:**
- Consumes: Task 1 的 launch 模式（独立启动 chromium + 打开网页版）。
- Produces: `node model.js --init --json <spec.json>` 在 Blockbench 中建立项目与骨骼树；`node model.js --screenshot <out.png>` 截图当前视口。spec.json 结构：`{bones:[{name,parent,origin:[x,y,z]},...], cubes:[{name,bone,from:[x,y,z],to:[x,y,z],uv:[u,v]},...]}`。

- [ ] **Step 1: 写 model.js 核心（evaluate 调 Blockbench API）**

```js
// 核心 evaluate 片段（Blockbench 全局 API，模式同 MCP 插件源码）
await page.evaluate((spec) => {
  // 新建 Minecraft Java 实体项目
  if (!Project.selected) {
    const p = new Project({ name: "demo_beast", box_uv: false, identifier: "quirky:demo_beast" });
    p.init();
  }
  const model = Project.selected;
  // 骨骼
  const boneMap = {};
  for (const b of spec.bones) {
    const g = new Group({ name: b.name, origin: b.origin || [0,0,0] }).init();
    if (b.parent && boneMap[b.parent]) g.addTo(boneMap[b.parent]);
    boneMap[b.name] = g;
  }
  // 立方体（from/to 为最小/最大角点）
  for (const c of spec.cubes) {
    const cube = new Cube({ name: c.name, from: c.from, to: c.to, origin: c.origin || c.from,
      uv_offset: c.uv || [0,0] }).init();
    cube.addTo(boneMap[c.bone] || "root");
  }
  Canvas.updateAll();
}, spec);
```

- [ ] **Step 2: 加 --screenshot 支持**（复用 Task 1 shot.js 逻辑：等待 `Canvas.updateAll` 后 `page.waitForTimeout(3000)` 截图）

- [ ] **Step 3: 用最小 spec 验证（一根腿骨架 + 一个 cube）**

```bash
node model.js --init --json '{"bones":[{"name":"root"},{"name":"leg_l","parent":"root","origin":[0,0,0]}],"cubes":[{"name":"leg_box","bone":"leg_l","from":[0,0,0],"to":[2,2,2],"uv":[0,0]}]}'
node model.js --screenshot ../../previews/model_basic.png
```

- [ ] **Step 4: 检查截图**（应看到立方体网格场景），必要时对照 MCP 插件源码调整 API 调用（`server/tools/cubes.ts`、`server/tools/element.ts` 是调用参考）。

- [ ] **Step 5: Commit**

```bash
git add build/tools/blockbench/model.js build/previews/model_basic.png
git commit -m "feat: blockbench model.js — create project/bones/cubes via page.evaluate"
```

### Task 3: anim.js（动画关键帧）+ export.js（.bbmodel 导出）

**Files:**
- Create: `build/tools/blockbench/anim.js`
- Create: `build/tools/blockbench/export.js`

**Interfaces:**
- Consumes: Task 2 的模型。
- Produces: `anim.js --add --json <anim.json>`（anim.json：`{name,loop,length,bones:{bone:[{time,position?,rotation?,scale?}]}}`）；`export.js --out <path.bbmodel>` 导出项目 JSON。

- [ ] **Step 1: anim.js——动画创建（模式同 MCP 插件 `server/tools/animation.ts`）**

```js
await page.evaluate((anim) => {
  // 方式 A（推荐，同 MCP 插件 create_animation）：Animator.loadFile 加载 Bedrock 动画
  const data = {
    format_version: "1.8.0",
    animations: { [`animation.${anim.name}`]: {
      loop: anim.loop, animation_length: anim.length,
      bones: Object.fromEntries(Object.entries(anim.bones).map(([bone, kfs]) => {
        const ch = { position: {}, rotation: {}, scale: {} };
        for (const kf of kfs) {
          if (kf.position) ch.position[kf.time] = kf.position;
          if (kf.rotation) ch.rotation[kf.time] = kf.rotation;
          if (kf.scale) ch.scale[kf.time] = kf.scale;
        }
        return [bone, ch];
      })),
    }},
  };
  Animator.loadFile({ content: JSON.stringify(data) });
}, anim);
```

- [ ] **Step 2: export.js——导出 .bbmodel**

```js
const json = await page.evaluate(() => {
  // Blockbench 项目序列化
  return Project.selected.save();
});
require("fs").writeFileSync(out, JSON.stringify(json, null, 2));
```

（`Project.selected.save()` 返回可 JSON 序列化的模型数据；若不可用，用 `JSON.stringify(Project.selected, (k,v) => k === 'mesh' ? undefined : v)` 兜底，实现时实测。）

- [ ] **Step 3: 验证——给 Task 2 模型加一条 2 秒循环动画并截图两个时间点**

```bash
node anim.js --add --json '{"name":"walk","loop":true,"length":2,"bones":{"leg_l":[{"time":0,"rotation":[0,0,30]},{"time":1,"rotation":[0,0,-30]},{"time":2,"rotation":[0,0,30]}]}}'
node model.js --screenshot ../../previews/anim_t0.png
node export.js --out ../../models/demo_beast.bbmodel && head -c 300 ../../models/demo_beast.bbmodel
```

- [ ] **Step 4: 截图给用户确认动画骨架可行，Commit**

```bash
git add build/tools/blockbench/anim.js build/tools/blockbench/export.js build/previews/anim_t0.png models/demo_beast.bbmodel
git commit -m "feat: blockbench anim.js + export.js — keyframe animations and bbmodel export"
```

### Task 4: convert.js（.bbmodel → 26.2 Java 模型/动画代码）

**Files:**
- Create: `build/tools/blockbench/convert.js`
- Create: `build/tools/blockbench/templates/DemoBeastModel.java.txt`（代码模板）

**Interfaces:**
- Consumes: Task 3 的 `.bbmodel` 文件。
- Produces: `convert.js --in <x.bbmodel> --out <dir>` 生成 `XxxModel.java` + `XxxAnimations.java`（26.2 mojmap 代码）。

- [ ] **Step 1: 确认 bbmodel JSON 字段**（`meta/model_format`、`elements[]`（from/to/uv/name）、`outliner`、`animations[]`（bones 关键帧）），用 Task 3 导出的文件实测字段名。

- [ ] **Step 2: convert.js 转换规则**

- 模型：每个 outliner 骨骼 → `ModelPart`；每个 element → `CubeListBuilder.addBox(name, fromX, fromY, fromZ, w, h, d, new CubeDeformation(0))` + `texOffs(uvX, uvY)`；根骨骼在 `createBodyLayer()` 中 `MeshDefinition`/`PartDefinition` 组装（模板给出固定骨架，convert.js 填充 box 数据与父子关系）。
- 动画：每条 animation → `AnimationDefinition.Builder.withLength(seconds)`；每骨骼每通道 → `.addAnimation(boneName, new AnimationChannel(AnimationChannel.Targets.ROTATION, new Keyframe(t, KeyframeAnimations.degreeVec(x,y,z), AnimationChannel.Interpolations.LINEAR)))`（position 用 posVec、scale 用 scaleVec；时间=秒）。插值映射：linear→LINEAR、smooth/catmullrom→CATMULLROM、step/bezier→LINEAR（注释标注）。
- 输出：`DemoBeastModel.java`（`public static LayerDefinition createBodyLayer()` + `public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(Identifier.of("quirky","demo_beast"), "main")`）、`DemoBeastAnimations.java`（`public static final AnimationDefinition WALK/IDLE/TAIL_WAG`）。模板中 model 类含 `setupAnim(EntityRenderState, float walkPos, float walkSpeed, float partialTick)` 骨架，动画应用走 `AnimationDefinition.bake(root)` 的 `KeyframeAnimation`（Task 5 实装时接 AnimationState）。

- [ ] **Step 3: 验证——转换 Task 3 的 demo_beast.bbmodel，产物放 `build/generated/`（不直接进 src）**

```bash
node convert.js --in ../../models/demo_beast.bbmodel --out ../../build/generated/
ls ../../build/generated/
```

- [ ] **Step 4: 人工检查生成代码的 API 与 mcsrc 一致**（`AnimationDefinition.Builder.addAnimation`、`Keyframe` 构造、`LayerDefinition.create`、`CubeListBuilder`），不一致就地修正模板。Commit。

```bash
git add build/tools/blockbench/convert.js build/tools/blockbench/templates/ build/generated/
git commit -m "feat: convert.js — bbmodel to 26.2 java model/animation code generator"
```

### Task 5: demo_beast 完整建模（骨架/cube/纹理/三动画 + 截图确认）

**Files:**
- Create: `build/tools/blockbench/specs/demo_beast.json`（模型 spec）
- Create: `build/tools/blockbench/specs/demo_beast_anims.json`（动画 spec）
- Create: `build/previews/demo_beast_*.png`（各阶段截图）
- Create: `build/tools/blockbench/texgen.js`（程序生成 64×64 纹理 png）

**Interfaces:**
- Consumes: Task 2/3 脚本。
- Produces: `models/demo_beast.bbmodel` 最终版 + 纹理 `assets/quirky/textures/entity/demo_beast/demo_beast.png` 的原型（放 `build/previews/` 待确认）。

- [ ] **Step 1: 设计模型 spec**（四足小兽，约 10 cube，64×64 纹理）：root 下 body（from 约 [-4,-2,-6] 到 [4,6,6]，即 8×8×12 居中偏上）、head（前上方 + 双耳）、4 腿（每条 2×6×2，微外扩）、tail（后部，细长）。数值写在 `specs/demo_beast.json`。

- [ ] **Step 2: 建模型 + 正/侧/前/透四视图截图**，`build/previews/demo_beast_*.png`，**用户确认形态后才继续**。

- [ ] **Step 3: 纹理**——texgen.js 用 node 生成 64×64 png（纯色底 + 简单花纹/眼睛），写入 `build/previews/demo_beast_tex.png` 并在 Blockbench 中赋给模型（evaluate 设置 `model.textures[0].source`），截图确认。

- [ ] **Step 4: 三动画**（walk 2s 对角步态四腿摆动 + 身体俯仰、idle 1.5s 呼吸、tail_wag 1s 尾巴左右摇），每动画截图 2 个时间点给用户确认。

- [ ] **Step 5: 导出最终 .bbmodel + 截图存档，Commit**

```bash
node export.js --out ../../models/demo_beast.bbmodel
git add models/demo_beast.bbmodel build/tools/blockbench/specs/ build/tools/blockbench/texgen.js build/previews/
git commit -m "feat: demo_beast model + textures + walk/idle/tail_wag animations (user-approved previews)"
```

### Task 6: 服务端实体接入（EntityType/实体类/属性/AI）

**Files:**
- Modify: `src/main/java/dev/quirky/ModEntities.java`
- Create: `src/main/java/dev/quirky/demobeast/DemoBeastEntity.java`
- Create: `src/main/java/dev/quirky/demobeast/DemoBeastAttributes.java`（或并入实体类）

**Interfaces:**
- Consumes: Task 5 的动画名（walk/idle/tail_wag）——客户端用；本任务只做实体骨架。
- Produces: `ModEntities.DEMO_BEAST`（`EntityType<DemoBeastEntity>`，`ResourceKey` 注册模式沿用现有文件）；`DemoBeastEntity extends Animal`，`isFood` 小麦种子，属性：移动速度 0.25、生命 10，goal：`WaterAvoidingRandomStrollGoal`(1.0) + `RandomLookAroundGoal` + `LookAtPlayerGoal`（26.2 类名已验，无 WanderAroundGoal）。

- [ ] **Step 1: 对照 mcsrc 确认 `Animal`/`AgeableMob` 抽象方法**（`getBreedOffspring` 等必须实现的抽象方法），`RandomStrollGoal` 构造签名（`Goal`/`RandomStrollGoal(float speedModifier)` 变体），写实体类：

```java
package dev.quirky.demobeast;

public class DemoBeastEntity extends Animal {
    public DemoBeastEntity(EntityType<? extends Animal> type, Level level) { super(type, level); }
    @Override public boolean isFood(ItemStack stack) { return stack.is(Items.WHEAT_SEEDS); }
    @Override public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return ModEntities.DEMO_BEAST.create(level); // 26.2 签名以 mcsrc 为准
    }
    // 注册 goals + 属性（registerGoals / createAttributes，对照 mcsrc Animal/AgeableMob）
}
```

- [ ] **Step 2: ModEntities 注册**（沿用现有 `ResourceKey.create(Registries.ENTITY_TYPE, QuirkyMod.id("demo_beast"))` + `EntityType.Builder.of(DemoBeastEntity::new, MobCategory.CREATURE).sized(0.8F, 0.8F)`）。

- [ ] **Step 3: 属性注册**（fabric 方式或沿用项目现有实体模式；`Attributes.MOVEMENT_SPEED` 0.25、`MAX_HEALTH` 10，以 mcsrc `Mob.createMobAttributes` 为基）。

- [ ] **Step 4: 验证**

```bash
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk PATH=... gradle build --no-daemon --console=plain
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/quirky/ModEntities.java src/main/java/dev/quirky/demobeast/
git commit -m "feat: demo_beast entity — registration, attributes, minimal AI"
```

### Task 7: 客户端接入（模型/动画/渲染器/纹理）

**Files:**
- Create: `src/client/java/dev/quirky/client/demobeast/DemoBeastModel.java`（由 Task 4 模板+convert 产物调整而来）
- Create: `src/client/java/dev/quirky/client/demobeast/DemoBeastAnimations.java`（Task 4 产物）
- Create: `src/client/java/dev/quirky/client/demobeast/DemoBeastRenderState.java`
- Create: `src/client/java/dev/quirky/client/demobeast/DemoBeastRenderer.java`
- Modify: `src/client/java/dev/quirky/client/QuirkyModClient.java`
- Create: `src/main/resources/assets/quirky/textures/entity/demo_beast/demo_beast.png`

**Interfaces:**
- Consumes: Task 5 纹理（用户确认后的正式版）、Task 6 的 `ModEntities.DEMO_BEAST`。
- Produces: 注册 `EntityRenderers.register(ModEntities.DEMO_BEAST, DemoBeastRenderer::new)` + `ModelLayerRegistry.registerModelLayer(LAYER_LOCATION, () -> DemoBeastModel.createBodyLayer())`（fabric-rendering-v1，签名已验证）。

- [ ] **Step 1: 对照 mcsrc 原版动物渲染器**（`ChickenRenderer`/`WolfRenderer` 的 RenderState 模式：`extractRenderState`/`setupAnim`/RenderState 字段），写 DemoBeastRenderState（含 `AnimationState walk/idle/tailWag`）与 DemoBeastRenderer（extends `MobRenderer<DemoBeastEntity, DemoBeastRenderState, DemoBeastModel>`）。

- [ ] **Step 2: DemoBeastModel**（extends `EntityModel<DemoBeastRenderState>`）：`createBodyLayer()` 来自 Task 4 产物；`setupAnim(DemoBeastRenderState state, float walkPos, float walkSpeed, float partialTick)` 中：`walk` 用 `KeyframeAnimation.applyWalk(walkPos, walkSpeed, 2.0F, 2.5F)`，`idle`/`tailWag` 用 `apply(state.idle, partialTick)`（AnimationState 在 `tick`/`extractRenderState` 中 `startIfStopped`，对照原版 `WolfModel` 的 AnimationState 用法）。

- [ ] **Step 3: QuirkyModClient 注册**（沿用现有 `EntityRenderers.register` 行 + `ModelLayerRegistry.registerModelLayer`）。

- [ ] **Step 4: 纹理落地**（Task 5 确认后的 png 复制到 `src/main/resources/assets/quirky/textures/entity/demo_beast/demo_beast.png`；lang 键 `entity.quirky.demo_beast` 加入语言文件）。

- [ ] **Step 5: 验证**

```bash
gradle build --no-daemon --console=plain   # 必须通过
```

- [ ] **Step 6: Commit**

```bash
git add src/client/java/dev/quirky/client/demobeast/ src/client/java/dev/quirky/client/QuirkyModClient.java src/main/resources/assets/quirky/ src/main/resources/assets/quirky/lang/
git commit -m "feat: demo_beast client — model, animations, renderer, texture"
```

### Task 8: 生成蛋 + 配方 + 语言键

**Files:**
- Modify: `src/main/java/dev/quirky/ModItems.java`
- Create: `src/main/resources/data/quirky/recipe/demo_beast_spawn_egg.json`
- Modify: `src/main/resources/assets/quirky/lang/en_us.json`、`zh_cn.json`（若存在）

**Interfaces:**
- Consumes: `ModEntities.DEMO_BEAST`。
- Produces: `ModItems.DEMO_BEAST_SPAWN_EGG`（`new SpawnEggItem(new Item.Properties().spawnEgg(ModEntities.DEMO_BEAST))`，26.2 构造已验证）+ 无序配方（生成蛋 + 小麦种子）+ lang。

- [ ] **Step 1: ModItems 注册**（沿用现有注册模式，注意 `Item.Properties().spawnEgg(type)` 26.2 新 API）。
- [ ] **Step 2: 配方 json + lang 键**（`item.quirky.demo_beast_spawn_egg`、`entity.quirky.demo_beast`）。
- [ ] **Step 3: 验证**

```bash
gradle build --no-daemon --console=plain
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/quirky/ModItems.java src/main/resources/data/quirky/recipe/ src/main/resources/assets/quirky/lang/
git commit -m "feat: demo_beast spawn egg + recipe + lang keys"
```

### Task 9: 沉淀 skill + 用户游戏内验收

**Files:**
- Create: `~/.pi/agent/projects-memory/minecraft/skills/quirky-blockbench-modeling/SKILL.md`
- 不修改仓库文件（skill 在项目 memory 目录）。

**Interfaces:**
- Consumes: 全部前面任务的产物路径。

- [ ] **Step 1: 写 skill**（when_to_use：新增/修改带模型动画的自定义实体；procedure_steps：四步线路+具体命令；pitfalls：网页版装不了 desktop 插件、WebGL 需 `--enable-unsafe-swiftshader`、26.2 动画 API 要点（Keyframe 秒制、插值仅 LINEAR/CATMULLROM、bake→KeyframeAnimation）、ModelLayerRegistry/EntityRendererRegistry/`Item.Properties().spawnEgg`、先截图后落地；verification_steps：截图 + gradle build + 游戏内清单）。

- [ ] **Step 2: 用户游戏内验收**：生成蛋刷出 demo_beast → 走动 walk 动画 → 静止 idle → 尾巴摇动。逐项对照 §1 验收标准，任一不通过则回到对应任务修复。

- [ ] **Step 3: 收尾提交**

```bash
git add build/tools/blockbench/ && git commit -m "chore: finalize blockbench modeling pipeline tooling"
```

## Self-Review Notes

- Spec §2 架构三节点 → Task 1-4（工具层）、Task 4（转换层）、Task 6-8（接入层）；§5 演示实体 → Task 5-8；§9 复用指南 → 依赖 Task 1-9 产物；§10 skill → Task 9。
- 26.2 API 全部在本计划撰写前经 mcsrc/javap 验证：AnimationDefinition/Keyframe/AnimationChannel/KeyframeAnimation/EntityModel/MobRenderer/ModelLayerRegistry/EntityRendererRegistry/SpawnEggItem.spawnEgg/EntityType.Builder/RandomStrollGoal。
- 插值 STEP 在 26.2 不存在（已验证 Interpolations 仅 LINEAR/CATMULLROM）→ 转换规则中已注明映射。
- 建模脚本的 Blockbench 内部 API 以 MCP 插件源码为参考模式，脚本实现时若页面实测有出入，以实测为准并更新本计划对应步骤（这是探索性环节，plan 已给出兜底与验证手段）。
