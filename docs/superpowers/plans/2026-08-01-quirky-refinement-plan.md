# Quirky 细节补全实施计划

> **给执行者：** 使用 `superpowers:subagent-driven-development` 或 `superpowers:executing-plans` 按任务执行。步骤用 `- [ ]` 跟踪。

## 本次要改什么

- **云瓶**：把现在的“缓慢下落”改成“右键在准心最近的空气方块生成临时云团”。云团可被普通方块原位替换、实体缓慢穿过、下方持续白色粒子、10 秒后消失；生存消耗并返还玻璃瓶，创造不消耗。
- **双开门**：玩家、村民等实体打开一扇门时，相邻匹配门同步开关；铁门和红石直改状态不联动。
- **吃西瓜**：吃完最后一片不再直接进背包，改为从玩家面前吐出 `melon_seeds` 物品实体，带 40 tick 拾取延迟、投掷者和吐籽音效。
- **地图 tooltip**：悬停地图时显示原版肉色地图纸边，预览尺寸从 64x64 改为 71x71。
- **收割反馈**：保留已有破坏粒子和点击者挥臂，补上破坏音效；作物/下界疣补种成功时补上补种音效。

## 文件跟踪

- [x] `src/main/java/dev/quirky/ModBlocks.java`（新增：注册云团方块）
- [x] `src/main/java/dev/quirky/block/CloudBlock.java`（新增：临时云团方块）
- [x] `src/main/java/dev/quirky/cloud/CloudPlacement.java`（新增：准心空气方块查找）
- [x] `src/main/java/dev/quirky/item/BottledCloudItem.java`（修改：右键放云团）
- [x] `src/main/java/dev/quirky/QuirkyMod.java`（修改：初始化注册顺序）
- [x] `src/test/java/dev/quirky/cloud/CloudPlacementTest.java`（新增）
- [x] `src/test/java/dev/quirky/block/CloudBlockTest.java`（新增）
- [x] `src/test/java/dev/quirky/item/BottledCloudUseTest.java`（修改）
- [x] `src/main/java/dev/quirky/door/DoubleDoorHandler.java`（修改：支持非玩家实体）
- [x] `src/main/java/dev/quirky/mixin/DoubleDoorMixin.java`（修改：增加 `setOpen` 注入）
- [x] `src/test/java/dev/quirky/door/DoubleDoorHandlerTest.java`（修改）
- [x] `src/main/java/dev/quirky/food/MelonSeedHandler.java`（修改：吐籽）
- [x] `src/test/java/dev/quirky/food/MelonSeedHandlerTest.java`（修改）
- [x] `src/client/java/dev/quirky/client/tooltips/ClientMapTooltipComponent.java`（修改：地图纸边）
- [x] `src/test/java/dev/quirky/client/tooltips/ClientMapTooltipComponentTest.java`（新增）
- [x] `build.gradle`（修改：测试编译包含 client 输出）
- [x] `src/main/java/dev/quirky/harvest/HarvestFx.java`（修改：补破坏音/补种音）
- [x] `src/main/java/dev/quirky/harvest/HarvestHandler.java`（修改：补种音调用点）
- [x] `src/test/java/dev/quirky/harvest/HarvestFxTest.java`（修改）
- [x] `src/main/resources/assets/quirky/blockstates/cloud.json`（新增）
- [x] `src/main/resources/assets/quirky/models/block/cloud.json`（新增）
- [x] `src/main/resources/assets/quirky/textures/block/cloud.png`（新增）

**架构：** 继续使用单一 Fabric 模组。服务端/通用逻辑在 `src/main`，客户端逻辑在 `src/client`，每个机制一个包，Mixin 只用于 Fabric API 没有钩子的地方。

**技术栈：** Minecraft 26.2、Fabric Loader 0.19.3、Fabric API 0.155.2+26.2、Java 25、Gradle 9.6.1、官方映射。

## 全局约束

- Minecraft 必须是 `26.2`，Java `>=25`。
- 运行时只依赖 Fabric API。
- 模组 ID 是 `quirky`，Java 包根是 `dev.quirky`。
- API 名称以 `$HOME/.cache/mcsrc` 的 26.2 反编译源码为准。
- TDD：先写失败测试，再写生产代码。
- 每次任务结束必须通过构建并提交。

构建命令：

```bash
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle clean build --no-daemon --console=plain
```

## 一、音效与细节总表

| 机制 | 音效 | 视觉/物理反馈 |
|---|---|---|
| 云瓶放置 | `item.bottle.empty` | 生成云团；云团下方持续白色云粒子 |
| 云团方块 | 无 | 可被普通方块替换；实体缓慢穿过；10 秒后消失 |
| 收割作物/下界疣 | 方块破坏音 + 补种音 | `levelEvent(2001)` 破坏粒子 + 玩家挥臂 |
| 收割西瓜/南瓜/可可豆 | 果实/可可豆破坏音 | 同上 |
| 吃西瓜吐籽 | `entity.fox.spit` | 种子从玩家面前抛出，40 tick 后能捡回 |
| 双开门 | 原版门音效 | 玩家和村民等实体都能同步开两扇门 |
| 背包换装 | 原版装备音效 | 原版交换逻辑 |
| 地图 tooltip | 无 | 原版肉色地图纸边 |

## 二、文件结构

```text
src/main/java/dev/quirky/
  ModBlocks.java                  # 新增云团方块注册
  block/CloudBlock.java           # 新增临时云团方块
  cloud/CloudPlacement.java       # 新增准心空气方块查找
  door/DoubleDoorHandler.java     # 改造：支持非玩家实体
  food/MelonSeedHandler.java      # 改造：吐籽而不是进背包
  harvest/HarvestFx.java          # 改造：补破坏音/补种音
  item/BottledCloudItem.java      # 改造：右键放云团
  mixin/DoubleDoorMixin.java      # 改造：增加 setOpen 注入

src/client/java/dev/quirky/client/
  tooltips/ClientMapTooltipComponent.java  # 改造：画地图纸边

src/main/resources/assets/quirky/
  blockstates/cloud.json
  models/block/cloud.json
  textures/block/cloud.png
  textures/item/bottled_cloud.png
```

---

## 任务 1：云瓶放置云团

**涉及文件**
- 新增：`src/main/java/dev/quirky/cloud/CloudPlacement.java`
- 新增：`src/main/java/dev/quirky/block/CloudBlock.java`
- 新增：`src/main/java/dev/quirky/ModBlocks.java`
- 修改：`src/main/java/dev/quirky/item/BottledCloudItem.java`
- 修改：`src/main/java/dev/quirky/QuirkyMod.java`
- 新增：`src/test/java/dev/quirky/cloud/CloudPlacementTest.java`
- 新增：`src/test/java/dev/quirky/block/CloudBlockTest.java`
- 修改：`src/test/java/dev/quirky/item/BottledCloudUseTest.java`

### 1.1 查找最近空气方块

先写测试 `CloudPlacementTest`：

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
		when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.STONE.defaultBlockState());
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

运行测试确认失败，然后实现：

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

### 1.2 云团方块

先写 `CloudBlockTest`：

```java
package dev.quirky.block;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

运行测试确认失败，然后实现 `CloudBlock`：

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

新增 `ModBlocks`：

```java
package dev.quirky;

import dev.quirky.block.CloudBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
	public static final CloudBlock CLOUD = new CloudBlock(
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

在 `QuirkyMod.onInitialize()` 中先调用 `ModBlocks.register()`，再调用 `ModItems.register()`。

### 1.3 云团资源

新增：

`src/main/resources/assets/quirky/blockstates/cloud.json`：

```json
{
  "variants": {
    "": {
      "model": "quirky:block/cloud"
    }
  }
}
```

`src/main/resources/assets/quirky/models/block/cloud.json`：

```json
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "quirky:block/cloud"
  }
}
```

用脚本生成半透明白色云朵贴图：

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

### 1.4 云瓶使用逻辑

在 `BottledCloudUseTest` 中新增两个测试：

新增 import：`static org.mockito.ArgumentMatchers.any`、`static org.mockito.Mockito.verify`、`net.minecraft.core.BlockPos`、`net.minecraft.sounds.SoundEvents`、`net.minecraft.world.level.block.Blocks`、`net.minecraft.world.phys.Vec3`、`dev.quirky.ModBlocks`。

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
	when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.STONE.defaultBlockState());
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

运行测试确认失败，然后重写 `BottledCloudItem.use`：

同时给现有两个测试补上：`when(player.getEyePosition())`、`when(player.getLookAngle())`、`when(player.blockInteractionRange())`，并让 `when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.defaultBlockState())`。否则实现改造后，现有测试会因为找不到空气方块而返回 `FAIL`。

```java
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
		player.getItemInHand(hand).consume(1, player);
	}
	return InteractionResult.SUCCESS;
}
```

### 1.5 云瓶贴图验收

- 检查 `build/libs/quirky-0.1.0.jar` 中存在 `assets/quirky/models/item/bottled_cloud.json` 和 `assets/quirky/textures/item/bottled_cloud.png`。
- 如果游戏仍显示紫黑模型，先修资源路径；如果路径正确，再重绘 `bottled_cloud.png` 为清晰的玻璃瓶 + 淡蓝云朵。

### 1.6 提交

```bash
git add src/main src/test src/main/resources/assets/quirky
git commit -m "feat: bottled cloud places temporary replaceable cloud"
```

---

## 任务 2：双开门支持村民等实体

**涉及文件**
- 修改：`src/main/java/dev/quirky/door/DoubleDoorHandler.java`
- 修改：`src/main/java/dev/quirky/mixin/DoubleDoorMixin.java`
- 修改：`src/test/java/dev/quirky/door/DoubleDoorHandlerTest.java`

### 2.1 先写失败测试

在 `DoubleDoorHandlerTest` 中新增：

新增 `import net.minecraft.world.entity.Entity;`。

```java
@Test
void syncsPartnerForNonPlayerEntity() {
	Level level = mock(Level.class);
	when(level.isClientSide()).thenReturn(false);
	when(level.getRandom()).thenReturn(RandomSource.create());
	Entity villager = mock(Entity.class);
	BlockPos pos = new BlockPos(1, 64, 1);
	BlockPos partnerPos = pos.east();
	when(level.getBlockState(pos)).thenReturn(oakDoor(false, DoorHingeSide.LEFT));
	when(level.getBlockState(partnerPos)).thenReturn(oakDoor(false, DoorHingeSide.RIGHT));

	DoubleDoorHandler.sync(level, pos, villager, true);

	verify(level).setBlock(eq(partnerPos), argThat(state -> state.getValue(DoorBlock.OPEN)), anyInt());
}

@Test
void doesNotSyncPartnerAlreadyAtTargetState() {
	Level level = mock(Level.class);
	when(level.isClientSide()).thenReturn(false);
	BlockPos pos = new BlockPos(1, 64, 1);
	BlockPos partnerPos = pos.east();
	when(level.getBlockState(pos)).thenReturn(oakDoor(true, DoorHingeSide.LEFT));
	when(level.getBlockState(partnerPos)).thenReturn(oakDoor(true, DoorHingeSide.RIGHT));

	DoubleDoorHandler.sync(level, pos, mock(Entity.class), true);

	verify(level, never()).setBlock(any(), any(BlockState.class), anyInt());
}
```

把现有测试中旧的 `sync(level, pos, state, player, result)` 调用全部改成 `sync(level, pos, entity, shouldOpen)`：
- `syncsPartnerWhenHandInteractionSucceeds`：补 `when(level.getBlockState(pos))`，调用改为 `sync(level, pos, mock(Player.class), true)`。
- `doesNotSyncWhenInteractionDoesNotConsumeAction`：新签名没有 `result` 参数，删除该测试。
- `doesNotSyncPartnerWithSameHinge`：补 `when(level.getBlockState(pos))`，调用改为 `sync(level, pos, mock(Player.class), true)`。

### 2.2 改造 `DoubleDoorHandler`

不再通过 `DoorBlock.setOpen` 同步伙伴门，避免 A 门同步 B 门、B 门又同步 A 门的递归。直接设置伙伴门状态、播放音效、触发游戏事件：

```java
package dev.quirky.door;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;

public final class DoubleDoorHandler {
	private DoubleDoorHandler() {
	}

	public static void sync(Level level, BlockPos pos, Entity source, boolean shouldOpen) {
		if (level.isClientSide()) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
			pos = pos.below();
			state = level.getBlockState(pos);
		}
		if (!(state.getBlock() instanceof DoorBlock door)) {
			return;
		}

		Direction facing = state.getValue(DoorBlock.FACING);
		Direction side = state.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT
			? facing.getClockWise()
			: facing.getCounterClockWise();
		BlockPos partnerPos = pos.relative(side);
		BlockState partnerState = level.getBlockState(partnerPos);
		if (!isPartner(state, partnerState)) {
			return;
		}
		if (partnerState.getValue(DoorBlock.OPEN) == shouldOpen) {
			return;
		}

		level.setBlock(partnerPos, partnerState.setValue(DoorBlock.OPEN, shouldOpen), 10);
		SoundEvent sound = shouldOpen ? door.type().doorOpen() : door.type().doorClose();
		level.playSound(source, partnerPos, sound, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
		level.gameEvent(source, shouldOpen ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, partnerPos);
	}

	private static boolean isPartner(BlockState state, BlockState partnerState) {
		return partnerState.is(state.getBlock())
			&& partnerState.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
			&& partnerState.getValue(DoorBlock.FACING) == state.getValue(DoorBlock.FACING)
			&& partnerState.getValue(DoorBlock.HINGE) != state.getValue(DoorBlock.HINGE);
	}
}
```

### 2.3 改造 `DoubleDoorMixin`

玩家点击路径在 `useWithoutItem` 开头预同步，村民/AI/风弹路径在 `setOpen` 尾部同步：

```java
@Inject(method = "useWithoutItem", at = @At("HEAD"))
private void quirky$syncBeforeHandUse(
	BlockState state,
	Level level,
	BlockPos pos,
	Player player,
	BlockHitResult hitResult,
	CallbackInfoReturnable<InteractionResult> cir
) {
	if (DoorBlock.isWoodenDoor(level, pos)) {
		DoubleDoorHandler.sync(level, pos, player, !state.getValue(DoorBlock.OPEN));
	}
}

@Inject(method = "setOpen", at = @At("TAIL"))
private void quirky$syncAfterSetOpen(
	@Nullable Entity sourceEntity,
	Level level,
	BlockState state,
	BlockPos pos,
	boolean shouldOpen,
	CallbackInfo ci
) {
	if (DoorBlock.isWoodenDoor(level, pos)) {
		DoubleDoorHandler.sync(level, pos, sourceEntity, shouldOpen);
	}
}
```

### 2.4 运行测试并提交

```bash
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle test --tests dev.quirky.door.DoubleDoorHandlerTest --no-daemon --console=plain
```

全绿后提交：

```bash
git add src/main/java/dev/quirky/door src/main/java/dev/quirky/mixin/DoubleDoorMixin.java src/test/java/dev/quirky/door
git commit -m "fix: sync double doors for villagers and other entities"
```

---

## 任务 3：吃西瓜吐籽

**涉及文件**
- 修改：`src/main/java/dev/quirky/food/MelonSeedHandler.java`
- 修改：`src/test/java/dev/quirky/food/MelonSeedHandlerTest.java`

### 3.1 先写失败测试

重写 `MelonSeedHandlerTest` 中的主测试：

保留 `net.minecraft.world.food.FoodData` import，并在 mock 中补充 `when(player.getFoodData()).thenReturn(mock(FoodData.class))`（西瓜片吃完会走 `FoodProperties.onConsume`，需要 `FoodData` 才能执行）。另外新增 `net.minecraft.world.entity.item.ItemEntity` 和 `net.minecraft.sounds.SoundEvents` import。

```java
@Test
void spitsSeedAsItemEntityWhenFinishingLastMelonSlice() {
	ServerPlayer player = mock(ServerPlayer.class);
	when(player.hasInfiniteMaterials()).thenReturn(false);
	when(player.getRandom()).thenReturn(RandomSource.create());
	when(player.getFoodData()).thenReturn(mock(FoodData.class));
	when(player.getEyePosition()).thenReturn(new Vec3(0.5, 64.5, 0.5));
	when(player.getLookAngle()).thenReturn(new Vec3(1.0, 0.0, 0.0));
	ServerLevel level = mock(ServerLevel.class);
	when(player.level()).thenReturn(level);
	PlayerAdvancements advancements = mock(PlayerAdvancements.class);
	when(advancements.getTriggerMapForType(any(CriterionTrigger.class))).thenReturn(Collections.emptyMap());
	when(player.getAdvancements()).thenReturn(advancements);

	ItemStack slice = new ItemStack(Items.MELON_SLICE);
	ItemStack result = MelonSeedHandler.finishUsing(slice, level, player);

	assertSame(slice, result);
	assertTrue(slice.isEmpty());
	verify(player).playSound(SoundEvents.FOX_SPIT, 1.0F, 1.0F);
	verify(level).addFreshEntity(argThat(entity ->
		entity instanceof ItemEntity item
			&& item.getItem().is(Items.MELON_SEEDS)
			&& item.hasPickUpDelay()
	));
}
```

运行测试确认失败。

### 3.2 实现吐籽

```java
package dev.quirky.food;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class MelonSeedHandler {
	private MelonSeedHandler() {
	}

	public static ItemStack finishUsing(ItemStack stack, Level level, LivingEntity entity) {
		boolean isMelonSlice = stack.is(Items.MELON_SLICE);
		ItemStack result = stack.finishUsingItem(level, entity);
		if (entity instanceof ServerPlayer player
			&& isMelonSlice
			&& !player.hasInfiniteMaterials()
			&& level instanceof ServerLevel serverLevel) {
			ItemStack seed = new ItemStack(Items.MELON_SEEDS);
			ItemEntity item = new ItemEntity(
				serverLevel,
				player.getEyePosition().x + player.getLookAngle().x,
				player.getEyePosition().y + player.getLookAngle().y,
				player.getEyePosition().z + player.getLookAngle().z,
				seed
			);
			item.setPickUpDelay(40);
			item.setThrower(player);
			item.setDeltaMovement(player.getLookAngle().scale(0.3));
			serverLevel.addFreshEntity(item);
			player.playSound(SoundEvents.FOX_SPIT, 1.0F, 1.0F);
		}
		return result;
	}
}
```

### 3.3 运行测试并提交

```bash
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle test --tests dev.quirky.food.MelonSeedHandlerTest --no-daemon --console=plain
```

全绿后提交：

```bash
git add src/main/java/dev/quirky/food src/test/java/dev/quirky/food
git commit -m "fix: spit melon seed as item entity with pickup delay"
```

---

## 任务 4：地图 tooltip 肉色纸边

**涉及文件**
- 修改：`build.gradle`
- 修改：`src/client/java/dev/quirky/client/tooltips/ClientMapTooltipComponent.java`
- 新增：`src/test/java/dev/quirky/client/tooltips/ClientMapTooltipComponentTest.java`

### 4.1 让测试能编译客户端类

在 `build.gradle` 的 `test {}` 前添加：

```groovy
sourceSets {
	test {
		compileClasspath += sourceSets.client.output
		runtimeClasspath += sourceSets.client.output
	}
}
```

### 4.2 先写失败测试

```java
package dev.quirky.client.tooltips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import dev.quirky.TestBootstrap;
import net.minecraft.client.gui.Font;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClientMapTooltipComponentTest {
	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	@Test
	void keepsVanillaParchmentBorderAroundMap() {
		ClientMapTooltipComponent component = new ClientMapTooltipComponent(new MapId(1));
		Font font = mock(Font.class);

		assertEquals(71, component.getWidth(font));
		assertEquals(71, component.getHeight(font));
	}
}
```

运行测试确认失败。

### 4.3 实现纸边

```java
package dev.quirky.client.tooltips;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class ClientMapTooltipComponent implements ClientTooltipComponent {
	private static final Identifier MAP_BACKGROUND = Identifier.withDefaultNamespace("textures/map/map_background.png");
	private static final int TOTAL_SIZE = 71;
	private static final int MAP_SCALE = 2;
	private final MapId mapId;
	private final MapRenderState renderState = new MapRenderState();

	public ClientMapTooltipComponent(MapId mapId) {
		this.mapId = mapId;
	}

	@Override
	public int getWidth(Font font) {
		return TOTAL_SIZE;
	}

	@Override
	public int getHeight(Font font) {
		return TOTAL_SIZE;
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
		graphics.blit(RenderPipelines.GUI_TEXTURED, MAP_BACKGROUND, -7, -7, 0, 0, 142, 142, 64, 64);
		graphics.map(renderState);
		graphics.pose().popMatrix();
	}
}
```

### 4.4 运行测试并提交

```bash
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle test --tests dev.quirky.client.tooltips.ClientMapTooltipComponentTest --no-daemon --console=plain
```

全绿后提交：

```bash
git add build.gradle src/client/java/dev/quirky/client/tooltips/ClientMapTooltipComponent.java src/test/java/dev/quirky/client/tooltips/ClientMapTooltipComponentTest.java
git commit -m "feat: draw vanilla parchment border around map tooltip"
```

---

## 任务 5：收割粒子、挥臂与音效

**涉及文件**
- 修改：`src/main/java/dev/quirky/harvest/HarvestFx.java`
- 修改：`src/main/java/dev/quirky/harvest/HarvestHandler.java`
- 修改：`src/test/java/dev/quirky/harvest/HarvestFxTest.java`

### 5.1 先写失败测试

在 `HarvestFxTest` 中新增：

新增 import：`net.minecraft.sounds.SoundSource`、`net.minecraft.sounds.SoundEvents`。

```java
@Test
void playsBreakSoundAndReplantSound() {
	ServerLevel level = mock(ServerLevel.class);
	Player player = mock(Player.class);
	BlockPos pos = new BlockPos(1, 64, 1);
	BlockState wheat = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE);

	HarvestFx.playBreak(level, player, InteractionHand.MAIN_HAND, pos, wheat);
	HarvestFx.playReplant(level, pos, wheat);

	verify(level).playSound(isNull(), eq(pos), eq(wheat.getSoundType().getBreakSound()), eq(SoundSource.BLOCKS), anyFloat(), anyFloat());
	verify(level).playSound(isNull(), eq(pos), eq(SoundEvents.CROP_PLANTED), eq(SoundSource.BLOCKS), anyFloat(), anyFloat());
	verify(level).levelEvent(isNull(), eq(2001), eq(pos), eq(Block.getId(wheat)));
	verify(player).swing(InteractionHand.MAIN_HAND, true);
}
```

运行测试确认失败。

### 5.2 实现音效

```java
package dev.quirky.harvest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;

final class HarvestFx {
	private HarvestFx() {
	}

	static void playBreak(ServerLevel level, Player player, InteractionHand hand, BlockPos pos, BlockState state) {
		level.levelEvent(null, 2001, pos, Block.getId(state));
		level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 0.9F);
		player.swing(hand, true);
	}

	static void playReplant(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.is(Blocks.NETHER_WART)) {
			level.playSound(null, pos, SoundEvents.NETHER_WART_PLANTED, SoundSource.BLOCKS, 1.0F, 0.9F);
		} else {
			level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 0.9F);
		}
	}
}
```

把 `HarvestHandler` 中所有 `HarvestFx.play(...)` 改成 `HarvestFx.playBreak(...)`；在种子作物和下界疣补种成功后，调用 `HarvestFx.playReplant(level, pos, newState)`。

### 5.3 运行测试并提交

```bash
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle test --tests dev.quirky.harvest.HarvestFxTest --no-daemon --console=plain
```

全绿后提交：

```bash
git add src/main/java/dev/quirky/harvest src/test/java/dev/quirky/harvest
git commit -m "feat: play break and replant sounds while harvesting"
```

---

## 任务 6：整体验收

### 6.1 全量构建与测试

```bash
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle clean build --no-daemon --console=plain
```

预期：`BUILD SUCCESSFUL`，所有 JUnit 测试通过。

### 6.2 专用服务器冒烟

```bash
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle runServer --no-daemon --console=plain
```

预期：出现 `Quirky loaded`，无 Mixin/方块注册异常。

### 6.3 桌面客户端验收清单

- 云瓶右键生成临时云团；云团下方有白色粒子；云团可被普通方块原位替换；实体穿过时缓慢下落；10 秒后消失。
- 生存模式消耗云瓶并返还玻璃瓶；创造模式不消耗；准心范围内没有空气时不消耗。
- 村民或其他实体开门时，相邻匹配门同步开/关；玩家手点门仍然有效；铁门不受影响。
- 吃西瓜时从玩家面前吐出一颗种子，种子 40 tick 后才能捡回，并播放吐籽音效；不再直接进背包。
- 地图 tooltip 显示原版肉色地图纸边，尺寸为 71x71。
- 收割作物/下界疣/西瓜/南瓜/可可豆时有破坏粒子、玩家挥臂和对应音效；补种成功有补种音效。
