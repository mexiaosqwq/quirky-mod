package dev.quirky.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.quirky.ModBlocks;
import dev.quirky.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MetalButtonBlockTest {
	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	@Test
	void goldButtonPulseIsTwoTicks() {
		assertEquals(2, MetalButtonBlock.holdTicksOf(ModBlocks.GOLD_BUTTON));
	}

	@Test
	void ironButtonPulseIsOneHundredTicks() {
		assertEquals(100, MetalButtonBlock.holdTicksOf(ModBlocks.IRON_BUTTON));
	}
}
