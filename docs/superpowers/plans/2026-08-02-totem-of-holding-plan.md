# 保留图腾（Totem of Holding）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 玩家死亡时，背包物品存入死亡点生成的悬浮图腾实体，击打 3 次取回；未取回再死则旧图腾破裂散落。

**Architecture:** 纯逻辑 `TotemOfHoldingLogic`（可单测）+ `TotemEntity` 实体（Entity 基类，无 AI，`hurtServer` 计数击打，ValueOutput/Input 序列化）+ `ServerPlayerMixin` 在 `die()` 的 `dropAllDeathLoot` 调用前拦截（收集→生成→清空背包，让原版掉落空背包）+ 客户端 `TotemEntityRenderer`（复用 ItemEntityRenderState 物品渲染管线）。

**Tech Stack:** Minecraft 26.2 官方映射、Fabric API、Mixin（SpongePowered）、JUnit 5 + Mockito（测试）。

## Global Constraints

- 构建：`JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH gradle clean build --no-daemon --console=plain`；单元测试 `gradle test`，Java 25，26.2 官方映射
- Tab 缩进；资源路径 `lower_snake_case`；机制包 `dev.quirky.totem`（实体逻辑）/ `dev.quirky.client.totem`（渲染）
- 物品注册模式照抄 `ModItems`（静态字段 + `Registry.register` + `CreativeModeTabEvents`，页签 `TOOLS_AND_UTILITIES`）
- 实体数据：`addAdditionalSaveData(ValueOutput)` / `readAdditionalSaveData(ValueInput)`；物品列表用 `ItemStackWithSlot.CODEC`（`net.minecraft.world.ItemStackWithSlot` record：`slot()` / `stack()`）
- 已实锤 API（26.2 源码验证）：`Entity.hurtServer(ServerLevel, DamageSource, float)` 是唯一服务端伤害入口（`hurt` 为 @Deprecated final 转发）；近战判定 `source.is(DamageTypes.PLAYER_ATTACK)`；`GameRules.KEEP_INVENTORY` 经 `level.getGameRules().get(GameRules.KEEP_INVENTORY)` 返回 `Boolean`；`Inventory.clearContent()` 清 41 槽；`Inventory.getFreeSlot()` 只查 items 36 格；消失诅咒判定 `EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)`
- 序列化测试构造：`TagValueOutput.createWithContext(ProblemReporter.nope(), provider)`、`TagValueInput.create(ProblemReporter.nope(), provider, tag)`
- 贴图必须先出 `build/previews/` 预览，用户确认后才落地正式资源（任务 4 是确认门）
- 规格：`docs/superpowers/specs/2026-08-02-totem-of-holding-design.md`（行为以规格为准；实现中发现的稳定约束需同步回规格）

---

### Task 1: TotemOfHoldingLogic 纯逻辑（判定 / 收集 / 归还）

**Files:**
- Create: `src/main/java/dev/quirky/totem/TotemOfHoldingLogic.java`
- Test: `src/test/java/dev/quirky/totem/TotemOfHoldingLogicTest.java`

**Interfaces:**
- Consumes: `TestBootstrap.boot()`、`TestBootstrap.bindItem(Item)`（现有）；`Inventory(player, new EntityEquipment())` 真实构造模式（EquipSwapServerTest 已用）
- Produces:
  - `boolean shouldSpawnTotem(Player player, DamageSource source, boolean keepInventory)` — 非旁观 && 非创造 && !keepInventory && 伤害来源不是玩家
  - `List<ItemStackWithSlot> collectInventory(Player player)` — 遍历 `getContainerSize()`，跳过空槽与消失诅咒物品，`stack.copy()` 存 `new ItemStackWithSlot(slot, stack)`
  - `List<ItemStack> restoreToPlayer(Player player, List<ItemStackWithSlot> stored)` — 原槽位空则原位放回；否则 `getFreeSlot()`；满则加入返回列表（overflow）

- [ ] **Step 1: 写失败测试** `src/test/java/dev/quirky/totem/TotemOfHoldingLogicTest.java`

```java
package dev.quirky.totem;

import dev.quirky.TestBootstrap;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TotemOfHoldingLogicTest {

	@BeforeAll
	static void boot() {
		TestBootstrap.boot();
		TestBootstrap.bindItem(Items.DIAMOND_SWORD);
		TestBootstrap.bindItem(Items.IRON_HELMET);
		TestBootstrap.bindItem(Items.STONE);
	}

	// ---- shouldSpawnTotem 判定矩阵 ----

	@Test
	void shouldSpawnTotem_returnsTrueForNormalDeath() {
		Player player = mock(Player.class);
		DamageSource source = mock(DamageSource.class);
		assertTrue(TotemOfHoldingLogic.shouldSpawnTotem(player, source, false));
	}

	@Test
	void shouldSpawnTotem_returnsFalseForPlayerKill() {
		Player player = mock(Player.class);
		DamageSource source = mock(DamageSource.class);
		when(source.getEntity()).thenReturn(mock(Player.class));
		assertFalse(TotemOfHoldingLogic.shouldSpawnTotem(player, source, false));
	}

	@Test
	void shouldSpawnTotem_returnsFalseInCreative() {
		Player player = mock(Player.class);
		when(player.hasInfiniteMaterials()).thenReturn(true);
		assertFalse(TotemOfHoldingLogic.shouldSpawnTotem(player, mock(DamageSource.class), false));
	}

	@Test
	void shouldSpawnTotem_returnsFalseForSpectator() {
		Player player = mock(Player.class);
		when(player.isSpectator()).thenReturn(true);
		assertFalse(TotemOfHoldingLogic.shouldSpawnTotem(player, mock(DamageSource.class), false));
	}

	@Test
	void shouldSpawnTotem_returnsFalseWhenKeepInventory() {
		assertFalse(TotemOfHoldingLogic.shouldSpawnTotem(mock(Player.class), mock(DamageSource.class), true));
	}

	// ---- collectInventory ----

	@Test
	void collectInventory_keepsSlotAndSkipsEmptyAndVanishing() {
		Player player = mock(Player.class);
		Inventory inventory = new Inventory(player, new EntityEquipment());
		when(player.getInventory()).thenReturn(inventory);
		inventory.setItem(3, new ItemStack(Items.DIAMOND_SWORD));
		ItemStack cursed = new ItemStack(Items.DIAMOND_SWORD);
		cursed.enchant(Enchantments.VANISHING_CURSE, 1);
		inventory.setItem(5, cursed);
		inventory.setItem(36, new ItemStack(Items.IRON_HELMET)); // 盔甲槽

		List<ItemStackWithSlot> stored = TotemOfHoldingLogic.collectInventory(player);

		assertEquals(2, stored.size());
		assertEquals(3, stored.get(0).slot());
		assertTrue(stored.get(0).stack().is(Items.DIAMOND_SWORD));
		assertEquals(36, stored.get(1).slot());
		assertTrue(stored.get(1).stack().is(Items.IRON_HELMET));
	}

	// ---- restoreToPlayer 三级降级 ----

	@Test
	void restoreToPlayer_putsBackIntoOriginalSlot() {
		Player player = mock(Player.class);
		Inventory inventory = new Inventory(player, new EntityEquipment());
		when(player.getInventory()).thenReturn(inventory);
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);

		List<ItemStack> overflow = TotemOfHoldingLogic.restoreToPlayer(
			player, List.of(new ItemStackWithSlot(3, sword)));

		assertTrue(overflow.isEmpty());
		assertEquals(sword, inventory.getItem(3));
	}

	@Test
	void restoreToPlayer_fallsBackToFreeSlotWhenOriginalOccupied() {
		Player player = mock(Player.class);
		Inventory inventory = new Inventory(player, new EntityEquipment());
		when(player.getInventory()).thenReturn(inventory);
		inventory.setItem(3, new ItemStack(Items.STONE));
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);

		TotemOfHoldingLogic.restoreToPlayer(player, List.of(new ItemStackWithSlot(3, sword)));

		assertTrue(inventory.getItem(3).is(Items.STONE));
		assertTrue(inventory.hasAnyMatching(stack -> stack.is(Items.DIAMOND_SWORD)));
	}

	@Test
	void restoreToPlayer_returnsOverflowWhenItemsFull() {
		Player player = mock(Player.class);
		Inventory inventory = new Inventory(player, new EntityEquipment());
		when(player.getInventory()).thenReturn(inventory);
		for (int i = 0; i < 36; i++) {
			inventory.setItem(i, new ItemStack(Items.STONE));
		}
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);

		List<ItemStack> overflow = TotemOfHoldingLogic.restoreToPlayer(
			player, List.of(new ItemStackWithSlot(0, sword)));

		assertEquals(1, overflow.size());
		assertTrue(overflow.getFirst().is(Items.DIAMOND_SWORD));
	}
}
```

- [ ] **Step 2: 运行确认失败**

Run: `gradle test --tests 'dev.quirky.totem.TotemOfHoldingLogicTest'`
Expected: 编译失败（TotemOfHoldingLogic 不存在）

- [ ] **Step 3: 实现** `src/main/java/dev/quirky/totem/TotemOfHoldingLogic.java`

```java
package dev.quirky.totem;

import net.minecraft.core.component.EnchantmentEffectComponents;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.List;

public final class TotemOfHoldingLogic {
	private TotemOfHoldingLogic() {
	}

	public static boolean shouldSpawnTotem(Player player, DamageSource source, boolean keepInventory) {
		return !player.isSpectator()
			&& !player.hasInfiniteMaterials()
			&& !keepInventory
			&& !(source.getEntity() instanceof Player);
	}

	public static List<ItemStackWithSlot> collectInventory(Player player) {
		Inventory inventory = player.getInventory();
		List<ItemStackWithSlot> stored = new ArrayList<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty() || EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
				continue;
			}
			stored.add(new ItemStackWithSlot(i, stack.copy()));
		}
		return stored;
	}

	public static List<ItemStack> restoreToPlayer(Player player, List<ItemStackWithSlot> stored) {
		Inventory inventory = player.getInventory();
		List<ItemStack> overflow = new ArrayList<>();
		for (ItemStackWithSlot entry : stored) {
			ItemStack stack = entry.stack();
			int slot = entry.slot();
			if (slot < inventory.getContainerSize() && inventory.getItem(slot).isEmpty()) {
				inventory.setItem(slot, stack);
			} else {
				int free = inventory.getFreeSlot();
				if (free != -1) {
					inventory.setItem(free, stack);
				} else {
					overflow.add(stack);
				}
			}
		}
		return overflow;
	}
}
```

- [ ] **Step 4: 运行确认通过**

Run: `gradle test --tests 'dev.quirky.totem.TotemOfHoldingLogicTest'`
Expected: 9 个测试全过（若 `inventory.setItem(36, ...)` 或 `getFreeSlot` 行为与预期不符，按 mcsrc 实测调整断言，并把事实回写 Global Constraints）

- [ ] **Step 5: 提交**

```bash
git add src/main/java/dev/quirky/totem/TotemOfHoldingLogic.java src/test/java/dev/quirky/totem/TotemOfHoldingLogicTest.java
git commit -m "feat: add totem of holding core logic with tests"
```

---

### Task 2: TotemEntity 实体（击打取回 / NBT 序列化 / 破裂）

**Files:**
- Create: `src/main/java/dev/quirky/ModEntities.java`
- Create: `src/main/java/dev/quirky/totem/TotemEntity.java`
- Modify: `src/main/java/dev/quirky/QuirkyMod.java`（onInitialize 调 `ModEntities.register()`；仅当使用 `Registry.register` 路径时需要——Fabric `build(id)` 已注册则不需要）
- Test: `src/test/java/dev/quirky/totem/TotemEntityTest.java`

**Interfaces:**
- Consumes: Task 1 的 `TotemOfHoldingLogic.restoreToPlayer`；`ModItems.TOTEM_OF_HOLDING`（任务 4 才创建——**本任务先不引用物品**，渲染素材由任务 4/5 接上）
- Produces:
  - `ModEntities.TOTEM` — `EntityType<TotemEntity>`（`EntityType.Builder.of(TotemEntity::new, MobCategory.MISC).sized(0.5F, 0.6F).clientTrackingRange(8).build(QuirkyMod.id("totem_of_holding"))`，Fabric build 已注册）
  - `TotemEntity.initStored(UUID owner, List<ItemStackWithSlot> stored)`
  - `TotemEntity.getOwner()` / `getStored()`
  - `static void TotemEntity.breakForOwner(ServerPlayer player)` — 遍历 `player.getServer().getAllLevels()`，`level.getEntities(TotemEntity.class, e -> player.getUUID().equals(e.getOwner()))` → 每物品 `totem.spawnAtLocation((ServerLevel) totem.level(), stack)` → `totem.discard()`
  - 击打：`hurtServer` 计数，`HITS_TO_RETRIEVE = 3`；第 3 次 `restoreToPlayer` + overflow 掉落 + `playSound(SoundEvents.TOTEM_USE)` + `discard()`

- [ ] **Step 1: 写失败测试** `src/test/java/dev/quirky/totem/TotemEntityTest.java`

```java
package dev.quirky.totem;

import dev.quirky.ModEntities;
import dev.quirky.TestBootstrap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TotemEntityTest {

	private static HolderLookup.Provider provider;
	private static ServerLevel level;

	@BeforeAll
	static void boot() {
		TestBootstrap.boot();
		TestBootstrap.bindItem(Items.DIAMOND_SWORD);
		provider = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY); // 本身即 HolderLookup.Provider
		level = mock(ServerLevel.class);
		when(level.isClientSide()).thenReturn(false);
	}

	private static TotemEntity newTotem() {
		return new TotemEntity(ModEntities.TOTEM, level);
	}

	@Test
	void saveAndLoad_roundTripsStoredItems() {
		TotemEntity totem = newTotem();
		UUID owner = UUID.randomUUID();
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		sword.setHoverName(net.minecraft.network.chat.Component.literal("Excalibur"));
		totem.initStored(owner, List.of(new ItemStackWithSlot(3, sword)));

		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, provider);
		totem.addAdditionalSaveData(out);
		CompoundTag tag = out.buildResult();

		TotemEntity loaded = newTotem();
		loaded.readAdditionalSaveData(TagValueInput.create(ProblemReporter.DISCARDING, provider, tag));

		assertEquals(owner, loaded.getOwner());
		assertEquals(1, loaded.getStored().size());
		assertEquals(3, loaded.getStored().getFirst().slot());
		assertTrue(loaded.getStored().getFirst().stack().is(Items.DIAMOND_SWORD));
		assertEquals("Excalibur", loaded.getStored().getFirst().stack().getHoverName().getString());
	}

	@Test
	void hurtServer_countsMeleeHitsAndRetrievesOnThird() {
		TotemEntity totem = newTotem();
		totem.initStored(UUID.randomUUID(), List.of(new ItemStackWithSlot(3, new ItemStack(Items.DIAMOND_SWORD))));
		Player player = mock(Player.class);
		when(player.getInventory()).thenReturn(new Inventory(player, new EntityEquipment())); // restoreToPlayer 必需
		DamageSource melee = mock(DamageSource.class);
		when(melee.getEntity()).thenReturn(player);
		when(melee.is(DamageTypes.PLAYER_ATTACK)).thenReturn(true);

		assertFalse(totem.hurtServer(level, melee, 1.0F));
		assertFalse(totem.hurtServer(level, melee, 1.0F));
		assertFalse(totem.hurtServer(level, melee, 1.0F));

		assertTrue(totem.isRemoved());
	}

	@Test
	void hurtServer_ignoresNonPlayerAndNonMeleeDamage() {
		TotemEntity totem = newTotem();
		DamageSource fire = mock(DamageSource.class);
		when(fire.getEntity()).thenReturn(null);

		assertFalse(totem.hurtServer(level, fire, 5.0F));
		assertFalse(totem.isRemoved());
	}
}

// 已实锤：Entity 构造与 discard 在 mock(Level) 环境安全——levelCallback 默认
// EntityInLevelCallback.NULL（Entity.java:280），getNextEntityId() 默认 0，
// playSound/spawnAtLocation 为空操作（Mockito void 默认）。无需 stub level。
```

- [ ] **Step 2: 运行确认失败**

Run: `gradle test --tests 'dev.quirky.totem.TotemEntityTest'`
Expected: 编译失败（ModEntities/TotemEntity 不存在）

- [ ] **Step 3: 实现** `src/main/java/dev/quirky/ModEntities.java`

```java
package dev.quirky;

import dev.quirky.totem.TotemEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final EntityType<TotemEntity> TOTEM = EntityType.Builder.of(TotemEntity::new, MobCategory.MISC)
		.sized(0.5F, 0.6F)
		.clientTrackingRange(8)
		.build(QuirkyMod.id("totem_of_holding"));

	private ModEntities() {
	}
}
```

- [ ] **Step 4: 实现** `src/main/java/dev/quirky/totem/TotemEntity.java`

```java
package dev.quirky.totem;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TotemEntity extends Entity {
	private static final String TAG_OWNER_MOST = "OwnerMost";
	private static final String TAG_OWNER_LEAST = "OwnerLeast";
	private static final String TAG_ITEMS = "Items";
	private static final int HITS_TO_RETRIEVE = 3;

	private UUID owner;
	private List<ItemStackWithSlot> stored = List.of();
	private final Map<UUID, Integer> hits = new HashMap<>();

	public TotemEntity(EntityType<TotemEntity> type, Level level) {
		super(type, level);
	}

	public void initStored(UUID owner, List<ItemStackWithSlot> stored) {
		this.owner = owner;
		this.stored = stored;
	}

	public UUID getOwner() {
		return this.owner;
	}

	public List<ItemStackWithSlot> getStored() {
		return this.stored;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (!this.level().isClientSide()
			&& source.getEntity() instanceof Player player
			&& source.is(DamageTypes.PLAYER_ATTACK)) {
			int count = this.hits.merge(player.getUUID(), 1, Integer::sum);
			if (count >= HITS_TO_RETRIEVE) {
				this.retrieveFor(player);
			}
		}
		return false;
	}

	private void retrieveFor(Player player) {
		for (ItemStack stack : TotemOfHoldingLogic.restoreToPlayer(player, this.stored)) {
			this.spawnAtLocation((ServerLevel) this.level(), stack);
		}
		this.playSound(SoundEvents.TOTEM_USE, 1.0F, 1.0F);
		this.discard();
	}

	public static void breakForOwner(ServerPlayer player) {
		for (ServerLevel level : player.getServer().getAllLevels()) {
			for (TotemEntity totem : level.getEntities(TotemEntity.class, e -> player.getUUID().equals(e.getOwner()))) {
				for (ItemStackWithSlot entry : totem.getStored()) {
					totem.spawnAtLocation(level, entry.stack());
				}
				totem.discard();
			}
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		if (this.owner != null) {
			output.putLong(TAG_OWNER_MOST, this.owner.getMostSignificantBits());
			output.putLong(TAG_OWNER_LEAST, this.owner.getLeastSignificantBits());
		}
		output.store(TAG_ITEMS, ItemStackWithSlot.CODEC.listOf(), this.stored);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		long most = input.getLongOr(TAG_OWNER_MOST, 0L);
		long least = input.getLongOr(TAG_OWNER_LEAST, 0L);
		this.owner = most == 0L && least == 0L ? null : new UUID(most, least);
		this.stored = input.read(TAG_ITEMS, ItemStackWithSlot.CODEC.listOf()).orElse(List.of());
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
	}
}
```

- [ ] **Step 5: 运行确认通过**

Run: `gradle test --tests 'dev.quirky.totem.TotemEntityTest'`
Expected: 3 个测试全过。若 `TagValueOutput` 取 tag 方法名不同（`getCompoundTag` vs 其他），查 `TagValueOutput.java` 源码修正；若 `hurtServer` 中 `spawnAtLocation`/`discard` 在 mock level 下 NPE，stub 对应调用。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/dev/quirky/ModEntities.java src/main/java/dev/quirky/totem/TotemEntity.java src/test/java/dev/quirky/totem/TotemEntityTest.java
git commit -m "feat: add totem entity with retrieve and persistence"
```

---

### Task 3: ServerPlayerMixin 死亡拦截

**Files:**
- Create: `src/main/java/dev/quirky/mixin/ServerPlayerMixin.java`
- Modify: `src/main/resources/quirky.mixins.json`（`"mixins"` 数组加 `"ServerPlayerMixin"`）

**Interfaces:**
- Consumes: `TotemOfHoldingLogic.shouldSpawnTotem` / `collectInventory`；`TotemEntity.breakForOwner` / `initStored`；`ModEntities.TOTEM`
- Produces: 无（拦截点行为：触发则破裂旧图腾 → 收集 → 生成新图腾 → `inventory.clearContent()`）

- [ ] **Step 1: 实现 Mixin** `src/main/java/dev/quirky/mixin/ServerPlayerMixin.java`

```java
package dev.quirky.mixin;

import dev.quirky.ModEntities;
import dev.quirky.totem.TotemEntity;
import dev.quirky.totem.TotemOfHoldingLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
	@Inject(
		method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerPlayer;dropAllDeathLoot(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/damagesource/DamageSource;)V",
			shift = At.Shift.BEFORE
		)
	)
	private void quirky$totemProtectInventory(DamageSource source, CallbackInfo ci) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		boolean keepInventory = player.serverLevel().getGameRules().get(GameRules.KEEP_INVENTORY);
		if (!TotemOfHoldingLogic.shouldSpawnTotem(player, source, keepInventory)) {
			return;
		}
		TotemEntity.breakForOwner(player);
		List<ItemStackWithSlot> stored = TotemOfHoldingLogic.collectInventory(player);
		if (stored.isEmpty()) {
			return; // 空背包死亡不生成空图腾
		}
		TotemEntity totem = new TotemEntity(ModEntities.TOTEM, player.serverLevel());
		BlockPos pos = player.blockPosition();
		if (pos.getY() < 0) {
			pos = pos.atY(0);
		}
		totem.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		totem.initStored(player.getUUID(), stored);
		player.serverLevel().addFreshEntity(totem);
		player.getInventory().clearContent();
	}
}
```

- [ ] **Step 2: 注册 Mixin**

`src/main/resources/quirky.mixins.json` 的 `"mixins"` 数组改为：`["DoubleDoorMixin", "MapTooltipMixin", "MelonSeedMixin", "ServerPlayerMixin"]`

- [ ] **Step 3: 构建验证**

Run: `gradle build`
Expected: BUILD SUCCESSFUL（mixin 注入点解析成功；`defaultRequire: 1` 会强制验证 target 存在）

- [ ] **Step 4: 提交**

```bash
git add src/main/java/dev/quirky/mixin/ServerPlayerMixin.java src/main/resources/quirky.mixins.json
git commit -m "feat: intercept player death to spawn holding totem"
```

---

### Task 4: 图腾物品 + 贴图（预览确认门）

**Files:**
- Create: `build/previews/totem_item_v1.png`（20x 放大预览，**先展示给用户确认**）
- Modify: `src/main/java/dev/quirky/ModItems.java`（加 `TOTEM_OF_HOLDING`：`new Item(new Item.Properties().stacksTo(1).setId(TOTEM_ID))` + register 里 `Registry.register` + 创造页签 `TOOLS_AND_UTILITIES`）
- Create: `src/main/resources/assets/quirky/models/item/totem_of_holding.json`
- Create: `src/main/resources/assets/quirky/textures/item/totem_of_holding.png`（16x16 RGBA，零透明）
- Modify: `src/main/resources/assets/quirky/lang/en_us.json` / `zh_cn.json`（`item.quirky.totem_of_holding`）

**Interfaces:**
- Consumes: 无（贴图素材）
- Produces: `ModItems.TOTEM_OF_HOLDING`（任务 5 渲染引用）

- [ ] **Step 1: 生成预览**

用 python3 生成 16x16 图腾贴图并放大 20x 输出 `build/previews/totem_item_v1.png` + ASCII 渲染展示。设计参考 Quark 图腾：竖向长条徽章，米白/金色主体 + 中间菱形宝石（蓝/紫色），深色描边。**展示给用户，等待确认后才继续 Step 2**。

- [ ] **Step 2: 落地正式资源**（用户确认后）

- `ModItems` 加物品注册（照 BOTTLED_CLOUD 模式）
- `models/item/totem_of_holding.json`：
```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "quirky:item/totem_of_holding"
  }
}
```
- `textures/item/totem_of_holding.png` 16x16 RGBA（与预览同像素）
- lang 文件加：`"item.quirky.totem_of_holding": "Totem of Holding"` / `"保留图腾"`

- [ ] **Step 3: 构建验证 + 提交**

Run: `gradle build`
Expected: BUILD SUCCESSFUL

```bash
git add src/main/java/dev/quirky/ModItems.java src/main/resources/assets/quirky
git commit -m "feat: add totem of holding item with texture"
```

---

### Task 5: 客户端渲染（悬浮图腾）

**Files:**
- Create: `src/client/java/dev/quirky/client/totem/TotemEntityRenderState.java`
- Create: `src/client/java/dev/quirky/client/totem/TotemEntityRenderer.java`
- Modify: `src/client/java/dev/quirky/client/QuirkyModClient.java`（`EntityRenderers.register(ModEntities.TOTEM, TotemEntityRenderer::new)`）

**Interfaces:**
- Consumes: `ModEntities.TOTEM`、`ModItems.TOTEM_OF_HOLDING`；26.2 渲染管线：`EntityRenderer<T, S extends EntityRenderState>` 的 `createRenderState()` / `extractRenderState(T, S, float)` / `submit(S, PoseStack, SubmitNodeCollector, CameraRenderState)`（模板：`ItemEntityRenderer`）；`ItemEntityRenderState.extractItemGroupRenderState(Entity, ItemStack, ItemModelResolver)`（已实锤签名）；`context.getItemModelResolver()`

- [ ] **Step 1: 实现** `src/client/java/dev/quirky/client/totem/TotemEntityRenderState.java`

```java
package dev.quirky.client.totem;

import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;

public class TotemEntityRenderState extends ItemEntityRenderState {
}
```

- [ ] **Step 2: 实现** `src/client/java/dev/quirky/client/totem/TotemEntityRenderer.java`

```java
package dev.quirky.client.totem;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.quirky.ModEntities;
import dev.quirky.ModItems;
import dev.quirky.totem.TotemEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public class TotemEntityRenderer extends EntityRenderer<TotemEntity, TotemEntityRenderState> {
	private final ItemModelResolver itemModelResolver;

	public TotemEntityRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemModelResolver = context.getItemModelResolver();
		this.shadowRadius = 0.2F;
		this.shadowStrength = 0.5F;
	}

	@Override
	public TotemEntityRenderState createRenderState() {
		return new TotemEntityRenderState();
	}

	@Override
	public void extractRenderState(TotemEntity entity, TotemEntityRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.extractItemGroupRenderState(entity, new ItemStack(ModItems.TOTEM_OF_HOLDING), this.itemModelResolver);
	}

	@Override
	public void submit(TotemEntityRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
		if (!state.item.isEmpty()) {
			poseStack.pushPose();
			float bob = Mth.sin(state.ageInTicks / 10.0F) * 0.1F + 0.15F;
			poseStack.translate(0.0F, bob, 0.0F);
			poseStack.mulPose(Axis.YP.rotation(state.ageInTicks / 20.0F));
			state.item.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
			poseStack.popPose();
			super.submit(state, poseStack, submitNodeCollector, camera);
		}
	}

	@Override
	public Identifier getTextureLocation(TotemEntity entity) {
		return null; // 纯物品渲染，无实体贴图
	}
}
```

- [ ] **Step 3: 注册渲染器**

`QuirkyModClient.onInitializeClient` 末尾加：
```java
EntityRenderers.register(ModEntities.TOTEM, TotemEntityRenderer::new);
```
（import `dev.quirky.ModEntities`、`dev.quirky.client.totem.TotemEntityRenderer`、`net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry`）

- [ ] **Step 4: 构建验证 + 提交**

Run: `gradle build`
Expected: BUILD SUCCESSFUL（若 `EntityRenderer<T, S>` 泛型/方法签名与预期不符，以 `ItemEntityRenderer.java` 源码为准修正；`Identifier` 若为 `ResourceLocation` 旧名同样修正）

```bash
git add src/client/java/dev/quirky/client/totem src/client/java/dev/quirky/client/QuirkyModClient.java
git commit -m "feat: render floating totem entity"
```

---

### Task 6: 文档同步 + 全量验证

**Files:**
- Modify: `README.md`（功能列表加保留图腾）
- Modify: `features.md`（同步）

- [ ] **Step 1: 更新文档**

README 功能列表（照现有条目风格）：
> - **保留图腾**：死亡时背包物品集中存入死亡点生成的悬浮图腾（非 PVP 击杀、非创造、`keepInventory` 关闭时生效），任何玩家击打 3 次取回，按原槽位归还；未取回再死则旧图腾破裂散落。消失诅咒物品不保存；经验照常掉落。

features.md 同步同内容。

- [ ] **Step 2: 全量构建 + 测试**

Run: `gradle clean build`
Expected: BUILD SUCCESSFUL，全量测试（37 + 新增 12 = 49 左右）0 失败

- [ ] **Step 3: 提交**

```bash
git add README.md features.md
git commit -m "docs: document totem of holding feature"
```

- [ ] **Step 4: 桌面手动验证清单**（交给用户/桌面客户端）

1. 生存死亡（摔死/溺死）→ 死亡点出现悬浮旋转图腾，原物品不散落
2. 击打 3 次 → 物品按原槽位回背包（含盔甲槽），音效播放；背包满时多余物品原地掉落
3. PVP 击杀 → 无图腾，正常掉落
4. 未取回再死 → 旧图腾破裂散落 + 新图腾生成
5. 虚空死亡 → 图腾在 Y=0
6. 等待 5 分钟 → 图腾仍在（不消失）
7. 附魔/命名物品往返无损；消失诅咒物品死亡时销毁（不进图腾）
8. 服务器重启 → 图腾仍在（实体持久化）
