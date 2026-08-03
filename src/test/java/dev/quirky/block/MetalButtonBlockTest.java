package dev.quirky.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.quirky.ModBlocks;
import dev.quirky.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.flag.FeatureFlagSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.when;

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

	/** 正常按下：返回 SUCCESS 且方块进入 POWERED 状态。 */
	@Test
	void goldButtonPresses() {
		Level level = mock(Level.class);
		when(level.enabledFeatures()).thenReturn(FeatureFlagSet.of());
		BlockPos pos = new BlockPos(1, 64, 1);
		var state = ModBlocks.GOLD_BUTTON.defaultBlockState();

		InteractionResult result = ModBlocks.GOLD_BUTTON.useWithoutItem(
			state, level, pos, mock(Player.class), mock(BlockHitResult.class)
		);

		assertEquals(InteractionResult.SUCCESS, result);
		verify(level).setBlock(
			org.mockito.ArgumentMatchers.eq(pos),
			org.mockito.ArgumentMatchers.eq(state.setValue(BlockStateProperties.POWERED, true)),
			org.mockito.ArgumentMatchers.eq(3)
		);
	}
}
