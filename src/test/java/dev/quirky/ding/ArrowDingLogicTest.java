package dev.quirky.ding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import dev.quirky.TestBootstrap;
import dev.quirky.ding.ArrowDingLogic.Ding;
import dev.quirky.ding.ArrowDingLogic.TargetKind;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ArrowDingLogicTest {

	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	@Test
	void nonLivingAndShieldBlockedAreSilent() {
		assertTrue(ArrowDingLogic.resolve(TargetKind.NON_LIVING, false, false, 0.6F).isEmpty());
		assertTrue(ArrowDingLogic.resolve(TargetKind.SHIELD_BLOCKED, true, false, 0.6F).isEmpty());
	}

	@Test
	void unarmoredHitUsesBellAtConfigVolume() {
		Optional<Ding> ding = ArrowDingLogic.resolve(TargetKind.LIVING_UNARMORED, false, false, 0.6F);
		assertTrue(ding.isPresent());
		assertEquals(SoundEvents.NOTE_BLOCK_BELL, ding.get().sound());
		assertEquals(1.5F, ding.get().pitch());
		assertEquals(0.6F, ding.get().volume(), 1.0E-6F);
	}

	@Test
	void critRaisesPitch() {
		Ding ding = ArrowDingLogic.resolve(TargetKind.LIVING_UNARMORED, true, false, 0.6F).orElseThrow();
		assertEquals(1.8F, ding.pitch());
	}

	@Test
	void metalArmorTargetUsesIronClank() {
		Ding ding = ArrowDingLogic.resolve(TargetKind.LIVING_METAL_ARMOR, false, false, 0.6F).orElseThrow();
		assertEquals(SoundEvents.ARMOR_EQUIP_IRON, ding.sound());
		assertEquals(1.2F, ding.pitch());
	}

	@Test
	void killBoostsVolume() {
		Ding ding = ArrowDingLogic.resolve(TargetKind.LIVING_UNARMORED, false, true, 0.6F).orElseThrow();
		assertEquals(0.72F, ding.volume(), 1.0E-6F);
	}

	@Test
	void volumeClampedThenKillBoostApplied() {
		Ding ding = ArrowDingLogic.resolve(TargetKind.LIVING_UNARMORED, false, true, 1.5F).orElseThrow();
		assertEquals(1.2F, ding.volume(), 1.0E-6F);
	}

	@Test
	void zeroVolumeStaysSilent() {
		Ding ding = ArrowDingLogic.resolve(TargetKind.LIVING_UNARMORED, false, false, 0.0F).orElseThrow();
		assertEquals(0.0F, ding.volume(), 1.0E-6F);
	}

	@Test
	void ironArmorCountsAsMetal() {
		ItemStack chestplate = mock(ItemStack.class);
		when(chestplate.is(Items.IRON_CHESTPLATE)).thenReturn(true);
		assertTrue(ArrowDingLogic.hasMetalArmor(List.of(mock(ItemStack.class), chestplate)));
	}

	@Test
	void leatherAndEmptyAreNotMetal() {
		assertFalse(ArrowDingLogic.hasMetalArmor(List.of(mock(ItemStack.class))));
		assertFalse(ArrowDingLogic.hasMetalArmor(List.of()));
	}

	@Test
	void goldenAndNetheriteCountAsMetal() {
		ItemStack boots = mock(ItemStack.class);
		when(boots.is(Items.GOLDEN_BOOTS)).thenReturn(true);
		ItemStack helmet = mock(ItemStack.class);
		when(helmet.is(Items.NETHERITE_HELMET)).thenReturn(true);
		assertTrue(ArrowDingLogic.hasMetalArmor(List.of(boots)));
		assertTrue(ArrowDingLogic.hasMetalArmor(List.of(helmet)));
	}
}
