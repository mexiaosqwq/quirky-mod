package dev.quirky.block;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.quirky.ModBlocks;
import dev.quirky.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CloudBlockTest {
	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	@Test
	void onPlaceSchedulesLifetimeTick() {
		ServerLevel level = mock(ServerLevel.class);
		BlockPos pos = new BlockPos(1, 64, 1);

		ModBlocks.CLOUD.onPlace(
			ModBlocks.CLOUD.defaultBlockState(),
			level,
			pos,
			Blocks.AIR.defaultBlockState(),
			false
		);

		verify(level).scheduleTick(pos, ModBlocks.CLOUD, 200);
	}

	@Test
	void tickRemovesCloudWhenExpired() {
		ServerLevel level = mock(ServerLevel.class);
		BlockPos pos = new BlockPos(1, 64, 1);
		BlockState state = ModBlocks.CLOUD.defaultBlockState();
		when(level.getBlockState(pos)).thenReturn(state);

		ModBlocks.CLOUD.tick(state, level, pos, RandomSource.create());

		verify(level).setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
	}

	@Test
	void entityInsideSlowsEntity() {
		Level level = mock(Level.class);
		BlockPos pos = new BlockPos(1, 64, 1);
		BlockState state = ModBlocks.CLOUD.defaultBlockState();
		Entity entity = mock(Entity.class);

		ModBlocks.CLOUD.entityInside(state, level, pos, entity, InsideBlockEffectApplier.NOOP, false);

		verify(entity).makeStuckInBlock(state, new Vec3(0.9, 0.25, 0.9));
	}

	@Test
	void cloudIsReplaceableByBlockPlacement() {
		assertTrue(ModBlocks.CLOUD.defaultBlockState().canBeReplaced(mock(BlockPlaceContext.class)));
	}
}
