package dev.quirky.totem;

import dev.quirky.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Unit;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
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
		// 26.2 中附魔是 datapack registry，单测环境无注册表；用带消失诅咒效果的 direct holder 模拟。
		Enchantment curse = new Enchantment(
			Component.literal("vanishing_curse"),
			Enchantment.definition(HolderSet.empty(), 1, 1, Enchantment.constantCost(1), Enchantment.constantCost(1), 1),
			HolderSet.empty(),
			DataComponentMap.builder().set(EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP, Unit.INSTANCE).build()
		);
		cursed.enchant(Holder.direct(curse), 1);
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

	// ---- findSpawnPosition 自适应高度 ----

	@Test
	void findSpawnPosition_usesLowestFreeSpotAboveDeath() {
		Level level = mock(Level.class);
		when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());

		BlockPos result = TotemOfHoldingLogic.findSpawnPosition(level, new BlockPos(1, 64, 1));

		assertEquals(66, result.getY()); // 头顶上方（死亡点 +2）
	}

	@Test
	void findSpawnPosition_risesPastObstacles() {
		Level level = mock(Level.class);
		// y=66 有方块（低顶），y=67 起为空
		when(level.getBlockState(new BlockPos(1, 66, 1))).thenReturn(Blocks.STONE.defaultBlockState());
		when(level.getBlockState(new BlockPos(1, 67, 1))).thenReturn(Blocks.AIR.defaultBlockState());
		when(level.getBlockState(new BlockPos(1, 68, 1))).thenReturn(Blocks.AIR.defaultBlockState());

		BlockPos result = TotemOfHoldingLogic.findSpawnPosition(level, new BlockPos(1, 64, 1));

		assertEquals(67, result.getY()); // 跳过被挡的 y=66，落在 y=67
	}

	@Test
	void findSpawnPosition_fallsBackWhenFullyBlocked() {
		Level level = mock(Level.class);
		when(level.getBlockState(any())).thenReturn(Blocks.STONE.defaultBlockState());

		BlockPos result = TotemOfHoldingLogic.findSpawnPosition(level, new BlockPos(1, 64, 1));

		assertEquals(66, result.getY()); // 全堵时兜底头顶上方
	}
}
