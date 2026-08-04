package dev.quirky.mixin;

import dev.quirky.config.QuirkyConfigHolder;
import dev.quirky.fishbait.BaitZoneEntity;
import dev.quirky.fishbait.BaitZoneLogic;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 诱鱼区加速咬钩：FishingHook.tick() 内 catchingFish 的分支链为
 * nibble → timeUntilHooked → timeUntilLured（递减语句 timeUntilLured -= fishingSpeed）。
 * HEAD 快照“本 tick 是否执行该递减分支”（nibble==0 && timeUntilHooked==0 && timeUntilLured>0），
 * RETURN 若快照为真且浮漂位于诱鱼区内则额外再减 2（clamp ≥0）→ 诱鱼阶段约 3× 速（26.2
 * 默认 fishingSpeed=1）。实测 2×（-1）感知太弱，强化为 -2。
 * 区内浮漂每 tick 25% 概率冒泡（服务端 sendParticles），作为“生效中”的可见反馈。
 * 判定确定性：分支执行 ⇔ HEAD 条件成立（同 tick 内三个字段仅 catchingFish 修改）。
 * 二进制：多区域不叠加。
 */
@Mixin(FishingHook.class)
public abstract class FishingHookBaitMixin {
	// TODO 临时诊断日志（鱼饵“无加速”问题定位后删除）
	@Unique
	private static final org.slf4j.Logger BAIT_DEBUG = org.slf4j.LoggerFactory.getLogger("quirky-bait-debug");

	@Shadow
	private int nibble;

	@Shadow
	private int timeUntilHooked;

	@Shadow
	private int timeUntilLured;

	@Unique
	private boolean quirky$decrementingThisTick;

	@Unique
	private boolean quirky$loggedZoneHit;

	@Unique
	private boolean quirky$loggedNoZone;

	@Inject(method = "tick", at = @At("HEAD"))
	private void quirky$snapshotDecrementState(CallbackInfo ci) {
		this.quirky$decrementingThisTick = this.nibble == 0 && this.timeUntilHooked == 0 && this.timeUntilLured > 0;
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void quirky$extraLureDecrement(CallbackInfo ci) {
		if (!this.quirky$decrementingThisTick) {
			return;
		}
		// level()/isRemoved() 声明在 Entity（目标类超类），@Shadow 不安全，用本类映射转换访问
		Entity self = (Entity) (Object) this;
		if (self.level().isClientSide() || self.isRemoved()) {
			return;
		}
		if (!QuirkyConfigHolder.get().fishBaitEnabled) {
			return;
		}
		if (this.quirky$inBaitZone()) {
			if (!this.quirky$loggedZoneHit) {
				this.quirky$loggedZoneHit = true;
				BAIT_DEBUG.info("[bait-debug] hook ENTERED bait zone at {}, extra decrement active (radius={})",
					self.blockPosition(), QuirkyConfigHolder.get().fishBaitRadius);
			}
			this.timeUntilLured = Math.max(0, this.timeUntilLured - 2);
			this.quirky$bobberFeedback();
		} else if (!this.quirky$loggedNoZone) {
			this.quirky$loggedNoZone = true;
			BAIT_DEBUG.info("[bait-debug] hook lure-tick at {} but NO bait zone found (radius={})",
				self.blockPosition(), QuirkyConfigHolder.get().fishBaitRadius);
		}
	}

	/** 区内浮漂冒泡反馈（服务端 sendParticles）：25%/tick，让玩家明确看到诱鱼区在起作用。 */
	@Unique
	private void quirky$bobberFeedback() {
		Entity self = (Entity) (Object) this;
		if (self.getRandom().nextFloat() >= 0.25F) {
			return;
		}
		if (self.level() instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(
				ParticleTypes.BUBBLE,
				self.getX(), self.getY() + 0.1, self.getZ(),
				2, 0.15, 0.05, 0.15, 0.01
			);
		}
	}

	@Unique
	private boolean quirky$inBaitZone() {
		Entity self = (Entity) (Object) this;
		double radius = QuirkyConfigHolder.get().fishBaitRadius;
		AABB box = self.getBoundingBox().inflate(radius);
		for (BaitZoneEntity zone : self.level().getEntities(EntityTypeTest.forClass(BaitZoneEntity.class), box, e -> true)) {
			if (BaitZoneLogic.isInside(self.position(), zone.position(), radius)) {
				return true;
			}
		}
		return false;
	}
}
