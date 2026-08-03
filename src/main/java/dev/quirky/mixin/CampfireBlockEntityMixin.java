package dev.quirky.mixin;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 给营火 BE 追加烟色（RGB，-1 = 无色）与夜光标记字段。
 * - 存档：saveAdditional(ValueOutput)/loadAdditional(ValueInput)（26.2 Value 路径，CampfireBlockEntity.java:147/130）。
 * - 客户端同步：getUpdateTag 里写入（BE update packet 走 BlockEntity::getUpdateTag，客户端再经 loadAdditional 读回）。
 * 字段经 {@link CampfireBlockEntityAccessor} 暴露给 CampfireBlockMixin（同一 mixin 配置，本类必须排在其前）。
 */
@Mixin(CampfireBlockEntity.class)
public abstract class CampfireBlockEntityMixin {

	@Unique
	private int quirky$smokeColor = -1;

	@Unique
	private boolean quirky$glow = false;

	@Inject(method = "loadAdditional(Lnet/minecraft/world/level/storage/ValueInput;)V", at = @At("TAIL"))
	private void quirky$loadSmokeState(ValueInput input, CallbackInfo ci) {
		this.quirky$smokeColor = input.getInt("quirky_smoke_color").orElse(-1);
		this.quirky$glow = input.getBooleanOr("quirky_glow", false);
	}

	@Inject(method = "saveAdditional(Lnet/minecraft/world/level/storage/ValueOutput;)V", at = @At("TAIL"))
	private void quirky$saveSmokeState(ValueOutput output, CallbackInfo ci) {
		output.putInt("quirky_smoke_color", this.quirky$smokeColor);
		output.putBoolean("quirky_glow", this.quirky$glow);
	}

	@Inject(method = "getUpdateTag(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
	private void quirky$updateTagSmokeState(HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> cir) {
		cir.getReturnValue().putInt("quirky_smoke_color", this.quirky$smokeColor);
		cir.getReturnValue().putBoolean("quirky_glow", this.quirky$glow);
	}
}
