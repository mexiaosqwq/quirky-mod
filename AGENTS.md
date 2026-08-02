# Repository Guidelines

## Project Structure & Module Organization

This is **Quirky**, a Fabric mod for Minecraft 26.2 using official Mojang mappings.

- `src/main/java/dev/quirky/` - common and server-side code: item registration, harvest logic, payloads, and common mixins.
- `src/client/java/dev/quirky/client/` - client-only code: screens, tooltip rendering, client mixins, and networking callbacks.
- `src/main/resources/` - `fabric.mod.json`, mixin configs, models, textures, recipes, and language files.
- `docs/superpowers/specs/` and `docs/superpowers/plans/` - approved design spec and implementation plan.
- `.worktrees/quirky/` - isolated implementation worktree used during feature development.

Keep mod id `quirky`, Java base package `dev.quirky`, and one mechanic per package.

## Build, Test, and Development Commands

Build the mod with Java 25:

```sh
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle build --no-daemon --console=plain
```

`gradle genSources` generates decompiled 26.2 sources used for API verification. Unit tests live under `src/test/java` and run with `gradle test`; game-dependent client behavior still needs manual verification.

## Coding Style & Naming Conventions

- Use tabs for indentation in Java; keep JSON files in the repository's existing 2-space style.
- Use 26.2 official mapping names; verify APIs against `$HOME/.cache/mcsrc` before use.
- Name mixin classes `XxxMixin` and client mixin accessors `XxxAccessor`.
- Use `lower_snake_case` for resource paths and `tooltip.quirky.*` for tooltip language keys.
- Prefer Fabric API events over custom hooks when the API already provides one.
- Use the pi `edit`/`write` tools for file edits; do not use shell redirection to write files.

## Testing Guidelines

Verification is build-level plus review:

- Every task must end with a passing `gradle build`.
- Check gameplay-dependent client features manually on a desktop client (map preview, tooltips, equip swap).
- Check server mechanics with a dedicated server or in-game session.
- Each task goes through spec-compliance and code-quality review before it is considered complete; review findings are triaged before merge.

## Mechanic Polish & Detail Requirements

Small interactive mechanics must keep the hand-feel details that make them feel natural, not just work logically.

- Add sounds for every user-facing action: use item, harvest/replant, eat/spit, open/close.
- Show particles, swing, or other visible feedback when a block is harvested or destroyed.
- Preserve physical details such as item-entity ejection, thrower, pickup delay, and the vanilla map parchment border.
- List these details explicitly in specs and implementation plans; "the logic works" is not an acceptable substitute.

## Commit & Pull Request Guidelines

Use conventional commit prefixes from this repository's history: `feat:`, `fix:`, `chore:`, and `docs:`.

- Keep one logical change per commit.
- Include the build result in task reports.
- Update README and user-facing docs in the same change when behavior changes; stale docs are incomplete work.
- For a pull request, summarize the change, link the related issue, and include a manual verification checklist.

This environment does not push to remote repositories; merge and publish locally or through the host UI.

## Execution & Cleanup Notes

- Once a request is explicit and confirmed, run the safety check and execute end-to-end in the same turn; do not pause between announced intermediate steps.
- Use the simplest implementation for simple requests (copy is copy; do not introduce symlinks or migration scaffolding).
- For destructive deletion, list targets and ask first unless the user already named them; after confirmation, delete and verify in one pass.
- Before deleting a git repository, preserve history with `git bundle create <backup>.bundle --all`; scan external references and fix broken links in the same cleanup.

## Known Quirky Pitfalls

- **26.2 物品模型必须双文件**：新增物品时必须同时提供 `assets/quirky/items/<id>.json`（新格式定义 `{"model": {"type": "minecraft:model", "model": "quirky:item/<id>"}}`）与 `assets/quirky/models/item/<id>.json`（实际模型）；只写旧格式 `models/item/` 不生效 → 物品与实体渲染紫黑棋盘格（云瓶 4e85dff、图腾均踩过此坑）。新物品资源清单对照 `bottled_cloud` 逐项核对：items/ + models/item/ + textures/item/ + lang 键 + 注册代码。

- **Cloth Config 26.2 适配**（javap + v26.2 源码实测）：无 `BoundedAbove/Below`、无 `Gui.Slider`、无 `@ServerConfig`——仅 `BoundedDiscrete(long min,max)`（int/long，自动 slider）且不强制反序列化边界，运行时自行 clamp；`AutoConfig.getConfigScreen` 不存在，用 `AutoConfigClient.getConfigScreen` 返回 `Supplier<Screen>`；`ConfigHolder.load()` 返回 boolean 不抛异常，失败时内部 resetToDefault 换新实例——热重载按返回值分支、成功后重新注入静态 holder；GUI 翻译键 tooltip 后缀是 `@Tooltip` 非 `.tooltip`，无缺键回退。依赖 `cloth-config-fabric:26.2.155` + `modmenu:20.0.0-beta.2`，Loom 无 modApi/modCompileOnly（用 implementation/compileOnly）。配置读取走静态 `QuirkyConfigHolder`（测试注入默认实例，不碰 AutoConfig/文件系统）。

- **实体定位基准注意 floor 语义**：`Entity.blockPosition()` = `new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z))`（mcsrc Entity.java:3801）——站非整高方块（土径/耕地 15/16、半砖、雪层）顶部时脚底带小数（如 64.9375），floor 后基准低 1 格（图腾贴地根因）。需要精确相对位置时用 `getY()`（double）而非 blockPosition。

- **自定义箭必须补 `#minecraft:arrows` item tag**：`data/minecraft/tags/item/arrows.json` 含该箭 id，否则弓无法装填（26.2 弹药判定按 tag）；命中不可放置位置时既卡箭又掉物品会"双回收"（1 箭变 2 物）——只能保留一条回收路径。

- **HUD 元素注册用 attach 系列**：26.2 `HudElementRegistryImpl` 静态块已把 23 个原版元素注册为 RootLayer，`addLast(VanillaHudElements.HOTBAR, ...)` 的 validateUnique 第一次就命中已存在 layer → 永远 "Layer with identifier minecraft:hotbar already exists" 崩溃。**正确 API = `attachElementAfter(rootId, elementId, element)` / `attachElementBefore`**。项目 id 生成统一定 `QuirkyMod.id(path)`。

- **tooltip 扩展统一模式** = `Item.getTooltipImage(ItemStack) → Optional<TooltipComponent>`（HEAD 注入 `(ItemStack, CallbackInfoReturnable<Optional<TooltipComponent>>)`），服务端组件 + 客户端 ClientTooltipComponent + `ClientTooltipComponentCallback` 注册（潜影盒/食物/属性 tooltip 均走此路径）。画物品用 `graphics.item(stack,x,y)` + `graphics.itemDecorations(font,stack,x,y)`。

- **Mixin @Shadow/@Invoker 只匹配目标类本类成员**：指向父类（多层继承）方法时 Loom 不内联映射名（jar 保留 mojmap 名）→ 运行时（intermediary）找不到，启动报 `@Shadow ... was not located in the target class`（FlameParticleMixin 踩坑，commit 7fc6429）。最稳修复：@Mixin 目标改为成员所在类（如 `@Mixin(SingleQuadParticle)`），handler 里 `(Object) this instanceof FlameParticle` 过滤；或改 shadow **protected 字段**（父类字段可直接 shadow）。修复前用 `javap`/mcsrc 确认成员定义层；编进 jar 后检查 @Shadow 字符串是否被 remap。

- Cloud placement must skip blocks intersecting the player's own bounding box; otherwise the cloud can spawn inside the player.
- **26.2 mixin 运行时坑（编译全绿 ≠ 能跑，4 个崩溃实例见 26.2-mechanics-notes）**：① mixin 注解字符串编译期不校验——target 描述符（float 数量！）必须逐字对照 `$HOME/.cache/mcsrc`；② `@Shadow`/`@Invoker` 只匹配目标类【本类】成员，父类成员运行时抛 "was not located in the target class"；③ `@Inject(method="<init>")` 应用到所有构造器，目标类多构造器时须用完整描述符限定；④ fabric API 调用（如 HudElementRegistry）用 javap 验证运行时语义（addLast 对原版元素 id 首次即抛 already exists）。交付前过 `quirky-mixin-runtime-audit` skill 清单。
- Instant-use items must run the same reach/validity check on client and server before returning success, so a failed use cannot consume the item locally.
- Map tooltip drawing must stay inside the reported `getWidth`/`getHeight` bounds; the parchment border and map content must align to the component origin.
- Client visuals still require desktop-client manual verification; build success alone does not prove hand-feel details.

## Agent-Specific Instructions

- **子代理派发使用 pi-subagents**（根 AGENTS.md §13.6）：主对话说“用 scout/worker/... 做 X”或调用 `Agent` 工具（`subagent_type`/`prompt`/`description`/`run_in_background` 等）；后台 agent 用 `get_subagent_result` 取结果、`steer_subagent` 中途转向、`/agents` FleetView 管理。项目级 agent 定义放 `.pi/agents/*.md`（优先于全局 `~/.pi/agent/agents/`），本仓库的 Quirky 特化 agent（scout/planner/reviewer/worker 全部内置 26.2 API 实测要求与项目坑位，worker/reviewer 另有构建命令/陷阱清单）见 `.pi/agents/`。
- **写实现计划（`docs/superpowers/plans/`）必须过 CodeGraph**：计划中引用的每个项目符号（类名、方法、mixin 注入点、注册模式、资源/配置文件路径）都必须先用 `codegraph explore` 验证与当前代码库一致（`.codegraph/` 存在时），发现不符立即修正计划，不得凭记忆写路径或签名。
- **子代理工作区隔离**：派发子代理（含并行 worktree 模式）时，任务文本必须明确"只允许修改任务清单列出的文件"；新建文档必须放 `docs/superpowers/` 下；禁止在仓库根级新增/修改任务外文件（如 `docs/*.md` 根级文档、`AGENTS.md`）；会话日志/脚本一律放 worktree 目录内，用绝对路径避免污染其他 worktree。
- **项目知识沉淀**：Quirky 特有的教训/机制结论（26.2 API 实测、bug 根因、构建坑）按分层下沉——项目特有条目进本项目 AGENTS.md「Known Quirky Pitfalls」；全局性重要内容写入项目记忆 `~/.pi/agent/projects-memory/minecraft/MEMORY.md`；程序性步骤用 skill 体系（`~/.pi/agent/projects-memory/minecraft/skills/`）。旧 `~/.learnings/` 已废弃删除。

Agents must follow the root `AGENTS.md` constraints and the Superpowers subagent workflow. Dispatched agents must not create nested agents, must report `Status`, build output, and file changes, and must not modify files outside their assigned task scope. External review feedback is verified against the code before being implemented; reviewer suggestions are not applied blindly.
