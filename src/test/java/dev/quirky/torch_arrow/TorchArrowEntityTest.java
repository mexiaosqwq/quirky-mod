package dev.quirky.torch_arrow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.quirky.ModEntities;
import dev.quirky.ModItems;
import dev.quirky.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TorchArrowEntityTest {
	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	@Test
	void hitReplaceableBlockPlacesTorch() {
		ServerLevel level = mock(ServerLevel.class);
		when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.STONE.defaultBlockState());
		BlockPos hitPos = new BlockPos(1, 64, 0);
		BlockPos placePos = hitPos.relative(Direction.UP);
		when(level.getBlockState(placePos)).thenReturn(Blocks.AIR.defaultBlockState());

		TorchArrowEntity arrow = spy(new TorchArrowEntity(ModEntities.TORCH_ARROW, level));
		arrow.onHitBlock(new BlockHitResult(new Vec3(1.5, 64.0, 0.5), Direction.UP, hitPos, false));

		verify(level).setBlockAndUpdate(eq(placePos), eq(Blocks.TORCH.defaultBlockState()));
	}

	@Test
	void hitNonReplaceableBlockDropsPickupItem() {
		ServerLevel level = mock(ServerLevel.class);
		when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.STONE.defaultBlockState());
		BlockPos hitPos = new BlockPos(1, 64, 0);

		TorchArrowEntity arrow = spy(new TorchArrowEntity(ModEntities.TORCH_ARROW, level));
		arrow.onHitBlock(new BlockHitResult(new Vec3(1.5, 64.0, 0.5), Direction.UP, hitPos, false));

		verify(arrow).spawnAtLocation(eq(level), any(ItemStack.class), anyFloat());
	}

	@Test
	void hitEntityIgnitesItForThreeSeconds() {
		Entity target = mock(Entity.class);
		when(target.getRemainingFireTicks()).thenReturn(0);
		when(target.hurtOrSimulate(any(DamageSource.class), anyInt())).thenReturn(false);
		when(target.isAlive()).thenReturn(true);
		DamageSources damageSources = mock(DamageSources.class);
		when(damageSources.arrow(any(), any())).thenReturn(mock(DamageSource.class));
		Level level = mock(Level.class);
		when(level.isClientSide()).thenReturn(false);
		when(level.damageSources()).thenReturn(damageSources);

		TorchArrowEntity arrow = new TorchArrowEntity(ModEntities.TORCH_ARROW, level);
		arrow.onHitEntity(new EntityHitResult(target));

		verify(target).igniteForSeconds(3.0F);
	}

	@Test
	void createArrowReturnsTorchArrowEntity() {
		Level level = mock(Level.class);
		Player player = mock(Player.class);

		AbstractArrow arrow = ((ArrowItem) ModItems.TORCH_ARROW).createArrow(level, new ItemStack(ModItems.TORCH_ARROW), player, null);

		assertInstanceOf(TorchArrowEntity.class, arrow);
		assertEquals(ModItems.TORCH_ARROW, ((TorchArrowEntity) arrow).getDefaultPickupItem().getItem());
		assertEquals(ModEntities.TORCH_ARROW, arrow.getType());
	}

	@Test
	void bowPowerDoesNotRaiseBaseDamage() throws Exception {
		TorchArrowEntity arrow = new TorchArrowEntity(ModEntities.TORCH_ARROW, mock(Level.class));

		arrow.setBaseDamageFromMob(3.0F); // vanilla bows would raise a normal arrow to ~2+ damage

		java.lang.reflect.Field field = net.minecraft.world.entity.projectile.arrow.AbstractArrow.class.getDeclaredField("baseDamage");
		field.setAccessible(true);
		assertEquals(1.0, (double) field.get(arrow), 0.001);
	}
}
