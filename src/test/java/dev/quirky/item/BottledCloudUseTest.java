package dev.quirky.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.quirky.ModItems;
import dev.quirky.TestBootstrap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
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
		Level level = mock(Level.class);
		when(level.isClientSide()).thenReturn(false);

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
		Level level = mock(Level.class);
		when(level.isClientSide()).thenReturn(false);

		ItemStack stack = new ItemStack(ModItems.BOTTLED_CLOUD);
		when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(stack);

		InteractionResult result = stack.use(level, player, InteractionHand.MAIN_HAND);

		assertInstanceOf(InteractionResult.Success.class, result);
		InteractionResult.Success success = (InteractionResult.Success) result;
		assertSame(stack, success.heldItemTransformedTo());
		assertEquals(1, stack.getCount());
	}
}
