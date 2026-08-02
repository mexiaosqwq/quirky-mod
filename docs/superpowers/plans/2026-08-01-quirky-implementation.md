# Quirky Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Quirky Fabric 26.2 mod with 7 vanilla-friendly mechanics: map preview, right-click harvest, double-door sync, clock/compass tooltips, Bottled Cloud, inventory equip swap, and melon-seed drops.

**Architecture:** A single Fabric mod with `src/main` for common/server code and `src/client` for client-only code. Each mechanic is a small package with one entry point; mixins are used only where Fabric API has no hook.

**Tech Stack:** Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.155.2+26.2, Java 25, Gradle 9.6.1, official Mojang mappings.

## Global Constraints

- Minecraft version is exactly `26.2`; Java requirement is `>=25`.
- Only Fabric API is allowed as a runtime dependency.
- Mod ID is `quirky`; Java base package is `dev.quirky`.
- Use official mappings names from the local decompiled 26.2 source, not Yarn names from the installed 1.21.x skill.
- Keep main/server code in `src/main` and client-only code in `src/client`.
- Build command used by every task:

```bash
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle build --no-daemon --console=plain
```

- Every task must end with a passing build and a git commit.
- No config UI, no creative-inventory equip swap, no Totem of Holding in v1.

## File Structure

```text
src/main/java/dev/quirky/
  QuirkyMod.java
  ModItems.java
  item/BottledCloudItem.java
  harvest/HarvestHandler.java
  equip_swap/EquipSwapPayload.java
  equip_swap/EquipSwapServer.java
  tooltips/MapTooltipComponent.java
  mixin/DoubleDoorMixin.java
  mixin/MapTooltipMixin.java
  mixin/MelonSeedMixin.java

src/client/java/dev/quirky/client/
  QuirkyModClient.java
  tooltips/ClientMapTooltipComponent.java
  tooltips/ClockCompassTooltipMixin.java
  equip_swap/EquipSwapClient.java
  mixin/AbstractContainerScreenAccessor.java

src/main/resources/
  fabric.mod.json
  quirky.mixins.json
  quirky.client.mixins.json
  assets/quirky/icon.png
  assets/quirky/lang/en_us.json
  assets/quirky/lang/zh_cn.json
  assets/quirky/models/item/bottled_cloud.json
  assets/quirky/textures/item/bottled_cloud.png
  data/quirky/recipe/bottled_cloud.json
```

---

### Task 0: Rename Scaffold To Quirky

**Files:**
- Modify: `settings.gradle`
- Modify: `gradle.properties`
- Modify: `build.gradle`
- Create: `src/main/resources/fabric.mod.json`
- Create: `src/main/resources/quirky.mixins.json`
- Create: `src/main/resources/quirky.client.mixins.json`
- Create: `src/main/java/dev/quirky/QuirkyMod.java`
- Create: `src/client/java/dev/quirky/client/QuirkyModClient.java`
- Delete old example sources and `modid` resources.

**Interfaces:**
- Produces: `QuirkyMod.MOD_ID` = `"quirky"`, `QuirkyMod.id(String)` returns `Identifier.fromNamespaceAndPath(MOD_ID, path)`.
- Produces: empty mixin configs that later tasks append mixin class names to.

- [x] **Step 1: Update Gradle identity files**

`settings.gradle`:

```groovy
pluginManagement {
	repositories {
		maven {
			name = 'Fabric'
			url = 'https://maven.fabricmc.net/'
		}
		mavenCentral()
		gradlePluginPortal()
	}
}

rootProject.name = 'quirky'
```

`gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true
org.gradle.configuration-cache=false

minecraft_version=26.2
loader_version=0.19.3
loom_version=1.17-SNAPSHOT

mod_version=0.1.0
maven_group=dev.quirky
fabric_api_version=0.155.2+26.2
```

In `build.gradle`, change the `loom { mods { ... } }` block key from `"modid"` to `"quirky"`.

- [x] **Step 2: Replace fabric.mod.json**

```json
{
  "schemaVersion": 1,
  "id": "quirky",
  "version": "${version}",
  "name": "Quirky",
  "description": "A small collection of vanilla-friendly mechanics inspired by Quark.",
  "authors": ["You"],
  "license": "MIT",
  "icon": "assets/quirky/icon.png",
  "environment": "*",
  "entrypoints": {
    "main": ["dev.quirky.QuirkyMod"],
    "client": ["dev.quirky.client.QuirkyModClient"]
  },
  "mixins": [
    "quirky.mixins.json",
    {
      "config": "quirky.client.mixins.json",
      "environment": "client"
    }
  ],
  "depends": {
    "fabricloader": ">=0.19.3",
    "minecraft": "~26.2",
    "java": ">=25",
    "fabric-api": "*"
  }
}
```

- [x] **Step 3: Create entry point and mixin configs**

`src/main/java/dev/quirky/QuirkyMod.java`:

```java
package dev.quirky;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuirkyMod implements ModInitializer {
	public static final String MOD_ID = "quirky";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Quirky loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
```

`src/client/java/dev/quirky/client/QuirkyModClient.java`:

```java
package dev.quirky.client;

import net.fabricmc.api.ClientModInitializer;

public class QuirkyModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
	}
}
```

`src/main/resources/quirky.mixins.json`:

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "dev.quirky.mixin",
  "compatibilityLevel": "JAVA_25",
  "mixins": [],
  "client": [],
  "injectors": {
    "defaultRequire": 1
  }
}
```

`src/main/resources/quirky.client.mixins.json`:

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "dev.quirky.client.mixin",
  "compatibilityLevel": "JAVA_25",
  "mixins": [],
  "injectors": {
    "defaultRequire": 1
  }
}
```

- [x] **Step 4: Move icon and remove example sources**

```bash
mkdir -p src/main/resources/assets/quirky
cp src/main/resources/assets/modid/icon.png src/main/resources/assets/quirky/icon.png
find src/main/resources/assets/modid -mindepth 1 -delete
```

Delete these files with `apply_patch`:

```text
src/main/java/com/example/ExampleMod.java
src/main/java/com/example/mixin/ExampleMixin.java
src/client/java/com/example/client/ExampleModClient.java
src/client/java/com/example/client/mixin/ExampleClientMixin.java
src/main/resources/modid.mixins.json
src/main/resources/modid.client.mixins.json
```

- [x] **Step 5: Build**

Run the build command from Global Constraints.
Expected: `BUILD SUCCESSFUL`.

- [x] **Step 6: Commit**

```bash
git add -A
git commit -m "chore: scaffold quirky fabric mod"
```

---

### Task 1: Bottled Cloud Item

**Files:**
- Create: `src/main/java/dev/quirky/item/BottledCloudItem.java`
- Create: `src/main/java/dev/quirky/ModItems.java`
- Modify: `src/main/java/dev/quirky/QuirkyMod.java`
- Create: `src/main/resources/assets/quirky/models/item/bottled_cloud.json`
- Create: `src/main/resources/assets/quirky/textures/item/bottled_cloud.png`
- Create: `src/main/resources/data/quirky/recipe/bottled_cloud.json`
- Create: `src/main/resources/assets/quirky/lang/en_us.json`
- Create: `src/main/resources/assets/quirky/lang/zh_cn.json`

**Interfaces:**
- Consumes: `QuirkyMod.id(String)`.
- Produces: `ModItems.BOTTLED_CLOUD` (`net.minecraft.world.item.Item`) for later tasks and creative tab registration.

- [x] **Step 1: Create the item class**

`src/main/java/dev/quirky/item/BottledCloudItem.java`:

```java
package dev.quirky.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public class BottledCloudItem extends Item {
	public BottledCloudItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide()) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 400));
		}
		return InteractionResult.SUCCESS;
	}
}
```

- [x] **Step 2: Register the item and creative tab entry**

`src/main/java/dev/quirky/ModItems.java`:

```java
package dev.quirky;

import dev.quirky.item.BottledCloudItem;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class ModItems {
	public static final Item BOTTLED_CLOUD = new BottledCloudItem(
		new Item.Properties().stacksTo(1).craftRemainder(Items.GLASS_BOTTLE)
	);

	private ModItems() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("bottled_cloud"), BOTTLED_CLOUD);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register(output -> output.accept(BOTTLED_CLOUD));
	}
}
```

In `QuirkyMod.onInitialize()`, add `ModItems.register();` before the logger line.

- [x] **Step 3: Add model, recipe, lang, and texture**

`src/main/resources/assets/quirky/models/item/bottled_cloud.json`:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "quirky:item/bottled_cloud"
  }
}
```

`src/main/resources/data/quirky/recipe/bottled_cloud.json`:

```json
{
  "type": "minecraft:crafting_shapeless",
  "ingredients": [
    {
      "item": "minecraft:glass_bottle"
    },
    {
      "item": "minecraft:phantom_membrane"
    }
  ],
  "result": {
    "id": "quirky:bottled_cloud",
    "count": 1
  }
}
```

`src/main/resources/assets/quirky/lang/en_us.json`:

```json
{
  "item.quirky.bottled_cloud": "Bottled Cloud"
}
```

`src/main/resources/assets/quirky/lang/zh_cn.json`:

```json
{
  "item.quirky.bottled_cloud": "云瓶"
}
```

Generate the 16x16 texture by creating and running this script:

```bash
cat > /tmp/make_cloud_texture.py <<'PY'
import struct, zlib
from pathlib import Path

path = Path("src/main/resources/assets/quirky/textures/item/bottled_cloud.png")
path.parent.mkdir(parents=True, exist_ok=True)

w = h = 16
pixels = [[(0, 0, 0, 0) for _ in range(w)] for _ in range(h)]

for y in range(3, 13):
    for x in range(6, 10):
        pixels[y][x] = (190, 220, 235, 255)
for y in range(1, 4):
    pixels[y][7] = (190, 220, 235, 255)
    pixels[y][8] = (190, 220, 235, 255)
for y in range(6, 10):
    for x in range(5, 11):
        pixels[y][x] = (255, 255, 255, 255)
for y in (5, 10):
    pixels[y][6] = (255, 255, 255, 255)
    pixels[y][9] = (255, 255, 255, 255)

raw = b"".join(b"\x00" + bytes(rgba for pixel in row) for row in pixels)

def chunk(tag: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data))

png = b"\x89PNG\r\n\x1a\n"
png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
png += chunk(b"IDAT", zlib.compress(raw))
png += chunk(b"IEND", b"")
path.write_bytes(png)
PY
python3 /tmp/make_cloud_texture.py
```

- [x] **Step 4: Build**

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add bottled cloud item"
```

---

### Task 2: Right-Click Harvest And Replant

**Files:**
- Create: `src/main/java/dev/quirky/harvest/HarvestHandler.java`
- Modify: `src/main/java/dev/quirky/QuirkyMod.java`

**Interfaces:**
- Consumes: `QuirkyMod` entry point.
- Produces: `HarvestHandler.init()` which registers `UseBlockCallback.EVENT`.

- [x] **Step 1: Create the harvest handler**

`src/main/java/dev/quirky/harvest/HarvestHandler.java`:

```java
package dev.quirky.harvest;

import java.util.Iterator;
import java.util.List;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

public final class HarvestHandler {
	private HarvestHandler() {
	}

	public static void init() {
		UseBlockCallback.EVENT.register(HarvestHandler::onUseBlock);
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.PASS;
		}

		ServerLevel serverLevel = (ServerLevel) level;
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);

		if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
			return harvestSeedCrop(serverLevel, pos, state, crop, player, hand);
		}
		if (state.is(Blocks.NETHER_WART) && state.getValue(NetherWartBlock.AGE) == NetherWartBlock.MAX_AGE) {
			return harvestNetherWart(serverLevel, pos, state, player, hand);
		}
		if (state.is(Blocks.ATTACHED_MELON_STEM) || state.is(Blocks.ATTACHED_PUMPKIN_STEM)) {
			return harvestGourd(serverLevel, pos, state, player, hand);
		}
		if (state.getBlock() instanceof CocoaBlock && state.getValue(CocoaBlock.AGE) == CocoaBlock.MAX_AGE) {
			return harvestCocoa(serverLevel, pos, state, player, hand);
		}
		return InteractionResult.PASS;
	}

	private static InteractionResult harvestSeedCrop(
		ServerLevel level, BlockPos pos, BlockState state, CropBlock crop, Player player, InteractionHand hand
	) {
		List<ItemStack> drops = Block.getDrops(state, level, pos, null, player, ItemStack.EMPTY);
		Item seed = seedFor(state.getBlock());
		boolean replant = player.hasInfiniteMaterials();
		if (!replant && seed != Items.AIR) {
			replant = removeOneFromInventory(player.getInventory(), seed)
				|| removeOneFromDrops(drops, seed);
		}
		spawnDrops(level, pos, drops, player);
		level.setBlock(pos, replant ? crop.getStateForAge(0) : Blocks.AIR.defaultBlockState(), 3);
		player.swing(hand);
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult harvestNetherWart(
		ServerLevel level, BlockPos pos, BlockState state, Player player, InteractionHand hand
	) {
		List<ItemStack> drops = Block.getDrops(state, level, pos, null, player, ItemStack.EMPTY);
		boolean replant = player.hasInfiniteMaterials();
		if (!replant) {
			replant = removeOneFromInventory(player.getInventory(), Items.NETHER_WART)
				|| removeOneFromDrops(drops, Items.NETHER_WART);
		}
		spawnDrops(level, pos, drops, player);
		level.setBlock(pos, replant ? state.setValue(NetherWartBlock.AGE, 0) : Blocks.AIR.defaultBlockState(), 3);
		player.swing(hand);
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult harvestGourd(
		ServerLevel level, BlockPos stemPos, BlockState stemState, Player player, InteractionHand hand
	) {
		Direction facing = stemState.getValue(BlockStateProperties.HORIZONTAL_FACING);
		BlockPos fruitPos = stemPos.relative(facing);
		BlockState fruitState = level.getBlockState(fruitPos);
		Block fruit = stemState.is(Blocks.ATTACHED_MELON_STEM) ? Blocks.MELON : Blocks.PUMPKIN;
		if (!fruitState.is(fruit)) {
			return InteractionResult.PASS;
		}
		spawnDrops(level, fruitPos, Block.getDrops(fruitState, level, fruitPos, null, player, ItemStack.EMPTY), player);
		level.setBlock(fruitPos, Blocks.AIR.defaultBlockState(), 3);
		player.swing(hand);
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult harvestCocoa(
		ServerLevel level, BlockPos pos, BlockState state, Player player, InteractionHand hand
	) {
		spawnDrops(level, pos, Block.getDrops(state, level, pos, null, player, ItemStack.EMPTY), player);
		level.setBlock(pos, state.setValue(CocoaBlock.AGE, 0), 3);
		player.swing(hand);
		return InteractionResult.SUCCESS;
	}

	private static void spawnDrops(ServerLevel level, BlockPos pos, List<ItemStack> drops, Player player) {
		if (player.hasInfiniteMaterials()) {
			return;
		}
		for (ItemStack drop : drops) {
			Block.popResource(level, pos, drop);
		}
	}

	private static Item seedFor(Block block) {
		if (block == Blocks.WHEAT) {
			return Items.WHEAT_SEEDS;
		}
		if (block == Blocks.CARROTS) {
			return Items.CARROT;
		}
		if (block == Blocks.POTATOES) {
			return Items.POTATO;
		}
		if (block == Blocks.BEETROOTS) {
			return Items.BEETROOT_SEEDS;
		}
		if (block == Blocks.NETHER_WART) {
			return Items.NETHER_WART;
		}
		return Items.AIR;
	}

	private static boolean removeOneFromInventory(Inventory inventory, Item item) {
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				stack.shrink(1);
				return true;
			}
		}
		return false;
	}

	private static boolean removeOneFromDrops(List<ItemStack> drops, Item item) {
		Iterator<ItemStack> iterator = drops.iterator();
		while (iterator.hasNext()) {
			ItemStack drop = iterator.next();
			if (drop.is(item)) {
				drop.shrink(1);
				if (drop.isEmpty()) {
					iterator.remove();
				}
				return true;
			}
		}
		return false;
	}
}
```

- [x] **Step 2: Register the handler**

In `QuirkyMod.onInitialize()`, add `dev.quirky.harvest.HarvestHandler.init();`.

- [x] **Step 3: Build**

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: add right-click harvest and replant"
```

---

### Task 3: Double Door Sync

**Files:**
- Create: `src/main/java/dev/quirky/mixin/DoubleDoorMixin.java`
- Modify: `src/main/resources/quirky.mixins.json`

**Interfaces:**
- Consumes: empty `quirky.mixins.json` from Task 0.
- Produces: `DoubleDoorMixin` registered in the main mixin config.

- [x] **Step 1: Create the mixin**

`src/main/java/dev/quirky/mixin/DoubleDoorMixin.java`:

```java
package dev.quirky.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DoorBlock.class)
public abstract class DoubleDoorMixin {
	@Inject(method = "useWithoutItem", at = @At("TAIL"))
	private void quirky$syncPartner(
		BlockState state,
		Level level,
		BlockPos pos,
		Player player,
		BlockHitResult hitResult,
		CallbackInfoReturnable<InteractionResult> cir
	) {
		if (level.isClientSide() || !(state.getBlock() instanceof DoorBlock door)) {
			return;
		}
		if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
			pos = pos.below();
		}
		Direction facing = state.getValue(DoorBlock.FACING);
		Direction side = state.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT
			? facing.getClockWise()
			: facing.getCounterClockWise();
		BlockPos partnerPos = pos.relative(side);
		BlockState partnerState = level.getBlockState(partnerPos);
		if (partnerState.is(state.getBlock())
			&& partnerState.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
			&& partnerState.getValue(DoorBlock.FACING) == facing) {
			door.setOpen(player, level, partnerState, partnerPos, state.getValue(DoorBlock.OPEN));
		}
	}
}
```

- [x] **Step 2: Register the mixin**

In `src/main/resources/quirky.mixins.json`, set:

```json
"mixins": ["DoubleDoorMixin"]
```

- [x] **Step 3: Build**

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: sync double doors"
```

---

### Task 4: Map Preview Tooltip

**Files:**
- Create: `src/main/java/dev/quirky/tooltips/MapTooltipComponent.java`
- Create: `src/main/java/dev/quirky/mixin/MapTooltipMixin.java`
- Create: `src/client/java/dev/quirky/client/tooltips/ClientMapTooltipComponent.java`
- Modify: `src/client/java/dev/quirky/client/QuirkyModClient.java`
- Modify: `src/main/resources/quirky.mixins.json`

**Interfaces:**
- Consumes: `quirky.mixins.json`, `QuirkyModClient`.
- Produces: `MapTooltipComponent(MapId)` marker component and `ClientMapTooltipComponent` renderer.

- [x] **Step 1: Create the common tooltip component**

`src/main/java/dev/quirky/tooltips/MapTooltipComponent.java`:

```java
package dev.quirky.tooltips;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.saveddata.maps.MapId;

public record MapTooltipComponent(MapId mapId) implements TooltipComponent {
}
```

- [x] **Step 2: Create the map tooltip mixin**

`src/main/java/dev/quirky/mixin/MapTooltipMixin.java`:

```java
package dev.quirky.mixin;

import java.util.Optional;

import dev.quirky.tooltips.MapTooltipComponent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MapItem.class)
public abstract class MapTooltipMixin {
	@Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
	private void quirky$mapTooltip(ItemStack stack, CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
		if (stack.has(DataComponents.MAP_ID)) {
			cir.setReturnValue(Optional.of(new MapTooltipComponent(stack.get(DataComponents.MAP_ID))));
		}
	}
}
```

- [x] **Step 3: Create the client renderer**

`src/client/java/dev/quirky/client/tooltips/ClientMapTooltipComponent.java`:

```java
package dev.quirky.client.tooltips;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class ClientMapTooltipComponent implements ClientTooltipComponent {
	private static final int SIZE = 64;
	private final MapId mapId;
	private final MapRenderState renderState = new MapRenderState();

	public ClientMapTooltipComponent(MapId mapId) {
		this.mapId = mapId;
	}

	@Override
	public int getWidth(Font font) {
		return SIZE;
	}

	@Override
	public int getHeight(Font font) {
		return SIZE;
	}

	@Override
	public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		MapItemSavedData data = mc.level.getMapData(mapId);
		if (data == null) {
			return;
		}
		mc.getMapRenderer().extractRenderState(mapId, data, renderState);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(0.5F, 0.5F);
		graphics.map(renderState);
		graphics.pose().popMatrix();
	}
}
```

- [x] **Step 4: Register the client callback**

Replace `QuirkyModClient`:

```java
package dev.quirky.client;

import dev.quirky.client.tooltips.ClientMapTooltipComponent;
import dev.quirky.tooltips.MapTooltipComponent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;

public class QuirkyModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientTooltipComponentCallback.EVENT.register(component -> {
			if (component instanceof MapTooltipComponent map) {
				return new ClientMapTooltipComponent(map.mapId());
			}
			return null;
		});
	}
}
```

In `src/main/resources/quirky.mixins.json`, set:

```json
"mixins": ["DoubleDoorMixin", "MapTooltipMixin"]
```

- [x] **Step 5: Build**

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: render filled maps in tooltips"
```

---

### Task 5: Clock And Compass Tooltips

**Files:**
- Create: `src/client/java/dev/quirky/client/tooltips/ClockCompassTooltipMixin.java`
- Modify: `src/main/resources/quirky.client.mixins.json`
- Modify: `src/main/resources/assets/quirky/lang/en_us.json`
- Modify: `src/main/resources/assets/quirky/lang/zh_cn.json`

**Interfaces:**
- Consumes: `quirky.client.mixins.json`.
- Produces: client-only mixin that appends tooltip lines for clock and compass.

- [x] **Step 1: Create the mixin**

`src/client/java/dev/quirky/client/tooltips/ClockCompassTooltipMixin.java`:

```java
package dev.quirky.client.tooltips;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ClockCompassTooltipMixin {
	@Inject(method = "getTooltipLines", at = @At("RETURN"))
	private void quirky$appendTooltip(
		Item.TooltipContext context,
		@Nullable Player player,
		TooltipFlag flag,
		CallbackInfoReturnable<List<Component>> cir
	) {
		ItemStack stack = (ItemStack) (Object) this;
		if (player == null) {
			return;
		}
		if (stack.is(Items.CLOCK)) {
			cir.getReturnValue().add(
				Component.translatable("tooltip.quirky.clock", formatTime(player.level()))
					.withStyle(ChatFormatting.GRAY)
			);
		} else if (stack.is(Items.COMPASS)) {
			LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
			if (tracker != null && tracker.target().isPresent()) {
				GlobalPos target = tracker.target().get();
				BlockPos targetPos = target.pos();
				cir.getReturnValue().add(
					Component.translatable(
						"tooltip.quirky.lodestone",
						targetPos.getX(),
						targetPos.getZ(),
						target.dimension().location().toString()
					).withStyle(ChatFormatting.GRAY)
				);
			} else {
				BlockPos spawnPos = player.level().getRespawnData().globalPos().pos();
				cir.getReturnValue().add(
					Component.translatable(
						"tooltip.quirky.compass",
						player.getDirection().getSerializedName(),
						spawnPos.getX(),
						spawnPos.getZ()
					).withStyle(ChatFormatting.GRAY)
				);
			}
		}
	}

	private static String formatTime(Level level) {
		long dayTime = level.getDefaultClockTime();
		long day = dayTime / 24000L + 1L;
		int ticks = (int) (dayTime % 24000L);
		int hours = (ticks / 1000 + 6) % 24;
		int minutes = (ticks % 1000) * 60 / 1000;
		return day + " " + String.format("%02d:%02d", hours, minutes);
	}
}
```

- [x] **Step 2: Register the client mixin**

In `src/main/resources/quirky.client.mixins.json`, set:

```json
"mixins": ["ClockCompassTooltipMixin"]
```

- [x] **Step 3: Add translations**

`src/main/resources/assets/quirky/lang/en_us.json`:

```json
{
  "item.quirky.bottled_cloud": "Bottled Cloud",
  "tooltip.quirky.clock": "Day %s",
  "tooltip.quirky.compass": "Facing %s · Spawn %s, %s",
  "tooltip.quirky.lodestone": "Lodestone %s, %s (%s)"
}
```

`src/main/resources/assets/quirky/lang/zh_cn.json`:

```json
{
  "item.quirky.bottled_cloud": "云瓶",
  "tooltip.quirky.clock": "第 %s 天",
  "tooltip.quirky.compass": "朝向 %s · 出生点 %s, %s",
  "tooltip.quirky.lodestone": "磁石 %s, %s (%s)"
}
```

- [x] **Step 4: Build**

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add clock and compass tooltips"
```

---

### Task 6: Inventory Equip Swap

**Files:**
- Create: `src/main/java/dev/quirky/equip_swap/EquipSwapPayload.java`
- Create: `src/main/java/dev/quirky/equip_swap/EquipSwapServer.java`
- Create: `src/client/java/dev/quirky/client/equip_swap/EquipSwapClient.java`
- Create: `src/client/java/dev/quirky/client/mixin/AbstractContainerScreenAccessor.java`
- Modify: `src/main/java/dev/quirky/QuirkyMod.java`
- Modify: `src/client/java/dev/quirky/client/QuirkyModClient.java`
- Modify: `src/main/resources/quirky.client.mixins.json`

**Interfaces:**
- Consumes: `QuirkyMod`, `QuirkyModClient`, `quirky.client.mixins.json`.
- Produces: `EquipSwapPayload(int containerId, int slotIndex)` and server receiver.

- [x] **Step 1: Create the payload**

`src/main/java/dev/quirky/equip_swap/EquipSwapPayload.java`:

```java
package dev.quirky.equip_swap;

import dev.quirky.QuirkyMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record EquipSwapPayload(int containerId, int slotIndex) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<EquipSwapPayload> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(QuirkyMod.MOD_ID, "equip_swap"));
	public static final StreamCodec<FriendlyByteBuf, EquipSwapPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT,
		EquipSwapPayload::containerId,
		ByteBufCodecs.VAR_INT,
		EquipSwapPayload::slotIndex,
		EquipSwapPayload::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
```

- [x] **Step 2: Create the server handler**

`src/main/java/dev/quirky/equip_swap/EquipSwapServer.java`:

```java
package dev.quirky.equip_swap;

import java.util.Optional;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public final class EquipSwapServer {
	private EquipSwapServer() {
	}

	public static void init() {
		PayloadTypeRegistry.playC2S().register(EquipSwapPayload.TYPE, EquipSwapPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(EquipSwapPayload.TYPE, EquipSwapServer::handle);
	}

	private static void handle(EquipSwapPayload payload, ServerPlayNetworking.Context context) {
		context.server().execute(() -> {
			ServerPlayer player = context.player();
			AbstractContainerMenu menu = player.containerMenu;
			if (menu.containerId != payload.containerId()) {
				return;
			}
			if (payload.slotIndex() < 0 || payload.slotIndex() >= menu.slots.size()) {
				return;
			}

			Slot source = menu.getSlot(payload.slotIndex());
			ItemStack stack = source.getItem();
			if (stack.isEmpty() || !stack.has(DataComponents.EQUIPPABLE)) {
				return;
			}

			EquipmentSlot equipmentSlot = player.getEquipmentSlotForItem(stack);
			int inventoryIndex = inventoryIndexFor(equipmentSlot);
			if (inventoryIndex < 0) {
				return;
			}

			Optional<Slot> armorSlot = menu.slots.stream()
				.filter(slot -> slot.container == player.getInventory() && slot.index == inventoryIndex)
				.findFirst();
			if (armorSlot.isEmpty()) {
				return;
			}

			Slot target = armorSlot.get();
			if (source == target) {
				return;
			}
			ItemStack worn = target.getItem();
			if (!worn.isEmpty()
				&& EnchantmentHelper.has(worn, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)
				&& !player.isCreative()) {
				return;
			}
			if (!target.mayPlace(stack)) {
				return;
			}

			target.setByPlayer(stack, worn);
			source.setByPlayer(worn, stack);
			menu.broadcastChanges();
		});
	}

	private static int inventoryIndexFor(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> 39;
			case CHEST -> 38;
			case LEGS -> 37;
			case FEET -> 36;
			case BODY -> 41;
			default -> -1;
		};
	}
}
```

- [x] **Step 3: Create the screen accessor**

`src/client/java/dev/quirky/client/mixin/AbstractContainerScreenAccessor.java`:

```java
package dev.quirky.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Invoker("getHoveredSlot")
	Slot quirky$getHoveredSlot(double x, double y);
}
```

- [x] **Step 4: Create the client handler**

`src/client/java/dev/quirky/client/equip_swap/EquipSwapClient.java`:

```java
package dev.quirky.client.equip_swap;

import dev.quirky.client.mixin.AbstractContainerScreenAccessor;
import dev.quirky.equip_swap.EquipSwapPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.Slot;

public final class EquipSwapClient {
	private EquipSwapClient() {
	}

	public static void init() {
		ScreenEvents.BEFORE_INIT.register((client, screen, width, height) -> {
			if (screen instanceof AbstractContainerScreen<?> containerScreen) {
				ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
					if (event.button() != 1) {
						return true;
					}
					Slot slot = ((AbstractContainerScreenAccessor) containerScreen)
						.quirky$getHoveredSlot(event.x(), event.y());
					if (slot == null
						|| !slot.hasItem()
						|| !slot.getItem().has(DataComponents.EQUIPPABLE)
						|| !containerScreen.getMenu().getCarried().isEmpty()) {
						return true;
					}
					ClientPlayNetworking.send(
						new EquipSwapPayload(containerScreen.getMenu().containerId, slot.index)
					);
					return false;
				});
			}
		});
	}
}
```

- [x] **Step 5: Register init calls and mixin**

In `QuirkyMod.onInitialize()`, add `dev.quirky.equip_swap.EquipSwapServer.init();`.

In `QuirkyModClient.onInitializeClient()`, add `dev.quirky.client.equip_swap.EquipSwapClient.init();`.

In `src/main/resources/quirky.client.mixins.json`, set:

```json
"mixins": ["ClockCompassTooltipMixin", "AbstractContainerScreenAccessor"]
```

- [x] **Step 6: Build**

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: swap equipment with right-click in inventory"
```

---

### Task 7: Melon Seed Drop On Eat

**Files:**
- Create: `src/main/java/dev/quirky/mixin/MelonSeedMixin.java`
- Modify: `src/main/resources/quirky.mixins.json`

**Interfaces:**
- Consumes: `quirky.mixins.json`.
- Produces: server-side mixin that grants one melon seed after eating a melon slice.

- [x] **Step 1: Create the mixin**

`src/main/java/dev/quirky/mixin/MelonSeedMixin.java`:

```java
package dev.quirky.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MelonSeedMixin {
	@Inject(
		method = "completeUsingItem",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;",
			shift = At.Shift.AFTER
		)
	)
	private void quirky$dropMelonSeed(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self instanceof ServerPlayer player
			&& !player.hasInfiniteMaterials()
			&& self.getUseItem().is(Items.MELON_SLICE)) {
			ItemStack seed = new ItemStack(Items.MELON_SEEDS);
			if (!player.getInventory().add(seed)) {
				player.drop(seed, false);
			}
		}
	}
}
```

- [x] **Step 2: Register the mixin**

In `src/main/resources/quirky.mixins.json`, set:

```json
"mixins": ["DoubleDoorMixin", "MapTooltipMixin", "MelonSeedMixin"]
```

- [x] **Step 3: Build**

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: drop melon seeds when eating melon slices"
```

---

### Task 8: Final Build And Verification

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: every task above.
- Produces: final build artifact `build/libs/quirky-0.1.0.jar`.

- [x] **Step 1: Update README**

Add a short section listing the seven mechanics and the build command from Global Constraints.

- [x] **Step 2: Run the full build**

Run the build command from Global Constraints.
Expected: `BUILD SUCCESSFUL` and `build/libs/quirky-0.1.0.jar` exists.

- [x] **Step 3: Run a server smoke test (optional but recommended)**

If `gradle runServer` is available and the environment permits:

```bash
mkdir -p run
printf 'eula=true\n' > run/eula.txt
timeout 90 gradle runServer --no-daemon --console=plain
```

Expected: server reaches `Done` without crashing.

- [x] **Step 4: Manual client checklist (desktop environment)**

This checklist is for a machine with a GUI and the same `build/libs/quirky-0.1.0.jar`:

1. Filled map hover shows a 64x64 map preview; empty map does not.
2. Right-click mature wheat replants from inventory or from drops; melon/pumpkin harvest by right-clicking the attached stem; cocoa resets to age 0.
3. Two matching doors open together.
4. Clock and compass tooltips show time, facing, and spawn/lodestone data.
5. Bottled Cloud applies slow falling, returns a glass bottle in survival, and is craftable.
6. Right-clicking a chestplate in inventory swaps with a worn elytra, and vice versa.
7. Eating a melon slice in survival gives one melon seed.

- [x] **Step 5: Commit**

```bash
git add -A
git commit -m "docs: finalize quirky readme and build verification"
```
