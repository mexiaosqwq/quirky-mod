package dev.quirky.equip_swap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.quirky.TestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OffhandSwapItemsTest {

	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
		TestBootstrap.bindItem(Items.STONE);
		TestBootstrap.bindItem(Items.TORCH);
		TestBootstrap.bindMinimalComponents(Items.SHIELD);
		TestBootstrap.bindItem(Items.WIND_CHARGE);
		TestBootstrap.bindItem(Items.FIREWORK_ROCKET);
	}

	@Test
	void acceptsAllDedicatedOffhandItems() {
		assertTrue(OffhandSwapItems.isOffhandSwapItem(new ItemStack(Items.SHIELD)));
		assertTrue(OffhandSwapItems.isOffhandSwapItem(new ItemStack(Items.TORCH)));
		assertTrue(OffhandSwapItems.isOffhandSwapItem(new ItemStack(Items.WIND_CHARGE)));
		assertTrue(OffhandSwapItems.isOffhandSwapItem(new ItemStack(Items.FIREWORK_ROCKET)));
	}

	@Test
	void rejectsUnrelatedItems() {
		assertFalse(OffhandSwapItems.isOffhandSwapItem(new ItemStack(Items.STONE)));
	}
}
