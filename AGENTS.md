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

- Use tabs for indentation in Java and JSON.
- Use 26.2 official mapping names; verify APIs against `$HOME/.cache/mcsrc` before use.
- Name mixin classes `XxxMixin` and client mixin accessors `XxxAccessor`.
- Use `lower_snake_case` for resource paths and `tooltip.quirky.*` for tooltip language keys.
- Prefer Fabric API events over custom hooks when the API already provides one.
- Edit files with `apply_patch`; do not use shell redirection to write files.

## Testing Guidelines

Verification is build-level plus review:

- Every task must end with a passing `gradle build`.
- Check gameplay-dependent client features manually on a desktop client (map preview, tooltips, equip swap).
- Check server mechanics with a dedicated server or in-game session.
- Each task goes through spec-compliance and code-quality review before it is considered complete.

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
- For a pull request, summarize the change, link the related issue, and include a manual verification checklist.

This environment does not push to remote repositories; merge and publish locally or through the host UI.

## Agent-Specific Instructions

Agents must follow the root `AGENTS.md` constraints and the Superpowers subagent workflow. Dispatched agents must not create nested agents, must report `Status`, build output, and file changes, and must not modify files outside their assigned task scope.
