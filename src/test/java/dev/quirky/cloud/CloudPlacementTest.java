package dev.quirky.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.quirky.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CloudPlacementTest {
	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	@Test
	void returnsNearestAirBlockAlongLookDirection() {
		Level level = mock(Level.class);
		when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.STONE.defaultBlockState());
		BlockPos near = new BlockPos(1, 64, 0);
		BlockPos far = new BlockPos(2, 64, 0);
		when(level.getBlockState(near)).thenReturn(Blocks.STONE.defaultBlockState());
		when(level.getBlockState(far)).thenReturn(Blocks.AIR.defaultBlockState());

		BlockPos found = CloudPlacement.findNearestAir(
			level,
			new Vec3(0.5, 64.5, 0.5),
			new Vec3(1.0, 0.0, 0.0),
			4.5
		);

		assertEquals(far, found);
	}

	@Test
	void returnsNullWhenNoAirBlockIsWithinReach() {
		Level level = mock(Level.class);
		when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.STONE.defaultBlockState());

		BlockPos found = CloudPlacement.findNearestAir(
			level,
			new Vec3(0.5, 64.5, 0.5),
			new Vec3(1.0, 0.0, 0.0),
			4.5
		);

		assertNull(found);
	}
}
