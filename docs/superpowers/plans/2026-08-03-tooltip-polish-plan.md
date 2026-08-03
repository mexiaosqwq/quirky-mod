# Item Tooltip Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the food tooltip into a compact, data-driven information panel and polish attribute, shulker and map previews without duplicating vanilla component details.

**Architecture:** Extend the existing common `FoodTooltipComponent` with the client-visible `Consumable` data already present on the item stack. The client renderer will derive conditional rows from known vanilla consume-effect types, reuse the existing tooltip row metrics and vanilla effect sprites, and report dynamic width/height. The other tooltip renderers will receive focused layout changes only; their existing callbacks, mixins and configuration keys remain in place.

**Tech Stack:** Fabric tooltip component callback, Minecraft 26.2 official mappings, `FoodProperties`/`Consumable` data components, vanilla `MobEffect`/`Hud` sprites, Java 25, Gradle, JUnit 5 and Mockito.

## Global Constraints

- Keep the existing `foodTooltip`, `attributeTooltip`, `shulkerTooltip` and `mapPreview` config keys; do not add a tooltip verbosity setting.
- Use Java 25, official 26.2 mappings, tabs in Java, and verify changed Minecraft APIs against `$HOME/.cache/mcsrc`.
- Preserve `ClientTooltipComponentCallback` registration in `QuirkyModClient`; do not introduce a second callback or a custom network channel.
- Food details must be conditional: ordinary food remains compact, default consume time is not printed, and empty effect sections are omitted.
- Use vanilla translation/effect names and sprites where available; do not duplicate vanilla fireworks, potion, enchantment or suspicious-stew text.
- Respect `TooltipDisplay.hideTooltip()` and hidden `FOOD` component state before returning a custom food image.
- Keep the existing Shift behavior for attribute tooltips and the `player == null` search-index guard in `AttributeTextHideMixin`.
- Every dynamic tooltip component must keep its drawing inside its reported width/height.
- Do not modify files outside this plan and the paired offhand plan except the already-approved README/language updates.

## Verified File Map

- Modify `src/main/java/dev/quirky/tooltips/FoodTooltipComponent.java`: carry optional `Consumable` data while preserving the one-argument constructor.
- Modify `src/main/java/dev/quirky/mixin/TooltipDetailsMixin.java`: pass consumable data, honor tooltip display visibility, and avoid later injections overwriting an already-selected tooltip image.
- Modify `src/client/java/dev/quirky/client/tooltips/ClientFoodTooltipComponent.java`: render conditional food rows and known consume effects with dynamic geometry.
- Modify `src/client/java/dev/quirky/client/tooltips/ClientAttributeTooltipComponent.java`: wrap attribute cells into compact rows while preserving Shift hiding.
- Modify `src/client/java/dev/quirky/client/tooltips/ClientShulkerTooltipComponent.java`: label an empty 9×3 preview without changing real slot positions.
- Modify `src/client/java/dev/quirky/client/tooltips/ClientMapTooltipComponent.java`: append map scale/lock metadata only when map data exists and keep bounds correct.
- Modify `src/test/java/dev/quirky/client/tooltips/ClientFoodTooltipComponentTest.java`: cover conditional food rows, effect summaries and locale-stable formatting.
- Create `src/test/java/dev/quirky/tooltips/FoodTooltipComponentTest.java`: cover the component constructor and optional consumable payload.
- Modify `src/test/java/dev/quirky/client/tooltips/ClientAttributeTooltipComponentTest.java`: cover row wrapping and multi-row height.
- Modify `src/test/java/dev/quirky/client/tooltips/ClientShulkerTooltipComponentTest.java`: cover the empty-state label without changing slot geometry.
- Modify `src/test/java/dev/quirky/client/tooltips/ClientMapTooltipComponentTest.java`: cover base dimensions and metadata dimension behavior.
- Modify `src/main/resources/assets/quirky/lang/zh_cn.json` and `src/main/resources/assets/quirky/lang/en_us.json`: add only the new conditional food, map and empty-shulker strings and refresh stale tooltip descriptions.
- Modify `README.md`: describe the richer food tooltip and map metadata without claiming unsupported behavior.

---

### Task 1: Carry food consumption data and honor tooltip visibility

**Files:**
- Modify: `src/main/java/dev/quirky/tooltips/FoodTooltipComponent.java`
- Modify: `src/main/java/dev/quirky/mixin/TooltipDetailsMixin.java`
- Create: `src/test/java/dev/quirky/tooltips/FoodTooltipComponentTest.java`

**Interfaces:**
- Existing consumer: `ClientFoodTooltipComponent(FoodTooltipComponent)`.
- Produced component shape: `FoodTooltipComponent(FoodProperties food, @Nullable Consumable consumable)` plus a compatibility constructor `FoodTooltipComponent(FoodProperties food)` that stores `null` consumable data.
- Existing mixin entry point: `Item.getTooltipImage(ItemStack)` through `TooltipDetailsMixin.quirky$foodTooltip`.

- [ ] **Step 1: Write the component regression tests**

Create tests that assert the old constructor remains valid and the new constructor retains the optional consumable:

```java
@Test
void oneArgumentConstructorKeepsBaseFoodData() {
	FoodProperties food = new FoodProperties(6, 9.6F, false);
	FoodTooltipComponent component = new FoodTooltipComponent(food);

	assertEquals(food, component.food());
	assertNull(component.consumable());
}

@Test
void twoArgumentConstructorRetainsConsumable() {
	FoodProperties food = new FoodProperties(4, 6.0F, true);
	Consumable consumable = Consumables.defaultFood().consumeSeconds(0.8F).build();
	FoodTooltipComponent component = new FoodTooltipComponent(food, consumable);

	assertEquals(0.8F, component.consumable().consumeSeconds());
	assertTrue(component.food().canAlwaysEat());
}
```

- [ ] **Step 2: Run the new test and verify it fails**

```sh
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle test --no-daemon --console=plain --tests dev.quirky.tooltips.FoodTooltipComponentTest
```

Expected: FAIL because `FoodTooltipComponent` currently has only the `FoodProperties` record field and no `consumable()` accessor.

- [ ] **Step 3: Extend the common component without moving client code into common code**

Change the record to store `FoodProperties food` and `@Nullable Consumable consumable`, import the existing `org.jspecify.annotations.Nullable`, and add the compatibility constructor:

```java
public record FoodTooltipComponent(FoodProperties food, @Nullable Consumable consumable)
	implements TooltipComponent {
	public FoodTooltipComponent(FoodProperties food) {
		this(food, null);
	}
}
```

- [ ] **Step 4: Pass `CONSUMABLE` from `TooltipDetailsMixin` safely**

In `quirky$foodTooltip`, keep the `foodTooltip` config guard, then read `TooltipDisplay` and return early unless `display.shows(DataComponents.FOOD)` is true. When `FOOD` exists, construct:

```java
new FoodTooltipComponent(
	food,
	stack.get(DataComponents.CONSUMABLE)
)
```

At the beginning of each cancellable branch in `TooltipDetailsMixin`, return if `cir.isCancelled()` so a previously selected shulker/attribute/food image cannot be overwritten by a later branch for a custom item carrying multiple components. Keep the existing map branch separate and verify its injection priority/behavior against `MapTooltipMixin` during the runtime audit.

- [ ] **Step 5: Run common tooltip tests**

```sh
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle test --no-daemon --console=plain \
  --tests dev.quirky.tooltips.FoodTooltipComponentTest \
  --tests dev.quirky.client.tooltips.ClientFoodTooltipComponentTest
```

Expected: the new component tests pass; the existing client tests remain source-compatible through the one-argument constructor.

- [ ] **Step 6: Commit the common data slice**

```sh
git add src/main/java/dev/quirky/tooltips/FoodTooltipComponent.java \
  src/main/java/dev/quirky/mixin/TooltipDetailsMixin.java \
  src/test/java/dev/quirky/tooltips/FoodTooltipComponentTest.java
git commit -m "feat: expose food consumption data to tooltips"
```

---

### Task 2: Render conditional food facts and consume effects

**Files:**
- Modify: `src/client/java/dev/quirky/client/tooltips/ClientFoodTooltipComponent.java`
- Modify: `src/test/java/dev/quirky/client/tooltips/ClientFoodTooltipComponentTest.java`

**Interfaces:**
- Consumes: `FoodTooltipComponent.food()` and nullable `FoodTooltipComponent.consumable()`.
- Produces: the existing `ClientTooltipComponent` implementation with package-private static `detailLines(FoodTooltipComponent, float tickRate)` and a nested immutable line value containing an optional icon, `Component` text and color.
- Reuses: `TooltipRowMetrics`, `Hud.getMobEffectSprite(Holder<MobEffect>)`, `MobEffectUtil.formatDuration`, and existing GUI text/sprite extraction calls.

- [ ] **Step 1: Add failing renderer tests for conditional rows**

Extend `ClientFoodTooltipComponentTest` with fixtures for ordinary food, always-edible food, non-default consume time, and a status effect with probability:

```java
@Test
void ordinaryFoodStaysAtTheBaseRow() {
	FoodProperties food = new FoodProperties(4, 2.4F, false);
	FoodTooltipComponent component = new FoodTooltipComponent(food, Consumables.defaultFood().build());
	ClientFoodTooltipComponent client = new ClientFoodTooltipComponent(component);

	assertEquals(16, client.getHeight(mock(Font.class)));
}

@Test
void conditionalFactsAddRowsOnlyWhenPresent() {
	FoodProperties food = new FoodProperties(4, 6.0F, true);
	Consumable consumable = Consumable.builder().consumeSeconds(0.8F).build();
	FoodTooltipComponent component = new FoodTooltipComponent(food, consumable);
	ClientFoodTooltipComponent client = new ClientFoodTooltipComponent(component);

	assertEquals(2, ClientFoodTooltipComponent.detailLines(component, 20.0F).size());
	assertEquals(48, client.getHeight(mock(Font.class)));
}
```

Use a `Consumable` containing `ApplyStatusEffectsConsumeEffect` for a separate test and assert that the returned detail line text contains the localized effect name, duration and a probability only when probability is below 1.0. Add tests for `ClearAllStatusEffectsConsumeEffect` and `TeleportRandomlyConsumeEffect` to ensure special behavior produces a single readable line rather than an empty placeholder.

- [ ] **Step 2: Run the food renderer tests and verify they fail**

```sh
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle test --no-daemon --console=plain --tests dev.quirky.client.tooltips.ClientFoodTooltipComponentTest
```

Expected: FAIL because the current renderer has no static `detailLines` method and always reports one row.

- [ ] **Step 3: Implement the base/detail layout**

Keep the existing base row geometry and add a conditional detail list. The renderer must:

- Always render the nutrition/saturation base row.
- Add a line for `food.canAlwaysEat()` only when true.
- Add a consume-time line only when the consumable duration differs from `Consumable.DEFAULT_CONSUME_SECONDS`; use “eat” for `ItemUseAnimation.EAT` and “drink” for non-eat animations.
- Use `Locale.ROOT` for decimal seconds and saturation formatting.
- Return `LINE_HEIGHT * (1 + detailLines.size())` from `getHeight`.
- Return the maximum of the base row width and all detail row widths from `getWidth`.
- Draw each detail row at `y + rowIndex * LINE_HEIGHT`; draw an effect sprite when the line has one, otherwise begin text at the row origin.

Use translatable keys for the conditional labels and `Component` text for effect names. Do not print default duration, an empty effects label, or sound/particle implementation details.

- [ ] **Step 4: Implement known vanilla consume-effect summaries**

Inspect `Consumable.onConsumeEffects()` and handle these concrete classes:

- `ApplyStatusEffectsConsumeEffect`: one line per `MobEffectInstance`, using `MobEffect.getDisplayName()`, amplifier suffix, `MobEffectUtil.formatDuration`, and a percentage suffix only when probability is below 100%.
- `RemoveStatusEffectsConsumeEffect`: one line per removed holder, using the holder’s effect display name.
- `ClearAllStatusEffectsConsumeEffect`: one translated “clear all effects” line.
- `TeleportRandomlyConsumeEffect`: one translated “random teleport” line.
- `PlaySoundConsumeEffect`: omit it from the visual summary.

Use `Hud.getMobEffectSprite` for status effect rows and use the effect’s beneficial flag to select a positive or negative color. If `Minecraft.getInstance().level` is unavailable, use the normal 20-tick rate for deterministic unit tests; in a live client use the current level tick rate when formatting durations.

Do not inspect `SUSPICIOUS_STEW_EFFECTS` in this renderer; vanilla already owns its creative-only text path and adding it here would duplicate information.

- [ ] **Step 5: Verify food renderer behavior**

```sh
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle test --no-daemon --console=plain --tests dev.quirky.client.tooltips.ClientFoodTooltipComponentTest
```

Expected: PASS for ordinary one-row food, always-edible/fast food conditional rows, integral and fractional saturation, status effects, probability, clear effects, random teleport, and locale-stable formatting.

- [ ] **Step 6: Commit the food renderer slice**

```sh
git add src/client/java/dev/quirky/client/tooltips/ClientFoodTooltipComponent.java \
  src/test/java/dev/quirky/client/tooltips/ClientFoodTooltipComponentTest.java
git commit -m "feat: show conditional food consumption details"
```

---

### Task 3: Wrap dense attribute rows without changing Shift semantics

**Files:**
- Modify: `src/client/java/dev/quirky/client/tooltips/ClientAttributeTooltipComponent.java`
- Modify: `src/test/java/dev/quirky/client/tooltips/ClientAttributeTooltipComponentTest.java`

**Interfaces:**
- Consumes: `AttributeTooltipComponent.lines()` and `AttributeTooltipVisibility.shiftHidesCompactRow(Minecraft)`.
- Produces: the same `ClientTooltipComponent` with row grouping internal to the renderer; no change to `AttributeTooltipComponent` or `AttributeLineCollector` signatures.

- [ ] **Step 1: Add a failing wrap test**

Add a test with enough long cells to exceed the renderer’s explicit 128-pixel compact row width and assert that height becomes two rows while a short two-cell item remains one row. Keep the existing empty and width-sum tests; the existing short fixtures must remain one row.

- [ ] **Step 2: Run the attribute tests and verify the wrap test fails**

```sh
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle test --no-daemon --console=plain --tests dev.quirky.client.tooltips.ClientAttributeTooltipComponentTest
```

Expected: FAIL because all current lines are forced into one row.

- [ ] **Step 3: Implement deterministic row grouping**

Define `MAX_ROW_WIDTH = 128` beside the existing icon-size constants. Use the current icon/text/cell gap formula and group lines in source order; before adding a cell, start a new row when the current row is non-empty and the next cell would exceed 128 pixels. Compute `getWidth` as the widest row and `getHeight` as the number of rows multiplied by `TooltipRowMetrics.LINE_HEIGHT`.

In `extractImage`, return immediately when Shift hides the compact component, then draw each row at its row offset. Keep icon and text vertical alignment through `TooltipRowMetrics.iconY` and `TooltipRowMetrics.textY`. Do not change `AttributeTooltipVisibility` or `AttributeTextHideMixin`; the vanilla text must remain visible while Shift is held and searchable when `player == null`.

- [ ] **Step 4: Run attribute and visibility regressions**

```sh
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle test --no-daemon --console=plain \
  --tests dev.quirky.client.tooltips.ClientAttributeTooltipComponentTest \
  --tests dev.quirky.client.tooltips.AttributeTooltipVisibilityTest \
  --tests dev.quirky.tooltips.AttributeLineCollectorTest
```

Expected: PASS, including the existing six-attribute collection and Shift visibility behavior.

- [ ] **Step 5: Commit the attribute slice**

```sh
git add src/client/java/dev/quirky/client/tooltips/ClientAttributeTooltipComponent.java \
  src/test/java/dev/quirky/client/tooltips/ClientAttributeTooltipComponentTest.java
git commit -m "fix: wrap dense attribute tooltip rows"
```

---

### Task 4: Add empty shulker state and map metadata within bounds

**Files:**
- Modify: `src/client/java/dev/quirky/client/tooltips/ClientShulkerTooltipComponent.java`
- Modify: `src/client/java/dev/quirky/client/tooltips/ClientMapTooltipComponent.java`
- Modify: `src/test/java/dev/quirky/client/tooltips/ClientShulkerTooltipComponentTest.java`
- Modify: `src/test/java/dev/quirky/client/tooltips/ClientMapTooltipComponentTest.java`

**Interfaces:**
- Consumes: `ShulkerTooltipComponent.contents()` through the existing copied `NonNullList`, `Minecraft.level.getMapData(MapId)`, and `MapItemSavedData.scale`/`locked`.
- Produces: unchanged component constructors with improved rendering and accurate dimensions.

- [ ] **Step 1: Add failing visual-state tests**

For the shulker renderer, capture `graphics.text` and assert that an empty component emits the translated empty-state label while preserving the existing 27 slot fills and border lines. Add a non-empty fixture and assert it does not emit the empty label.

For the map renderer, add a package-private `metadataText(byte scale, boolean locked)` formatter test with known scale and locked values. Keep the existing 71-by-71 base assertion for the no-client/no-map-data fallback.

- [ ] **Step 2: Run the focused tests and verify the new assertions fail**

```sh
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle test --no-daemon --console=plain \
  --tests dev.quirky.client.tooltips.ClientShulkerTooltipComponentTest \
  --tests dev.quirky.client.tooltips.ClientMapTooltipComponentTest
```

Expected: FAIL because empty shulkers have no label and maps expose no metadata formatter/dynamic footer.

- [ ] **Step 3: Render the shulker empty state without changing the grid**

Add an `items.stream().allMatch(ItemStack::isEmpty)` check. Keep the existing background, 27 slot backing fills, borders, item icons and decorations. For an empty box, draw `Component.translatable("tooltip.quirky.shulker.empty")` centered inside the existing bounds using the existing `GuiGraphicsExtractor.text` overload. Do not change the 9×3 slot geometry or the `getWidth`/`getHeight` values.

- [ ] **Step 4: Render map scale/lock metadata with a shared data lookup**

Keep the existing parchment/map transform anchored at `(x, y)`. Add a helper that reads `MapItemSavedData` from the current client level and returns null when the level or map data is absent. Add package-private `metadataText(byte scale, boolean locked)` that formats the ratio as `1 << scale` and appends a locked marker only when `locked` is true. Make `getWidth`, `getHeight` and `extractImage` use the same data-presence decision and metadata width so the footer never renders outside the reported bounds. Keep the no-data component at the existing base dimensions.

- [ ] **Step 5: Run the focused tooltip tests**

```sh
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle test --no-daemon --console=plain \
  --tests dev.quirky.client.tooltips.ClientShulkerTooltipComponentTest \
  --tests dev.quirky.client.tooltips.ClientMapTooltipComponentTest
```

Expected: PASS. If the shell path above is not present in the worktree environment, rerun with the project-standard `/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin` path rather than changing Gradle configuration.

- [ ] **Step 6: Commit the container/map slice**

```sh
git add src/client/java/dev/quirky/client/tooltips/ClientShulkerTooltipComponent.java \
  src/client/java/dev/quirky/client/tooltips/ClientMapTooltipComponent.java \
  src/test/java/dev/quirky/client/tooltips/ClientShulkerTooltipComponentTest.java \
  src/test/java/dev/quirky/client/tooltips/ClientMapTooltipComponentTest.java
git commit -m "feat: clarify container and map tooltip previews"
```

---

### Task 5: Update translations and user-facing documentation

**Files:**
- Modify: `src/main/resources/assets/quirky/lang/zh_cn.json`
- Modify: `src/main/resources/assets/quirky/lang/en_us.json`
- Modify: `README.md`

**Interfaces:**
- Consumes: new translation keys referenced by `ClientFoodTooltipComponent`, `ClientShulkerTooltipComponent` and `ClientMapTooltipComponent`.
- Produces: complete Chinese/English text for conditional food facts, effect actions, empty shulker state, map ratio and lock marker.

- [ ] **Step 1: Add and review translation keys**

Add keys for:

- always-edible food
- non-default eat/drink duration
- random teleport
- clear-all-effects
- remove-effect summary
- empty shulker
- map scale
- map locked marker

Use vanilla effect display names for actual effects; do not hardcode vanilla effect names into Quirky language files. Keep existing config keys and update stale food/map descriptions to match the new behavior.

- [ ] **Step 2: Update README feature descriptions**

Expand the food tooltip bullet to mention conditional consumption details and effects, the attribute bullet to mention dense-row wrapping, the shulker bullet to mention empty-state clarity, and the map bullet to mention scale/lock metadata while keeping the parchment preview statement accurate.

- [ ] **Step 3: Validate resources**

```sh
python3 -m json.tool src/main/resources/assets/quirky/lang/zh_cn.json >/dev/null
python3 -m json.tool src/main/resources/assets/quirky/lang/en_us.json >/dev/null
git diff --check
```

Expected: both language files parse and no whitespace errors are reported.

- [ ] **Step 4: Commit text changes**

```sh
git add src/main/resources/assets/quirky/lang/zh_cn.json \
  src/main/resources/assets/quirky/lang/en_us.json README.md
git commit -m "docs: describe richer item tooltip information"
```

---

### Task 6: Run the integrated tooltip verification and runtime audit

**Files:**
- No new source files; verify all files changed by Tasks 1–5.

- [ ] **Step 1: Run all tooltip-focused tests**

```sh
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle test --no-daemon --console=plain \
  --tests dev.quirky.tooltips.FoodTooltipComponentTest \
  --tests dev.quirky.client.tooltips.ClientFoodTooltipComponentTest \
  --tests dev.quirky.client.tooltips.ClientAttributeTooltipComponentTest \
  --tests dev.quirky.client.tooltips.AttributeTooltipVisibilityTest \
  --tests dev.quirky.tooltips.AttributeLineCollectorTest \
  --tests dev.quirky.client.tooltips.ClientShulkerTooltipComponentTest \
  --tests dev.quirky.client.tooltips.ClientMapTooltipComponentTest
```

Expected: PASS.

- [ ] **Step 2: Run the complete build**

```sh
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle build --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Audit changed Mixin and callback behavior**

Run the `quirky-mixin-runtime-audit` checklist against `TooltipDetailsMixin`, `AttributeTextHideMixin`, and any changed `MapTooltipMixin` interaction. Verify `Item.getTooltipImage` and `ItemStack.addAttributeTooltips` signatures against `$HOME/.cache/mcsrc`, confirm no parent-class shadow/invoker issue was introduced, and confirm that `player == null` still bypasses attribute text hiding.

- [ ] **Step 4: Perform desktop-client visual verification**

Check ordinary food, golden apple, chicken/rotten flesh or pufferfish probability effects, honey bottle, chorus fruit, and a custom food with multiple effects. Confirm ordinary food remains compact, conditional rows are readable, icons are not clipped, and harmful/beneficial colors are distinguishable. Check a six-attribute item, Shift fallback, empty/colored/non-empty shulkers, maps with different scales and lock states, and the clock tooltip. Verify every custom image remains within its reported bounds and no vanilla component is duplicated.

- [ ] **Step 5: Record final status**

```sh
git status --short --branch
git log -8 --oneline
```

Expected: only the intended tooltip/offhand commits and the approved design/plan documents are present; no build artifacts or unrelated files are staged.
