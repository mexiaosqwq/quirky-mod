package dev.quirky.seedpouch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.quirky.TestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SeedFilterTest {
	@BeforeAll
	static void boot() {
		TestBootstrap.boot();
		TestBootstrap.bindMinimalComponents(Items.WHEAT_SEEDS);
		TestBootstrap.bindMinimalComponents(Items.NETHER_WART);
		TestBootstrap.bindMinimalComponents(Items.SUGAR_CANE);
		TestBootstrap.bindMinimalComponents(Items.LANTERN);
		TestBootstrap.bindMinimalComponents(Items.STICK);
	}

	@Test
	void wheatSeedsAccepted() {
		assertTrue(SeedFilter.isSeed(new ItemStack(Items.WHEAT_SEEDS)));
	}

	@Test
	void netherWartAccepted() {
		assertTrue(SeedFilter.isSeed(new ItemStack(Items.NETHER_WART)));
	}

	@Test
	void sugarCaneAccepted() {
		assertTrue(SeedFilter.isSeed(new ItemStack(Items.SUGAR_CANE)));
	}

	@Test
	void lanternRejected() {
		// 灯笼是 BlockItem 但非作物方块 → 必须拒绝（v1 灯笼 bug 根因）
		assertFalse(SeedFilter.isSeed(new ItemStack(Items.LANTERN)));
	}

	@Test
	void nonBlockItemRejected() {
		assertFalse(SeedFilter.isSeed(new ItemStack(Items.STICK)));
	}
}
