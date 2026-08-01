# Cloud Bottle Cloud Placement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Change the Bottled Cloud from a one-shot slow-falling drink into a tool that places a temporary, powder-snow-like cloud block in midair, complete with sound, particles, replacement-by-blocks, and item consumption rules.

**Architecture:** A new `quirky:cloud` block provides the temporary cloud behavior; a small `CloudPlacement` helper finds the nearest air block along the player's look direction; `BottledCloudItem.use` combines placement, sound, and consumption. All behavior is server-side; only particle animation is client-side.

**Tech Stack:** Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.155.2+26.2, Java 25, Gradle 9.6.1, official Mojang mappings.

## Global Constraints

- Minecraft version is exactly `26.2`; Java requirement is `>=25`.
- Only Fabric API is allowed as a runtime dependency.
- Mod ID is `quirky`; Java base package is `dev.quirky`.
- Use official mapping names from `$HOME/.cache/mcsrc`.
- Keep main/server code in `src/main` and client-only code in `src/client`.
- TDD: production code is written only after a failing test.
- Build command:

```bash
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle clean build --no-daemon --console=plain
```

- Every task ends with a passing build and a git commit.
- Hand-feel details are mandatory: sound, particles, slow fall-through, replaceability, and glass-bottle return must all be present.

---

### Task 1: Cloud Placement Helper

**Files:**
- Create: `src/main/java/dev/quirky/cloud/CloudPlacement.java`
- Test: `src/test/java/dev/quirky/cloud/CloudPlacementTest.java`

**Interfaces:**
- Produces: `CloudPlacement.findNearestAir(Level level, Vec3 from, Vec3 look, double reach)` returning `@Nullable BlockPos`.

- [ ] **Step 1: Write the failing test**

`src/test/java/dev/quirky/cloud/CloudPlacementTest.java`:

```java
package dev.quirky.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.quirky.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CloudPlacementTest {
	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	@Test
	void returnsNearestAirBlockAlongLookDirection() {
		Level level = mock(Level.class);
		BlockPos near = new BlockPos(1, 64, 0);
		BlockPos far = new BlockPos(2, 64, 0);
		when(level.getBlockState(near)).thenReturn(Blocks.STONE.defaultBlockState());
		when(level.getBlockState(far)).thenReturn(Blocks.AIR.defaultBlockState());

		BlockPos found = CloudPlacement.findNearestAir(
			level,
			new Vec3(0.5, 64.5, 0.5),
			new Vec3(1.0, 0.0, 0.0),
			4.5
		);

		assertEquals(far, found);
	}

	@Test
	void returnsNullWhenNoAirBlockIsWithinReach() {
		Level level = mock(Level.class);
		when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.STONE.defaultBlockState());

		BlockPos found = CloudPlacement.findNearestAir(
			level,
			new Vec3(0.5, 64.5, 0.5),
			new Vec3(1.0, 0.0, 0.0),
			4.5
		);

		assertNull(found);
	}
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle test --tests dev.quirky.cloud.CloudPlacementTest --no-daemon --console=plain
```

Expected: compile failure because `CloudPlacement` does not exist.

- [ ] **Step 3: Implement the helper**

`src/main/java/dev/quirky/cloud/CloudPlacement.java`:

```java
package dev.quirky.cloud;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class CloudPlacement {
	private static final double STEP = 0.25;

	private CloudPlacement() {
	}

	public static @Nullable BlockPos findNearestAir(Level level, Vec3 from, Vec3 look, double reach) {
		for (double t = STEP; t <= reach; t += STEP) {
			BlockPos pos = BlockPos.containing(from.add(look.scale(t)));
			if (level.getBlockState(pos).isAir()) {
				return pos;
			}
		}
		return null;
	}
}
```

- [ ] **Step 4: Run the test to verify it passes**

Same test command. Expected: 2 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/quirky/cloud/CloudPlacement.java src/test/java/dev/quirky/cloud/CloudPlacementTest.java
git commit -m "feat: find nearest air block for cloud placement"
```

---

### Task 2: Cloud Block

**Files:**
- Create: `src/main/java/dev/quirky/block/CloudBlock.java`
- Create: `src/main/java/dev/quirky/ModBlocks.java`
- Modify: `src/main/java/dev/quirky/QuirkyMod.java`
- Test: `src/test/java/dev/quirky/block/CloudBlockTest.java`

**Interfaces:**
- Consumes: `QuirkyMod.id(String)`.
- Produces: `ModBlocks.CLOUD` (`net.minecraft.world.level.block.Block`) for `BottledCloudItem`.

- [ ] **Step 1: Write the failing test**

`src/test/java/dev/quirky/block/CloudBlockTest.java`:

```java
package dev.quirky.block;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.quirky.ModBlocks;
import dev.quirky.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CloudBlockTest {
	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	@Test
	void onPlaceSchedulesLifetimeTick() {
		ServerLevel level = mock(ServerLevel.class);
		BlockPos pos = new BlockPos(1, 64, 1);

		ModBlocks.CLOUD.onPlace(
			ModBlocks.CLOUD.defaultBlockState(),
			level,
			pos,
			Blocks.AIR.defaultBlockState(),
			false
		);

		verify(level).scheduleTick(pos, ModBlocks.CLOUD, 200);
	}

	@Test
	void tickRemovesCloudWhenExpired() {
		ServerLevel level = mock(ServerLevel.class);
		BlockPos pos = new BlockPos(1, 64, 1);
		BlockState state = ModBlocks.CLOUD.defaultBlockState();
		when(level.getBlockState(pos)).thenReturn(state);

		ModBlocks.CLOUD.tick(state, level, pos, RandomSource.create());

		verify(level).setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
	}

	@Test
	void entityInsideSlowsEntity() {
		Level level = mock(Level.class);
		BlockPos pos = new BlockPos(1, 64, 1);
		BlockState state = ModBlocks.CLOUD.defaultBlockState();
		Entity entity = mock(Entity.class);

		ModBlocks.CLOUD.entityInside(state, level, pos, entity, InsideBlockEffectApplier.NOOP, false);

		verify(entity).makeStuckInBlock(state, new Vec3(0.9, 0.25, 0.9));
	}

	@Test
	void cloudIsReplaceableByBlockPlacement() {
		assertTrue(ModBlocks.CLOUD.defaultBlockState().canBeReplaced(mock(BlockPlaceContext.class)));
	}
}
```

Add missing static import for `when`:

```java
import static org.mockito.Mockito.when;
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle test --tests dev.quirky.block.CloudBlockTest --no-daemon --console=plain
```

Expected: compile failure because `ModBlocks` and `CloudBlock` do not exist.

- [ ] **Step 3: Implement the block**

`src/main/java/dev/quirky/block/CloudBlock.java`:

```java
package dev.quirky.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CloudBlock extends Block {
	public static final MapCodec<CloudBlock> CODEC = simpleCodec(CloudBlock::new);
	private static final int LIFETIME_TICKS = 200;
	private static final Vec3 STUCK_SPEED = new Vec3(0.9, 0.25, 0.9);

	public CloudBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public MapCodec<CloudBlock> codec() {
		return CODEC;
	}

	@Override
	protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		return true;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected VoxelShape getEntityInsideCollisionShape(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
		return Shapes.block();
	}

	@Override
	protected void entityInside(
		BlockState state,
		Level level,
		BlockPos pos,
		Entity entity,
		InsideBlockEffectApplier effectApplier,
		boolean isPrecise
	) {
		entity.makeStuckInBlock(state, STUCK_SPEED);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!level.isClientSide()) {
			level.scheduleTick(pos, this, LIFETIME_TICKS);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (level.getBlockState(pos).is(this)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(4) == 0) {
			double x = pos.getX() + random.nextDouble();
			double z = pos.getZ() + random.nextDouble();
			level.addParticle(ParticleTypes.CLOUD, x, pos.getY() - 0.1, z, 0.0, -0.05, 0.0);
		}
	}
}
```

`src/main/java/dev/quirky/ModBlocks.java`:

```java
package dev.quirky;

import dev.quirky.block.CloudBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
	public static final Block CLOUD = new CloudBlock(
		BlockBehaviour.Properties.of()
			.replaceable()
			.noCollision()
			.noLootTable()
			.instabreak()
			.sound(SoundType.POWDER_SNOW)
	);

	private ModBlocks() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.BLOCK, QuirkyMod.id("cloud"), CLOUD);
	}
}
```

In `QuirkyMod.onInitialize()`, add `ModBlocks.register();` before `ModItems.register();`.

- [ ] **Step 4: Run the test to verify it passes**

Same test command. Expected: 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/quirky/block/CloudBlock.java src/main/java/dev/quirky/ModBlocks.java src/main/java/dev/quirky/QuirkyMod.java src/test/java/dev/quirky/block/CloudBlockTest.java
git commit -m "feat: add temporary replaceable cloud block"
```

---

### Task 3: Cloud Block Assets

**Files:**
- Create: `src/main/resources/assets/quirky/blockstates/cloud.json`
- Create: `src/main/resources/assets/quirky/models/block/cloud.json`
- Create: `src/main/resources/assets/quirky/textures/block/cloud.png`

**Interfaces:**
- Produces: `quirky:block/cloud` model and texture used by the `cloud` blockstate.

- [ ] **Step 1: Create blockstate and model**

`src/main/resources/assets/quirky/blockstates/cloud.json`:

```json
{
  "variants": {
    "": {
      "model": "quirky:block/cloud"
    }
  }
}
```

`src/main/resources/assets/quirky/models/block/cloud.json`:

```json
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "quirky:block/cloud"
  }
}
```

- [ ] **Step 2: Generate a soft white cloud texture**

Generate `src/main/resources/assets/quirky/textures/block/cloud.png` with this script:

```bash
python3 - <<'PY'
import struct, zlib
from pathlib import Path

path = Path("src/main/resources/assets/quirky/textures/block/cloud.png")
w = h = 16
rows = []
for y in range(h):
    row = bytearray(b"\x00")
    for x in range(w):
        distance = max(abs(x - 7.5), abs(y - 7.5))
        alpha = max(0, min(255, int(235 - distance * 26)))
        row += bytes((255, 255, 255, alpha))
    rows.append(bytes(row))

raw = b"".join(rows)

def chunk(tag: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data))

png = b"\x89PNG\r\n\x1a\n"
png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
png += chunk(b"IDAT", zlib.compress(raw))
png += chunk(b"IEND", b"")
path.parent.mkdir(parents=True, exist_ok=True)
path.write_bytes(png)
PY
```

- [ ] **Step 3: Verify assets are packaged**

After a build, run:

```bash
unzip -l build/libs/quirky-0.1.0.jar | rg 'assets/quirky/(blockstates|models/block|textures/block)/cloud'
```

Expected: three asset entries exist.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/assets/quirky/blockstates/cloud.json src/main/resources/assets/quirky/models/block/cloud.json src/main/resources/assets/quirky/textures/block/cloud.png
git commit -m "feat: add cloud block model and texture"
```

---

### Task 4: Bottled Cloud Uses Cloud Placement

**Files:**
- Modify: `src/main/java/dev/quirky/item/BottledCloudItem.java`
- Modify: `src/test/java/dev/quirky/item/BottledCloudUseTest.java`

**Interfaces:**
- Consumes: `CloudPlacement.findNearestAir(...)`, `ModBlocks.CLOUD`.
- Produces: new `BottledCloudItem.use` behavior.

- [ ] **Step 1: Write the failing tests**

Add these tests to `src/test/java/dev/quirky/item/BottledCloudUseTest.java`:

```java
@Test
void usePlacesCloudAndConsumesBottle() {
	Player player = mock(Player.class);
	when(player.hasInfiniteMaterials()).thenReturn(false);
	when(player.getEyePosition()).thenReturn(new Vec3(0.5, 64.5, 0.5));
	when(player.getLookAngle()).thenReturn(new Vec3(1.0, 0.0, 0.0));
	when(player.blockInteractionRange()).thenReturn(4.5);
	Level level = mock(Level.class);
	when(level.isClientSide()).thenReturn(false);
	BlockPos pos = new BlockPos(2, 64, 0);
	when(level.getBlockState(new BlockPos(1, 64, 0))).thenReturn(Blocks.STONE.defaultBlockState());
	when(level.getBlockState(pos)).thenReturn(Blocks.AIR.defaultBlockState());

	ItemStack stack = new ItemStack(ModItems.BOTTLED_CLOUD);
	when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(stack);

	InteractionResult result = stack.use(level, player, InteractionHand.MAIN_HAND);

	verify(level).setBlock(pos, ModBlocks.CLOUD.defaultBlockState(), 3);
	verify(player).playSound(SoundEvents.BOTTLE_EMPTY, 1.0F, 1.0F);
	assertInstanceOf(InteractionResult.Success.class, result);
	assertTrue(stack.isEmpty());
}

@Test
void useFailsWithoutConsumingWhenNoAirIsInReach() {
	Player player = mock(Player.class);
	when(player.hasInfiniteMaterials()).thenReturn(false);
	when(player.getEyePosition()).thenReturn(new Vec3(0.5, 64.5, 0.5));
	when(player.getLookAngle()).thenReturn(new Vec3(1.0, 0.0, 0.0));
	when(player.blockInteractionRange()).thenReturn(4.5);
	Level level = mock(Level.class);
	when(level.isClientSide()).thenReturn(false);
	when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.STONE.defaultBlockState());

	ItemStack stack = new ItemStack(ModItems.BOTTLED_CLOUD);
	when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(stack);

	InteractionResult result = stack.use(level, player, InteractionHand.MAIN_HAND);

	assertInstanceOf(InteractionResult.Fail.class, result);
	assertEquals(1, stack.getCount());
}
```

Add imports for `ModBlocks`, `SoundEvents`, `BlockPos`, `Vec3`, `Blocks`, `any`, and `assertInstanceOf`.

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```bash
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle test --tests dev.quirky.item.BottledCloudUseTest --no-daemon --console=plain
```

Expected: the new tests fail because the item still grants slow falling.

- [ ] **Step 3: Implement the new use behavior**

`src/main/java/dev/quirky/item/BottledCloudItem.java`:

```java
package dev.quirky.item;

import dev.quirky.ModBlocks;
import dev.quirky.cloud.CloudPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class BottledCloudItem extends Item {
	public BottledCloudItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		BlockPos pos = CloudPlacement.findNearestAir(
			level,
			player.getEyePosition(),
			player.getLookAngle(),
			player.blockInteractionRange()
		);
		if (pos == null) {
			return InteractionResult.FAIL;
		}
		level.setBlock(pos, ModBlocks.CLOUD.defaultBlockState(), 3);
		player.playSound(SoundEvents.BOTTLE_EMPTY, 1.0F, 1.0F);
		if (!player.hasInfiniteMaterials()) {
			ItemStack stack = player.getItemInHand(hand);
			stack.consume(1, player);
		}
		return InteractionResult.SUCCESS;
	}
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Same test command. Expected: all BottledCloud tests pass.

- [ ] **Step 5: Run the full test suite and build**

```bash
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle clean build --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/quirky/item/BottledCloudItem.java src/test/java/dev/quirky/item/BottledCloudUseTest.java
git commit -m "feat: bottled cloud places temporary cloud block"
```

---

### Task 5: Verify In-Game Hand-Feel

**Files:**
- No production changes expected unless the game check reveals a problem.

- [ ] **Step 1: Run a dedicated server smoke test**

```bash
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle runServer --no-daemon --console=plain
```

Expected: server starts with `Quirky loaded` and no mixin or block registration errors.

- [ ] **Step 2: Desktop client checklist**

On a desktop client with the built jar:

- Right-click cloud bottle in air places a translucent white cloud block.
- The cloud appears at the nearest air block along the crosshair.
- The cloud shows falling cloud particles below it.
- Walking into the cloud slows descent and lets the player pass through.
- Placing a normal block into the cloud position replaces the cloud.
- The cloud disappears after about 10 seconds.
- Survival consumes the bottle and leaves a glass bottle; creative does not consume.
- Looking at a solid wall with no air inside reach does not consume the bottle.

- [ ] **Step 3: Fix any failure found**

If any checklist item fails, write a failing test first, fix the production code, rerun the full build, and commit with `fix:`.
