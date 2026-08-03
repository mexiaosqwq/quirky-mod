# Clean Shulker Tooltip UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hand-drawn purple shulker contents panel with a vanilla tooltip background and vanilla 18 px container slot sprites while preserving the 9×3 contents preview.

**Architecture:** Keep the existing `TooltipComponent` → `ClientTooltipComponent` callback pipeline. The client component will draw only 27 vanilla `minecraft:container/slot` sprites and item contents; Minecraft's tooltip renderer remains responsible for the outer background/frame. Remove the now-unused dye color payload after the visual change is green.

**Tech Stack:** Fabric/Minecraft 26.2 official mappings, Java 25, JUnit 5, Mockito, system Gradle 9.6.1.

## Global Constraints

- Work only in `/data/data/com.termux/files/home/minecraft/.worktrees/shulker-tooltip-ui` on branch `feat/shulker-tooltip-ui`.
- Keep the 9×3 layout, empty-slot rendering, item counts, empty-box fallback, shulker tag filter, and CONTAINER text suppression unchanged.
- Use `minecraft:container/slot` through `GuiGraphicsExtractor.blitSprite`; do not add a new texture or hand-draw slot borders.
- Use the project build command with Java 25: `gradle test --no-daemon --console=plain` and finish with `gradle build --no-daemon --console=plain`.
- Follow TDD: each changed behavior gets a failing test before production code changes.

### Task 1: Encode the new client rendering contract

**Files:**
- Modify: `src/test/java/dev/quirky/client/tooltips/ClientShulkerTooltipComponentTest.java`

**Interfaces:**
- Consumes: existing `ShulkerTooltipComponent(ItemContainerContents, @Nullable DyeColor)` and `ClientShulkerTooltipComponent`.
- Produces: test expectations for a 162×54 component, 27 vanilla slot sprite calls, no hand-drawn fill/line calls, and item/count drawing at slot offset 1.

- [ ] **Step 1: Replace layout assertions**

Change the layout test to expect exactly `9 * 18` width and `3 * 18` height. Keep the existing 9×3 semantics.

- [ ] **Step 2: Replace hand-drawn background assertions with sprite assertions**

Use the vanilla sprite and pipeline constants:

```java
Identifier slotSprite = Identifier.withDefaultNamespace("container/slot");
client.extractImage(font, 0, 0, client.getWidth(font), client.getHeight(font), graphics);
verify(graphics, times(27)).blitSprite(
    eq(RenderPipelines.GUI_TEXTURED), eq(slotSprite), anyInt(), anyInt(), eq(18), eq(18)
);
verify(graphics, times(0)).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
verify(graphics, times(0)).horizontalLine(anyInt(), anyInt(), anyInt(), anyInt());
verify(graphics, times(0)).verticalLine(anyInt(), anyInt(), anyInt(), anyInt());
```

Add the required imports for `Identifier` and `RenderPipelines`.

- [ ] **Step 3: Add a non-empty count rendering assertion**

Create contents with `new ItemStack(Items.STONE, 3)`, render at origin, and verify the first item is drawn at `(1, 1)` and its decorations use the same offset:

```java
verify(graphics).item(eq(stack), eq(1), eq(1));
verify(graphics).itemDecorations(eq(font), eq(stack), eq(1), eq(1));
```

- [ ] **Step 4: Run the focused test and verify RED**

Run:

```sh
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle test --no-daemon --console=plain --tests dev.quirky.client.tooltips.ClientShulkerTooltipComponentTest
```

Expected: FAIL because the current component reports 170×62 and calls `fill`/line methods instead of `blitSprite`.

### Task 2: Implement the vanilla slot rendering

**Files:**
- Modify: `src/client/java/dev/quirky/client/tooltips/ClientShulkerTooltipComponent.java`

**Interfaces:**
- Consumes: `ShulkerTooltipComponent.contents()`.
- Produces: `ClientTooltipComponent` with `getWidth() == 162`, `getHeight() == 54`, and vanilla slot/item rendering.

- [ ] **Step 1: Add the vanilla sprite constant and remove obsolete color state**

Import `RenderPipelines` and `Identifier`; add:

```java
private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
```

Remove `DyeColor`, `Nullable`, `PADDING`, all color constants, the `color` field, and the `background`, `border`, `mix`, and `drawSlot` helpers.

- [ ] **Step 2: Implement exact component bounds**

Return:

```java
return COLS * SLOT_SIZE;
return ROWS * SLOT_SIZE;
```

- [ ] **Step 3: Draw each vanilla slot and its contents**

Inside the existing 27-slot loop, calculate `sx` and `sy` from `x`/`y` without padding, draw the sprite, then draw non-empty stacks at `sx + 1` and `sy + 1`:

```java
graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, sx, sy, SLOT_SIZE, SLOT_SIZE);
if (!stack.isEmpty()) {
    graphics.item(stack, sx + ICON_OFFSET, sy + ICON_OFFSET);
    graphics.itemDecorations(font, stack, sx + ICON_OFFSET, sy + ICON_OFFSET);
}
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the same focused Gradle test from Task 1. Expected: PASS.

### Task 3: Remove the obsolete dye color payload

**Files:**
- Modify: `src/main/java/dev/quirky/tooltips/ShulkerTooltipComponent.java`
- Modify: `src/main/java/dev/quirky/mixin/TooltipDetailsMixin.java`
- Modify: `src/test/java/dev/quirky/client/tooltips/ClientShulkerTooltipComponentTest.java`

**Interfaces:**
- Consumes: existing contents component and shulker item tag check.
- Produces: `ShulkerTooltipComponent(ItemContainerContents contents)` with no unused color field; all callers use the one-argument constructor.

- [ ] **Step 1: Change tests to the one-argument component constructor and verify RED**

Replace test construction with `new ShulkerTooltipComponent(contents)`, remove the `DyeColor` import and color assertion, and remove the old colored-background test because the production component no longer carries a color field. The desktop client checklist remains the source of truth for ordinary versus dyed box appearance. Run the focused test and confirm compilation fails because production still exposes only the old two-argument constructor.

- [ ] **Step 2: Remove color from the service component**

Change the record to:

```java
public record ShulkerTooltipComponent(ItemContainerContents contents) implements TooltipComponent {
}
```

Remove `DyeColor` and `Nullable` imports.

- [ ] **Step 3: Remove color extraction from the mixin**

Remove `BlockItem`, `DyeColor`, and `ShulkerBoxBlock` imports and replace the constructor call with:

```java
cir.setReturnValue(Optional.of(new ShulkerTooltipComponent(contents)));
```

Keep the `ItemTags.SHULKER_BOXES` guard and `getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)` unchanged.

- [ ] **Step 4: Run focused and full tests**

Run:

```sh
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle test --no-daemon --console=plain --tests dev.quirky.client.tooltips.ClientShulkerTooltipComponentTest
```

Then run the full test suite with the same Java/Gradle environment. Expected: PASS.

### Task 4: Final build and review checkpoint

**Files:**
- No additional source files.

- [ ] **Step 1: Inspect the diff and check the scope**

Run:

```sh
git diff master...HEAD --stat
git status --short --branch
```

Confirm only the approved design/plan documents and the four implementation/test files changed; no resources or unrelated tooltip code changed.

- [ ] **Step 2: Run the required build**

Run:

```sh
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle build --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit the implementation**

```sh
git add src/client/java/dev/quirky/client/tooltips/ClientShulkerTooltipComponent.java \
  src/main/java/dev/quirky/tooltips/ShulkerTooltipComponent.java \
  src/main/java/dev/quirky/mixin/TooltipDetailsMixin.java \
  src/test/java/dev/quirky/client/tooltips/ClientShulkerTooltipComponentTest.java
git commit -m "fix: use vanilla slots for shulker tooltip"
```
