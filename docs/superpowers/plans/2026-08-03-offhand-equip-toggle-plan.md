# Offhand Equipment and Toggle Isolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add wind charges and firework rockets to the offhand quick-equip path while making `equipSwap` and `offhandSwap` independent switches in the same configuration group.

**Architecture:** Keep the existing shared `OffhandSwapItems` predicate and `EquipSwapPayload` transport. The client will decide whether a click belongs to the armor/equippable path or the dedicated offhand path, while the server will re-evaluate the same decision from the authoritative source stack and the two config fields. No new packet or public API is needed.

**Tech Stack:** Fabric API for screen mouse events and play payloads, Minecraft 26.2 official mappings, Cloth Config/AutoConfig, Java 25, Gradle, JUnit 5 and Mockito.

## Global Constraints

- Keep mod id `quirky`, base package `dev.quirky`, and existing Fabric/Minecraft 26.2 versions.
- Use Java 25 and verify APIs against `$HOME/.cache/mcsrc` before implementation.
- Keep tabs in Java and use the existing JSON formatting in language resources.
- `equipSwap` and `offhandSwap` remain separate persisted config keys and both stay in `@ConfigEntry.Category("toggles")`.
- The server remains authoritative; a client-side check may only decide whether to send the existing payload and consume the click.
- Preserve source-slot replacement, fake-slot rejection, carried-stack rejection, container-id validation, and armor-change enchantment checks.
- Do not modify files outside this plan and the paired tooltip plan except the already-approved README/language updates.

## Verified File Map

- Modify `src/main/java/dev/quirky/config/QuirkyConfig.java`: keep the two toggle fields in the same category and place them adjacent.
- Modify `src/main/java/dev/quirky/equip_swap/OffhandSwapItems.java`: extend the shared dedicated-offhand item predicate.
- Modify `src/main/java/dev/quirky/equip_swap/EquipSwapServer.java`: remove the global `equipSwap` gate and apply the relevant config gate inside the authoritative swap decision.
- Modify `src/client/java/dev/quirky/client/equip_swap/EquipSwapClient.java`: gate click interception by the relevant config field before cancelling the vanilla mouse event.
- Modify `src/test/java/dev/quirky/equip_swap/EquipSwapServerTest.java`: cover the new item set and independent server decisions.
- Modify `src/test/java/dev/quirky/client/equip_swap/EquipSwapClientTest.java`: cover the client interception matrix without booting a screen.
- Modify `src/test/java/dev/quirky/config/QuirkyConfigDefaultsTest.java`: assert both persisted toggles default to enabled.
- Create `src/test/java/dev/quirky/equip_swap/OffhandSwapItemsTest.java`: unit-test the shared four-item predicate.
- Modify `src/main/resources/assets/quirky/lang/zh_cn.json` and `src/main/resources/assets/quirky/lang/en_us.json`: describe all four supported offhand item types.
- Modify `README.md`: update the user-facing offhand feature description.

---

### Task 1: Define the item set and configuration grouping

**Files:**
- Modify: `src/main/java/dev/quirky/config/QuirkyConfig.java`
- Modify: `src/main/java/dev/quirky/equip_swap/OffhandSwapItems.java`
- Modify: `src/test/java/dev/quirky/config/QuirkyConfigDefaultsTest.java`
- Create: `src/test/java/dev/quirky/equip_swap/OffhandSwapItemsTest.java`

**Interfaces:**
- Existing consumer: `OffhandSwapItems.isOffhandSwapItem(ItemStack): boolean` from both client and server paths.
- Existing config fields: `QuirkyConfig.equipSwap` and `QuirkyConfig.offhandSwap`.
- Produced behavior: the shared predicate returns true for `Items.SHIELD`, `Items.TORCH`, `Items.WIND_CHARGE`, and `Items.FIREWORK_ROCKET`, and false for unrelated stacks.

- [ ] **Step 1: Add failing predicate tests**

Add a test bootstrap binding for `Items.WIND_CHARGE` and `Items.FIREWORK_ROCKET`, then add explicit assertions:

```java
@Test
void acceptsAllDedicatedOffhandItems() {
	assertTrue(OffhandSwapItems.isOffhandSwapItem(new ItemStack(Items.SHIELD)));
	assertTrue(OffhandSwapItems.isOffhandSwapItem(new ItemStack(Items.TORCH)));
	assertTrue(OffhandSwapItems.isOffhandSwapItem(new ItemStack(Items.WIND_CHARGE)));
	assertTrue(OffhandSwapItems.isOffhandSwapItem(new ItemStack(Items.FIREWORK_ROCKET)));
}

@Test
void rejectsUnrelatedItems() {
	assertFalse(OffhandSwapItems.isOffhandSwapItem(new ItemStack(Items.STONE)));
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```sh
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle test --no-daemon --console=plain --tests dev.quirky.equip_swap.OffhandSwapItemsTest
```

Expected: FAIL because the current predicate only accepts shield and torch.

- [ ] **Step 3: Implement the smallest predicate/config change**

Extend the existing return expression without introducing a registry or tag abstraction:

```java
return stack.is(Items.SHIELD)
	|| stack.is(Items.TORCH)
	|| stack.is(Items.WIND_CHARGE)
	|| stack.is(Items.FIREWORK_ROCKET);
```

Move `offhandSwap` directly after `equipSwap` in `QuirkyConfig`; keep both annotations as `@ConfigEntry.Category("toggles")` and keep their field names unchanged. Update the config-default test to assert `c.equipSwap && c.offhandSwap` explicitly.

- [ ] **Step 4: Run the focused tests and verify they pass**

Run the predicate and config tests together:

```sh
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle test --no-daemon --console=plain \
  --tests dev.quirky.equip_swap.OffhandSwapItemsTest \
  --tests dev.quirky.config.QuirkyConfigDefaultsTest
```

Expected: PASS.

- [ ] **Step 5: Commit the item-set slice**

```sh
git add src/main/java/dev/quirky/config/QuirkyConfig.java \
  src/main/java/dev/quirky/equip_swap/OffhandSwapItems.java \
  src/test/java/dev/quirky/config/QuirkyConfigDefaultsTest.java \
  src/test/java/dev/quirky/equip_swap/OffhandSwapItemsTest.java
git commit -m "feat: expand dedicated offhand item set"
```

---

### Task 2: Make server-side decisions independent and authoritative

**Files:**
- Modify: `src/main/java/dev/quirky/equip_swap/EquipSwapServer.java`
- Modify: `src/test/java/dev/quirky/equip_swap/EquipSwapServerTest.java`

**Interfaces:**
- Consumes: `QuirkyConfigHolder.get()`, `OffhandSwapItems.isOffhandSwapItem(ItemStack)`, `EquipSwapServer.trySwap(ServerPlayer, int, int)`.
- Produces: a payload handler that schedules `trySwap` regardless of one global toggle; `trySwap` accepts a dedicated offhand item only when `offhandSwap` is enabled and accepts a normal equippable item only when `equipSwap` is enabled.

- [ ] **Step 1: Add failing independent-toggle server tests**

Extend the existing `EquipSwapServerTest` setup to bind wind charges and firework rockets. Add tests with `QuirkyConfigHolder.set(new QuirkyConfig())` and a `finally` reset:

```java
@Test
void dedicatedOffhandSwapWorksWhenEquipSwapIsDisabled() {
	QuirkyConfig config = new QuirkyConfig();
	config.equipSwap = false;
	config.offhandSwap = true;
	QuirkyConfigHolder.set(config);
	try {
		ServerPlayer player = creativePlayer();
		ItemStack windCharge = new ItemStack(Items.WIND_CHARGE, 3);
		player.getInventory().setItem(9, windCharge);

		assertTrue(EquipSwapServer.trySwap(player, 0, 9));
		assertEquals(windCharge, player.getInventory().getItem(40));
		assertTrue(player.getInventory().getItem(9).isEmpty());
	} finally {
		QuirkyConfigHolder.set(new QuirkyConfig());
	}
}

@Test
void normalEquipmentIsRejectedWhenEquipSwapIsDisabled() {
	QuirkyConfig config = new QuirkyConfig();
	config.equipSwap = false;
	config.offhandSwap = true;
	QuirkyConfigHolder.set(config);
	try {
		ServerPlayer player = creativePlayer();
		ItemStack chestplate = new ItemStack(Items.IRON_CHESTPLATE);
		player.getInventory().setItem(0, chestplate);
		when(player.getEquipmentSlotForItem(chestplate)).thenReturn(EquipmentSlot.CHEST);
		when(player.isEquippableInSlot(chestplate, EquipmentSlot.CHEST)).thenReturn(true);

		assertFalse(EquipSwapServer.trySwap(player, 0, 36));
		assertEquals(chestplate, player.getInventory().getItem(0));
	} finally {
		QuirkyConfigHolder.set(new QuirkyConfig());
	}
}
```

Add equivalent coverage for firework rockets and for dedicated offhand items being rejected when `offhandSwap` is false. Keep the existing shield fallback test to prove an equippable shield still follows the normal path when only `equipSwap` is enabled.

- [ ] **Step 2: Run the focused server tests and verify they fail**

```sh
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle test --no-daemon --console=plain --tests dev.quirky.equip_swap.EquipSwapServerTest
```

Expected: `normalEquipmentIsRejectedWhenEquipSwapIsDisabled` fails because the direct `trySwap` path currently accepts any stack with `EQUIPPABLE` even when `equipSwap` is false. The payload-handler global gate is separately removed in Step 3 so dedicated offhand payloads are no longer blocked by `equipSwap`.

- [ ] **Step 3: Implement the server decision matrix**

In `EquipSwapServer.handle`, remove the early return based solely on `equipSwap`; schedule the existing `trySwap` call for every payload. In `trySwap`, read the config once after confirming the source stack is non-empty and distinguish the paths:

```java
QuirkyConfig config = QuirkyConfigHolder.get();
boolean offhandItem = OffhandSwapItems.isOffhandSwapItem(stack) && config.offhandSwap;
if (!offhandItem && (!config.equipSwap || !stack.has(DataComponents.EQUIPPABLE))) {
	return false;
}
```

Keep the existing offhand target selection, equippable-slot validation, `PREVENT_ARMOR_CHANGE` check, source `mayPlace` check, target/source swap, and `broadcastChanges()` unchanged. The new guard must be the only behavior change in this method apart from removing the handler’s global gate. Do not allow wind charges or firework rockets through the ordinary equipment path when `offhandSwap` is disabled because they have no `EQUIPPABLE` component.

- [ ] **Step 4: Run the entire server equipment test class**

```sh
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle test --no-daemon --console=plain --tests dev.quirky.equip_swap.EquipSwapServerTest
```

Expected: PASS for the existing armor, shield, torch, replacement, fake-slot, carried-stack, and config-disabled cases plus the new matrix cases.

- [ ] **Step 5: Commit the server slice**

```sh
git add src/main/java/dev/quirky/equip_swap/EquipSwapServer.java \
  src/test/java/dev/quirky/equip_swap/EquipSwapServerTest.java
git commit -m "fix: isolate offhand and equipment swap toggles"
```

---

### Task 3: Stop disabled client paths from swallowing clicks

**Files:**
- Modify: `src/client/java/dev/quirky/client/equip_swap/EquipSwapClient.java`
- Modify: `src/test/java/dev/quirky/client/equip_swap/EquipSwapClientTest.java`

**Interfaces:**
- Consumes: `ItemStack`, `QuirkyConfig`, and `OffhandSwapItems.isOffhandSwapItem(ItemStack)`.
- Produces: package-private `EquipSwapClient.isQuickEquipEnabled(ItemStack, QuirkyConfig): boolean`, used by the screen mouse callback and directly testable without a live screen.

- [ ] **Step 1: Add the client decision-matrix tests**

Add tests for the exact four combinations:

```java
@Test
void dedicatedOffhandItemsDoNotDependOnEquipSwap() {
	QuirkyConfig config = new QuirkyConfig();
	config.equipSwap = false;
	config.offhandSwap = true;
	assertTrue(EquipSwapClient.isQuickEquipEnabled(new ItemStack(Items.WIND_CHARGE), config));
}

@Test
void ordinaryEquipmentDoesNotDependOnOffhandSwap() {
	QuirkyConfig config = new QuirkyConfig();
	config.equipSwap = true;
	config.offhandSwap = false;
	ItemStack chestplate = new ItemStack(Items.IRON_CHESTPLATE);
	assertTrue(EquipSwapClient.isQuickEquipEnabled(chestplate, config));
}

@Test
void disabledFeaturesDoNotInterceptTheirItems() {
	QuirkyConfig config = new QuirkyConfig();
	config.equipSwap = false;
	config.offhandSwap = false;
	assertFalse(EquipSwapClient.isQuickEquipEnabled(new ItemStack(Items.IRON_CHESTPLATE), config));
	assertFalse(EquipSwapClient.isQuickEquipEnabled(new ItemStack(Items.FIREWORK_ROCKET), config));
}
```

- [ ] **Step 2: Run the client equipment tests and verify they fail**

```sh
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle test --no-daemon --console=plain --tests dev.quirky.client.equip_swap.EquipSwapClientTest
```

Expected: FAIL because `EquipSwapClient` has no independent config helper and currently accepts any equippable stack without checking `equipSwap`.

- [ ] **Step 3: Implement and use the client helper**

Add the package-private helper:

```java
static boolean isQuickEquipEnabled(ItemStack stack, QuirkyConfig config) {
	return (stack.has(DataComponents.EQUIPPABLE) && config.equipSwap)
		|| (OffhandSwapItems.isOffhandSwapItem(stack) && config.offhandSwap);
}
```

Use it in the mouse callback before calculating the server slot index and before returning `false`. Leave the existing hovered-slot, carried-stack and creative-slot mapping checks in their current order. This ensures a disabled feature returns `true` from `allowMouseClick` and lets vanilla handle the click.

- [ ] **Step 4: Run both client tests and the server regression class**

```sh
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle test --no-daemon --console=plain \
  --tests dev.quirky.client.equip_swap.EquipSwapClientTest \
  --tests dev.quirky.equip_swap.EquipSwapServerTest
```

Expected: PASS.

- [ ] **Step 5: Commit the client slice**

```sh
git add src/client/java/dev/quirky/client/equip_swap/EquipSwapClient.java \
  src/test/java/dev/quirky/client/equip_swap/EquipSwapClientTest.java
git commit -m "fix: restore vanilla clicks when quick equip is disabled"
```

---

### Task 4: Update user-facing descriptions

**Files:**
- Modify: `src/main/resources/assets/quirky/lang/zh_cn.json`
- Modify: `src/main/resources/assets/quirky/lang/en_us.json`
- Modify: `README.md`

**Interfaces:**
- Consumes: the stable config keys `text.autoconfig.quirky.option.equipSwap` and `text.autoconfig.quirky.option.offhandSwap`.
- Produces: descriptions that match the four-item dedicated offhand set and the independent toggle semantics.

- [ ] **Step 1: Update both language files**

Keep the existing keys and replace only the stale descriptions. The offhand tooltip must name shield, torch, wind charge and firework rocket in both languages. The quick-equip tooltip must state that ordinary equipment and the dedicated offhand path are separate options, without claiming that one toggle controls the other.

- [ ] **Step 2: Update the README feature bullets**

Change the offhand bullet from “shield or torch” to the complete supported set. Keep the statement that the feature works in container screens and creative inventory.

- [ ] **Step 3: Verify resource and documentation diffs**

Run:

```sh
python3 -m json.tool src/main/resources/assets/quirky/lang/zh_cn.json >/dev/null
python3 -m json.tool src/main/resources/assets/quirky/lang/en_us.json >/dev/null
git diff --check
```

Expected: both JSON files parse and no whitespace errors are reported.

- [ ] **Step 4: Commit the documentation slice**

```sh
git add src/main/resources/assets/quirky/lang/zh_cn.json \
  src/main/resources/assets/quirky/lang/en_us.json README.md
git commit -m "docs: describe expanded offhand quick equip"
```

---

### Task 5: Run the integrated equipment verification

**Files:**
- No new source files; verify the files changed by Tasks 1–4.

- [ ] **Step 1: Run all equipment-focused tests**

```sh
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle test --no-daemon --console=plain \
  --tests dev.quirky.equip_swap.OffhandSwapItemsTest \
  --tests dev.quirky.equip_swap.EquipSwapServerTest \
  --tests dev.quirky.client.equip_swap.EquipSwapClientTest \
  --tests dev.quirky.config.QuirkyConfigDefaultsTest
```

Expected: PASS.

- [ ] **Step 2: Run the complete build**

```sh
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle build --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Perform the runtime checklist**

In a desktop client/server pair, verify the two config entries are adjacent, each switch is independent, all four item types exchange with the offhand without loss or duplication, firework component variants remain intact, and disabled paths pass the original click through. Compare the final Mixin annotations and changed target methods against `$HOME/.cache/mcsrc` using the `quirky-mixin-runtime-audit` checklist.

- [ ] **Step 4: Record the final diff and status**

```sh
git status --short --branch
git log -5 --oneline
```

Expected: only the intended commits/files are present and the worktree is clean.
