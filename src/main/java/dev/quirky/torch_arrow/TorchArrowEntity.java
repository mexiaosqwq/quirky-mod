package dev.quirky.torch_arrow;

import dev.quirky.ModEntities;
import dev.quirky.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

/**
 * A torch arrow: sets the hit target on fire and tries to place a torch where it hits a block.
 * Extends {@link AbstractArrow} directly (like the vanilla SpectralArrow) because the vanilla
 * {@link net.minecraft.world.entity.projectile.arrow.Arrow} constructors hardcode the vanilla
 * arrow entity type, which cannot carry a custom registered type.
 */
public class TorchArrowEntity extends AbstractArrow {
	private static final double BASE_DAMAGE = 1.0;
	private static final float IGNITE_SECONDS = 3.0F;

	public TorchArrowEntity(EntityType<? extends TorchArrowEntity> type, Level level) {
		super(type, level);
		this.setBaseDamage(BASE_DAMAGE);
	}

	public TorchArrowEntity(Level level, LivingEntity owner, ItemStack pickupItemStack, @Nullable ItemStack firedFromWeapon) {
		super(ModEntities.TORCH_ARROW, owner, level, pickupItemStack, firedFromWeapon);
		this.setBaseDamage(BASE_DAMAGE);
	}

	public TorchArrowEntity(Level level, double x, double y, double z, ItemStack pickupItemStack, @Nullable ItemStack firedFromWeapon) {
		super(ModEntities.TORCH_ARROW, x, y, z, level, pickupItemStack, firedFromWeapon);
		this.setBaseDamage(BASE_DAMAGE);
	}

	@Override
	protected ItemStack getDefaultPickupItem() {
		return new ItemStack(ModItems.TORCH_ARROW);
	}

	@Override
	public void setBaseDamageFromMob(float power) {
		// Vanilla bows call this with the draw-strength multiplier, which would raise our
		// damage to a normal arrow's level; torch arrows stay at the fixed low base damage.
	}

	@Override
	protected void onHitEntity(EntityHitResult hitResult) {
		super.onHitEntity(hitResult);
		Entity entity = hitResult.getEntity();
		if (entity.isAlive()) {
			entity.igniteForSeconds(IGNITE_SECONDS);
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult hitResult) {
		super.onHitBlock(hitResult);
		if (this.level() instanceof ServerLevel serverLevel) {
			BlockPos placePos = hitResult.getBlockPos().relative(hitResult.getDirection());
			BlockState torch = Blocks.TORCH.defaultBlockState();
			BlockState target = serverLevel.getBlockState(placePos);
			if (target.canBeReplaced() && torch.canSurvive(serverLevel, placePos)) {
				serverLevel.setBlockAndUpdate(placePos, torch);
				serverLevel.playSound(null, placePos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
			} else {
				// super.onHitBlock 已把箭卡入方块（卡住的箭可被拾取），先移除避免双回收
				this.discard();
				this.spawnAtLocation(serverLevel, this.getPickupItem(), 0.1F);
			}
		}
	}
}
