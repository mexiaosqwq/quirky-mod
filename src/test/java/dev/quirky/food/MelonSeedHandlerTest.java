package dev.quirky.food;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.quirky.TestBootstrap;
import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;

class MelonSeedHandlerTest {
	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

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
		ArgumentCaptor<Entity> captor = ArgumentCaptor.forClass(Entity.class);
		verify(level).addFreshEntity(captor.capture());
		ItemEntity item = assertInstanceOf(ItemEntity.class, captor.getValue());
		assertTrue(item.getItem().is(Items.MELON_SEEDS));
		assertTrue(item.hasPickUpDelay());
		assertEquals(new Vec3(0.3, 0.0, 0.0), item.getDeltaMovement());
		verify(player, never()).getInventory();
	}
}
