package dev.quirky.door;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.quirky.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DoubleDoorHandlerTest {
	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	@Test
	void syncsPartnerWhenHandInteractionSucceeds() {
		Level level = mock(Level.class);
		when(level.isClientSide()).thenReturn(false);
		when(level.getRandom()).thenReturn(RandomSource.create());
		BlockPos pos = new BlockPos(1, 64, 1);
		BlockPos partnerPos = pos.east();
		BlockState clicked = oakDoor(true, DoorHingeSide.LEFT);
		when(level.getBlockState(partnerPos)).thenReturn(oakDoor(false, DoorHingeSide.RIGHT));

		DoubleDoorHandler.sync(level, pos, clicked, mock(Player.class), InteractionResult.SUCCESS);

		verify(level).setBlock(eq(partnerPos), any(BlockState.class), anyInt());
	}

	@Test
	void doesNotSyncWhenInteractionDoesNotConsumeAction() {
		Level level = mock(Level.class);
		when(level.isClientSide()).thenReturn(false);
		BlockPos pos = new BlockPos(1, 64, 1);

		DoubleDoorHandler.sync(level, pos, oakDoor(true, DoorHingeSide.LEFT), mock(Player.class), InteractionResult.PASS);

		verify(level, never()).setBlock(any(), any(BlockState.class), anyInt());
	}

	@Test
	void doesNotSyncPartnerWithSameHinge() {
		Level level = mock(Level.class);
		when(level.isClientSide()).thenReturn(false);
		BlockPos pos = new BlockPos(1, 64, 1);
		BlockPos partnerPos = pos.east();
		when(level.getBlockState(partnerPos)).thenReturn(oakDoor(false, DoorHingeSide.LEFT));

		DoubleDoorHandler.sync(level, pos, oakDoor(true, DoorHingeSide.LEFT), mock(Player.class), InteractionResult.SUCCESS);

		verify(level, never()).setBlock(any(), any(BlockState.class), anyInt());
	}

	private static BlockState oakDoor(boolean open, DoorHingeSide hinge) {
		BlockState state = Blocks.OAK_DOOR.defaultBlockState();
		return state
			.setValue(DoorBlock.FACING, Direction.NORTH)
			.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
			.setValue(DoorBlock.HINGE, hinge)
			.setValue(DoorBlock.OPEN, open)
			.setValue(DoorBlock.POWERED, false);
	}
}
