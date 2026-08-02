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

- Cloud placement must skip blocks intersecting the player's own bounding box; otherwise the cloud can spawn inside the player.
- Instant-use items must run the same reach/validity check on client and server before returning success, so a failed use cannot consume the item locally.
- Map tooltip drawing must stay inside the reported `getWidth`/`getHeight` bounds; the parchment border and map content must align to the component origin.
- Client visuals still require desktop-client manual verification; build success alone does not prove hand-feel details.

## Agent-Specific Instructions

- **写实现计划（`docs/superpowers/plans/`）必须过 CodeGraph**：计划中引用的每个项目符号（类名、方法、mixin 注入点、注册模式、资源/配置文件路径）都必须先用 `codegraph explore` 验证与当前代码库一致（`.codegraph/` 存在时），发现不符立即修正计划，不得凭记忆写路径或签名。
- **子代理工作区隔离**：派发子代理（含并行 worktree 模式）时，任务文本必须明确"只允许修改任务清单列出的文件"；新建文档必须放 `docs/superpowers/` 下；禁止在仓库根级新增/修改任务外文件（如 `docs/*.md` 根级文档、`AGENTS.md`）；会话日志/脚本一律放 worktree 目录内，用绝对路径避免污染其他 worktree。
- **项目知识沉淀**：Quirky 特有的教训/机制结论（26.2 API 实测、bug 根因、构建坑）写入 hermes-memory（`~/.pi/agent/pi-hermes-memory/MEMORY.md`），旧 `.learnings/` 已废弃不再写入；程序性步骤用 skill 体系（`~/.pi/agent/pi-hermes-memory/skills/`）。

Agents must follow the root `AGENTS.md` constraints and the Superpowers subagent workflow. Dispatched agents must not create nested agents, must report `Status`, build output, and file changes, and must not modify files outside their assigned task scope. External review feedback is verified against the code before being implemented; reviewer suggestions are not applied blindly.
