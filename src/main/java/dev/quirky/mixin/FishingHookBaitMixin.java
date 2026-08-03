package dev.quirky.mixin;

import dev.quirky.config.QuirkyConfigHolder;
import dev.quirky.fishbait.BaitZoneEntity;
import dev.quirky.fishbait.BaitZoneLogic;
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
 * HEAD 快照"本 tick 是否执行该递减分支"（nibble==0 && timeUntilHooked==0 && timeUntilLured>0），
 * RETURN 若快照为真且浮漂位于诱鱼区内则额外再减一次（clamp ≥0）。
 * 判定确定性：分支执行 ⇔ HEAD 条件成立（同 tick 内三个字段仅 catchingFish 修改）。
 * 二进制：多区域不叠加。
 */
@Mixin(FishingHook.class)
public abstract class FishingHookBaitMixin {
	@Shadow
	private int nibble;

	@Shadow
	private int timeUntilHooked;

	@Shadow
	private int timeUntilLured;

	@Unique
	private boolean quirky$decrementingThisTick;

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
			this.timeUntilLured = Math.max(0, this.timeUntilLured - 1);
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
