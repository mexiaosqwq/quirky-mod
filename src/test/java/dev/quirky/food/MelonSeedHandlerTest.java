package dev.quirky.food;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.quirky.TestBootstrap;
import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;

class MelonSeedHandlerTest {
	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	@Test
	@SuppressWarnings("unchecked")
	void grantsSeedWhenFinishingLastMelonSlice() {
		ServerPlayer player = mock(ServerPlayer.class);
		when(player.hasInfiniteMaterials()).thenReturn(false);
		when(player.getRandom()).thenReturn(RandomSource.create());
		Inventory inventory = mock(Inventory.class);
		when(inventory.add(any(ItemStack.class))).thenReturn(true);
		when(player.getInventory()).thenReturn(inventory);
		when(player.getFoodData()).thenReturn(mock(FoodData.class));
		PlayerAdvancements advancements = mock(PlayerAdvancements.class);
		when(advancements.getTriggerMapForType(any(CriterionTrigger.class))).thenReturn(Collections.emptyMap());
		when(player.getAdvancements()).thenReturn(advancements);
		Level level = mock(Level.class);

		ItemStack slice = new ItemStack(Items.MELON_SLICE);
		ItemStack result = MelonSeedHandler.finishUsing(slice, level, player);

		assertSame(slice, result);
		assertTrue(slice.isEmpty());
		verify(inventory).add(argThat(seed -> seed.is(Items.MELON_SEEDS)));
	}
}
