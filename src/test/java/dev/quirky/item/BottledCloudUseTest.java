package dev.quirky.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.quirky.ModBlocks;
import dev.quirky.ModItems;
import dev.quirky.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BottledCloudUseTest {
	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	@Test
	void survivalUseConsumesCloudAndConvertsToGlassBottle() {
		Player player = mock(Player.class);
		when(player.hasInfiniteMaterials()).thenReturn(false);
		when(player.getEyePosition()).thenReturn(new Vec3(0.5, 64.5, 0.5));
		when(player.getLookAngle()).thenReturn(new Vec3(1.0, 0.0, 0.0));
		when(player.blockInteractionRange()).thenReturn(4.5);
		Level level = mock(Level.class);
		when(level.isClientSide()).thenReturn(false);
		when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.defaultBlockState());

		ItemStack stack = new ItemStack(ModItems.BOTTLED_CLOUD);
		when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(stack);

		InteractionResult result = stack.use(level, player, InteractionHand.MAIN_HAND);

		assertInstanceOf(InteractionResult.Success.class, result);
		InteractionResult.Success success = (InteractionResult.Success) result;
		assertTrue(success.heldItemTransformedTo().is(Items.GLASS_BOTTLE));
		assertTrue(stack.isEmpty());
	}

	@Test
	void creativeUseDoesNotConsumeOrReturnGlassBottle() {
		Player player = mock(Player.class);
		when(player.hasInfiniteMaterials()).thenReturn(true);
		when(player.getEyePosition()).thenReturn(new Vec3(0.5, 64.5, 0.5));
		when(player.getLookAngle()).thenReturn(new Vec3(1.0, 0.0, 0.0));
		when(player.blockInteractionRange()).thenReturn(4.5);
		Level level = mock(Level.class);
		when(level.isClientSide()).thenReturn(false);
		when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.defaultBlockState());

		ItemStack stack = new ItemStack(ModItems.BOTTLED_CLOUD);
		when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(stack);

		InteractionResult result = stack.use(level, player, InteractionHand.MAIN_HAND);

		assertInstanceOf(InteractionResult.Success.class, result);
		InteractionResult.Success success = (InteractionResult.Success) result;
		assertSame(stack, success.heldItemTransformedTo());
		assertEquals(1, stack.getCount());
	}

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
}
