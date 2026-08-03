package dev.quirky.fishbait;

import dev.quirky.config.QuirkyConfigHolder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 诱鱼区实体：不可见、无重力、无碰撞、不渲染（客户端用 {@code NoopRenderer} 占位），
 * 服务端 tick 倒计时自毁，倒计时写入实体数据同步到客户端驱动气泡粒子密度。
 */
public class BaitZoneEntity extends Entity {
	private static final EntityDataAccessor<Integer> DATA_TICKS_LEFT = SynchedEntityData.defineId(
		BaitZoneEntity.class, EntityDataSerializers.INT
	);
	private static final String TAG_TICKS_LEFT = "TicksLeft";
	/** 气泡密度基准：剩余 90s（1800 tick）时每 tick 约 6% 概率；密度随剩余时间线性变稀，
	 *  雨天区域剩余时间更长 → 天然更密。 */
	private static final double BUBBLE_BASE_CHANCE = 0.06;
	private static final double BASE_TICKS = 90.0 * 20.0;

	private int ticksLeft;

	public BaitZoneEntity(EntityType<? extends BaitZoneEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true; // 无重力、无碰撞、不受推挤
	}

	/** 生成时由服务端初始化倒计时（生成时快照天气/配置，不追溯延长）。 */
	public void init(int ticks) {
		this.ticksLeft = ticks;
		this.entityData.set(DATA_TICKS_LEFT, ticks);
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide()) {
			this.ticksLeft--;
			if (this.ticksLeft <= 0) {
				this.discard();
				return;
			}
			this.entityData.set(DATA_TICKS_LEFT, this.ticksLeft);
			return;
		}
		// 客户端：水面偶发上浮气泡，密度随剩余时间线性变稀（不用 UI 即可看出窝快散了）
		int remaining = this.entityData.get(DATA_TICKS_LEFT);
		if (remaining <= 0) {
			return;
		}
		double chance = BUBBLE_BASE_CHANCE * remaining / BASE_TICKS;
		if (this.random.nextDouble() < chance) {
			double radius = QuirkyConfigHolder.get().fishBaitRadius;
			double x = this.getX() + (this.random.nextDouble() - 0.5) * 2.0 * radius;
			double z = this.getZ() + (this.random.nextDouble() - 0.5) * 2.0 * radius;
			this.level().addParticle(ParticleTypes.BUBBLE, x, this.getY(), z, 0.0, 0.02, 0.0);
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt(TAG_TICKS_LEFT, this.ticksLeft);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.ticksLeft = input.getIntOr(TAG_TICKS_LEFT, 1800);
		this.entityData.set(DATA_TICKS_LEFT, this.ticksLeft);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_TICKS_LEFT, 0);
	}
}
