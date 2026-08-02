package dev.quirky.totem;

import dev.quirky.ModEntities;
import dev.quirky.TestBootstrap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
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
		sword.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Excalibur")); // 26.2 无 setHoverName，改用 DataComponents.CUSTOM_NAME
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
