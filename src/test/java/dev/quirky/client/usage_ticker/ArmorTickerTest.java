package dev.quirky.client.usage_ticker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.quirky.TestBootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArmorTickerTest {

	private static final int FEET_SLOT = 36;

	private Inventory inventory;

	@BeforeAll
	static void boot() {
		TestBootstrap.boot();
		TestBootstrap.bindItem(Items.DIAMOND_HELMET);
		TestBootstrap.bindItem(Items.DIAMOND_CHESTPLATE);
	}

	@BeforeEach
	void setUp() {
		inventory = mock(Inventory.class);
		when(inventory.getContainerSize()).thenReturn(43);
		for (int slot = 0; slot < 43; slot++) {
			when(inventory.getItem(slot)).thenReturn(ItemStack.EMPTY);
		}
		ArmorTicker.reset();
	}

	private Player playerWithArmor(ItemStack... armor) {
		for (int i = 0; i < 4; i++) {
			when(inventory.getItem(FEET_SLOT + i)).thenReturn(ItemStack.EMPTY);
		}
		for (int i = 0; i < armor.length; i++) {
			when(inventory.getItem(FEET_SLOT + i)).thenReturn(armor[i]);
		}
		Player player = mock(Player.class);
		when(player.getInventory()).thenReturn(inventory);
		return player;
	}

	private ItemStack helmet(int damage) {
		ItemStack stack = new ItemStack(Items.DIAMOND_HELMET);
		stack.setDamageValue(damage);
		return stack;
	}

	@Test
	void firstTick_afterReset_establishesBaseline() {
		Player player = playerWithArmor(helmet(3));

		assertFalse(ArmorTicker.tick(player));
	}

	@Test
	void durabilityDecrease_triggers() {
		Player player = playerWithArmor(helmet(3));
		ArmorTicker.tick(player); // 基线

		playerWithArmor(helmet(8));

		assertTrue(ArmorTicker.tick(player));
	}

	@Test
	void durabilityIncrease_repair_triggers() {
		Player player = playerWithArmor(helmet(8));
		ArmorTicker.tick(player); // 基线

		playerWithArmor(helmet(3)); // 铁砧修复/经验修补

		assertTrue(ArmorTicker.tick(player));
	}

	@Test
	void noChange_doesNotTrigger() {
		Player player = playerWithArmor(helmet(5));
		ArmorTicker.tick(player); // 基线

		assertFalse(ArmorTicker.tick(player));
	}

	@Test
	void equippingArmor_triggers() {
		Player player = playerWithArmor(); // 未穿戴
		ArmorTicker.tick(player); // 基线

		playerWithArmor(helmet(0));

		assertTrue(ArmorTicker.tick(player));
	}

	@Test
	void removingArmor_triggers() {
		Player player = playerWithArmor(helmet(5));
		ArmorTicker.tick(player); // 基线

		playerWithArmor(); // 脱掉

		assertTrue(ArmorTicker.tick(player));
	}

	@Test
	void switchingToDifferentArmorPiece_triggers() {
		Player player = playerWithArmor(helmet(5));
		ArmorTicker.tick(player); // 基线

		ItemStack chestplate = new ItemStack(Items.DIAMOND_CHESTPLATE);
		chestplate.setDamageValue(5);
		playerWithArmor(chestplate); // 换同耐久不同装备

		assertTrue(ArmorTicker.tick(player));
	}
}
