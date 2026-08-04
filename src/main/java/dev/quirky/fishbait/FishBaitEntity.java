package dev.quirky.fishbait;

import dev.quirky.ModEntities;
import dev.quirky.ModItems;
import dev.quirky.config.QuirkyConfigHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/**
 * 鱼饵球投掷物（同鸡蛋手感）：命中后服务端判落点方块是否为水——落水生成诱鱼区并
 * 溅起水花；落在陆地/方块则碎裂消失，不生成区域。
 */
public class FishBaitEntity extends ThrowableItemProjectile {
	public FishBaitEntity(EntityType<? extends FishBaitEntity> type, Level level) {
		super(type, level);
	}

	public FishBaitEntity(Level level, LivingEntity owner, ItemStack stack) {
		super(ModEntities.FISH_BAIT, owner, level, stack);
	}

	public FishBaitEntity(Level level, double x, double y, double z, ItemStack stack) {
		super(ModEntities.FISH_BAIT, x, y, z, level, stack);
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.FISH_BAIT;
	}

	@Override
	protected void onHit(HitResult hitResult) {
		super.onHit(hitResult);
		if (this.level().isClientSide()) {
			return;
		}
		ServerLevel serverLevel = (ServerLevel) this.level();
		BlockPos pos = this.blockPosition();
		// 水面判定 = 落点方块是水（或落点本身在水中）
		boolean landedInWater = this.isInWater() || serverLevel.getFluidState(pos).is(FluidTags.WATER);
		if (landedInWater) {
			// 从落点向上扫描到水面，把诱鱼区生成在水面高度（浮漂所在层，深水打窝也生效）
			int y = pos.getY();
			int maxScan = Math.min(serverLevel.getMaxY() - 1, y + 64);
			while (y < maxScan && serverLevel.getFluidState(new BlockPos(pos.getX(), y, pos.getZ())).is(FluidTags.WATER)) {
				y++;
			}
			BaitZoneEntity zone = ModEntities.BAIT_ZONE.create(serverLevel, EntitySpawnReason.TRIGGERED);
			if (zone != null) {
				// 生成时快照天气与配置：雨天 + 开启加成 → ×5/3 时长
				int ticks = BaitZoneLogic.durationTicks(
					QuirkyConfigHolder.get().fishBaitDurationSeconds,
					serverLevel.isRaining(),
					QuirkyConfigHolder.get().fishBaitRainBonus
				);
				zone.init(ticks);
				zone.setPos(this.getX(), y, this.getZ());
				serverLevel.addFreshEntity(zone);
			}
			// 小型水花 + 轻响（音量 0.4）
			serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.4F, 1.0F);
			serverLevel.sendParticles(ParticleTypes.SPLASH, this.getX(), y - 0.15, this.getZ(), 10, 0.6, 0.1, 0.6, 0.05);
		} else {
			// 陆地碎裂：碎屑粒子 + 轻响
			serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.5F, 1.0F);
			serverLevel.sendParticles(
				new ItemParticleOption(ParticleTypes.ITEM, ModItems.FISH_BAIT),
				this.getX(), this.getY(), this.getZ(), 6, 0.25, 0.25, 0.25, 0.1
			);
		}
		this.discard();
	}
}
