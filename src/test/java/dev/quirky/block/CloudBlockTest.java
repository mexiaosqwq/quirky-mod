package dev.quirky.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.quirky.ModBlocks;
import dev.quirky.ModItems;
import dev.quirky.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CloudBlockTest {
	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
		TestBootstrap.bindItem(Items.STONE);
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

		verify(entity).makeStuckInBlock(state, new Vec3(0.95, 0.6, 0.95));
	}

	@Test
	void entityInsideExtinguishesFireAndRemovesCloud() {
		Level level = mock(Level.class);
		BlockPos pos = new BlockPos(1, 64, 1);
		BlockState state = ModBlocks.CLOUD.defaultBlockState();
		Entity entity = mock(Entity.class);
		when(entity.isOnFire()).thenReturn(true);

		ModBlocks.CLOUD.entityInside(state, level, pos, entity, InsideBlockEffectApplier.NOOP, false);

		verify(entity).clearFire();
		verify(level).setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
	}

	@Test
	void entityInsideWithoutFireKeepsCloud() {
		Level level = mock(Level.class);
		BlockPos pos = new BlockPos(1, 64, 1);
		BlockState state = ModBlocks.CLOUD.defaultBlockState();
		Entity entity = mock(Entity.class);
		when(entity.isOnFire()).thenReturn(false);

		ModBlocks.CLOUD.entityInside(state, level, pos, entity, InsideBlockEffectApplier.NOOP, false);

		verify(entity, never()).clearFire();
		verify(level, never()).setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
	}

	@Test
	void cloudIsReplaceableByBlockPlacement() {
		assertTrue(ModBlocks.CLOUD.defaultBlockState().canBeReplaced(mock(BlockPlaceContext.class)));
	}

	@Test
	void useItemOnWithGlassBottleCollectsCloudIntoBottleInPlace() {
		Level level = mock(Level.class);
		BlockPos pos = new BlockPos(1, 64, 1);
		BlockState state = ModBlocks.CLOUD.defaultBlockState();
		Player player = mock(Player.class);
		when(player.getInventory()).thenReturn(mock(Inventory.class));
		when(player.hasInfiniteMaterials()).thenReturn(false);
		ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);

		InteractionResult result = ModBlocks.CLOUD.useItemOn(
			bottle, state, level, pos, player, InteractionHand.MAIN_HAND, mock(BlockHitResult.class)
		);

		assertTrue(result.consumesAction());
		verify(level).setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		assertTrue(bottle.isEmpty());
		ArgumentCaptor<ItemStack> captor = ArgumentCaptor.forClass(ItemStack.class);
		verify(player).setItemInHand(eq(InteractionHand.MAIN_HAND), captor.capture());
		assertTrue(captor.getValue().is(ModItems.BOTTLED_CLOUD));
		assertEquals(1, captor.getValue().getCount());
	}

	@Test
	void useItemOnWithStackedBottlesPutsCloudInInventory() {
		Level level = mock(Level.class);
		BlockPos pos = new BlockPos(1, 64, 1);
		BlockState state = ModBlocks.CLOUD.defaultBlockState();
		Player player = mock(Player.class);
		Inventory inventory = mock(Inventory.class);
		when(player.getInventory()).thenReturn(inventory);
		when(player.hasInfiniteMaterials()).thenReturn(false);
		ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE, 2);

		InteractionResult result = ModBlocks.CLOUD.useItemOn(
			bottle, state, level, pos, player, InteractionHand.MAIN_HAND, mock(BlockHitResult.class)
		);

		assertTrue(result.consumesAction());
		assertEquals(1, bottle.getCount());
		ArgumentCaptor<ItemStack> captor = ArgumentCaptor.forClass(ItemStack.class);
		verify(inventory).placeItemBackInInventory(captor.capture());
		assertTrue(captor.getValue().is(ModItems.BOTTLED_CLOUD));
		verify(player, never()).setItemInHand(any(), any());
	}

	@Test
	void useItemOnInCreativeKeepsBottleAndGivesCloud() {
		Level level = mock(Level.class);
		BlockPos pos = new BlockPos(1, 64, 1);
		BlockState state = ModBlocks.CLOUD.defaultBlockState();
		Player player = mock(Player.class);
		Inventory inventory = mock(Inventory.class);
		when(player.getInventory()).thenReturn(inventory);
		when(player.hasInfiniteMaterials()).thenReturn(true);
		ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);

		InteractionResult result = ModBlocks.CLOUD.useItemOn(
			bottle, state, level, pos, player, InteractionHand.MAIN_HAND, mock(BlockHitResult.class)
		);

		assertTrue(result.consumesAction());
		assertEquals(1, bottle.getCount());
		ArgumentCaptor<ItemStack> captor = ArgumentCaptor.forClass(ItemStack.class);
		verify(inventory).placeItemBackInInventory(captor.capture());
		assertTrue(captor.getValue().is(ModItems.BOTTLED_CLOUD));
	}

	@Test
	void useItemOnWithoutGlassBottlePasses() {
		Level level = mock(Level.class);
		BlockPos pos = new BlockPos(1, 64, 1);
		BlockState state = ModBlocks.CLOUD.defaultBlockState();
		Player player = mock(Player.class);

		InteractionResult result = ModBlocks.CLOUD.useItemOn(
			new ItemStack(Items.STONE), state, level, pos, player, InteractionHand.MAIN_HAND, mock(BlockHitResult.class)
		);

		assertEquals(InteractionResult.PASS, result);
		verify(level, never()).setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
	}
}
