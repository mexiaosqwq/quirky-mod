package dev.quirky.parrotegg;

import dev.quirky.ModEntities;
import dev.quirky.ModItems;
import dev.quirky.config.QuirkyConfigHolder;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 鹦鹉蛋弹射物：手感与鸡蛋一致（抛出弧线、命中生物 0 伤害轻微击退）。
 * 服务端落地/命中时按概率孵化 0/1/2 只鹦鹉（EntitySpawnReason.TRIGGERED + snapTo，鹦鹉无幼年形态
 * ——Parrot.canBeABaby() = false，孵出即成体且构造器自动随机选色）；碎壳粒子颜色跟随鹦鹉羽色。
 */
public class ParrotEggEntity extends ThrowableItemProjectile {

	public ParrotEggEntity(EntityType<? extends ParrotEggEntity> type, Level level) {
		super(type, level);
	}

	public ParrotEggEntity(Level level, LivingEntity owner, ItemStack itemStack) {
		super(ModEntities.PARROT_EGG, owner, level, itemStack);
	}

	public ParrotEggEntity(Level level, double x, double y, double z, ItemStack itemStack) {
		super(ModEntities.PARROT_EGG, x, y, z, level, itemStack);
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == 3) {
			ItemStack item = this.getItem();
			if (!item.isEmpty()) {
				ItemParticleOption breakParticle = new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(item));

				for (int i = 0; i < 8; i++) {
					this.level()
						.addParticle(
							breakParticle,
							this.getX(),
							this.getY(),
							this.getZ(),
							(this.random.nextFloat() - 0.5F) * 0.08,
							(this.random.nextFloat() - 0.5F) * 0.08,
							(this.random.nextFloat() - 0.5F) * 0.08
						);
				}
			}
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult hitResult) {
		super.onHitEntity(hitResult);
		hitResult.getEntity().hurt(this.damageSources().thrown(this, this.getOwner()), 0.0F);
	}

	@Override
	protected void onHit(HitResult hitResult) {
		super.onHit(hitResult);
		if (this.level().isClientSide()) {
			return; // 孵化与粒子由服务端权威执行
		}
		ServerLevel serverLevel = (ServerLevel) this.level();
		float hatchChance;
		if (!QuirkyConfigHolder.get().parrotEggEnabled) {
			hatchChance = 0.0F;
		} else if (isOnJungle(serverLevel, hitResult)) {
			hatchChance = ParrotEggHatchLogic.jungleBoost(QuirkyConfigHolder.get().parrotEggHatchChance);
		} else {
			hatchChance = QuirkyConfigHolder.get().parrotEggHatchChance;
		}
		int count = ParrotEggHatchLogic.hatchCount(this.random, hatchChance, QuirkyConfigHolder.get().parrotEggTwinChance);
		int shellColor;
		if (count > 0) {
			shellColor = 0;
			for (int i = 0; i < count; i++) {
				Parrot parrot = EntityTypes.PARROT.create(serverLevel, EntitySpawnReason.TRIGGERED);
				if (parrot != null) {
					parrot.snapTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
					serverLevel.addFreshEntity(parrot);
					shellColor = ParrotEggHatchLogic.shellColor(parrot.getVariant());
				}
			}
			serverLevel.playSound(
				null, this.getX(), this.getY(), this.getZ(), SoundEvents.PARROT_AMBIENT, SoundSource.NEUTRAL, 1.0F, 1.0F
			);
			serverLevel.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 0.5, this.getZ(), 5, 0.3, 0.3, 0.3, 0.0);
		} else {
			shellColor = ParrotEggHatchLogic.randomShellColor(this.random);
		}
		// 碎壳色粒子（先看到壳色，再看到鹦鹉）
		serverLevel.sendParticles(
			ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, shellColor),
			this.getX(), this.getY(), this.getZ(), 8, 0.2, 0.2, 0.2, 0.0
		);
		this.level().broadcastEntityEvent(this, (byte) 3);
		this.discard();
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.PARROT_EGG;
	}

	/** 落在丛林树叶/丛林原木上 → 孵化率提升。 */
	private static boolean isOnJungle(Level level, HitResult hitResult) {
		if (hitResult instanceof BlockHitResult blockHit) {
			BlockState state = level.getBlockState(blockHit.getBlockPos());
			return state.is(Blocks.JUNGLE_LEAVES) || state.is(Blocks.JUNGLE_LOG);
		}
		return false;
	}
}
