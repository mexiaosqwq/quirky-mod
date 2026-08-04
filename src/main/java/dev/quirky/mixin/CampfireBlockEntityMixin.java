package dev.quirky.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 给营火 BE 追加烟色（RGB，-1 = 无色）、夜光标记、及其【限时倒计时】。
 * - 染色烟约 5 分钟（6000 tick），萤石夜光约 2 分钟（2400 tick），到期自动恢复无色/无夜光；
 *   重染/重投萤石可续期。水浇熄灭一并清零。数值不进配置（§12.2 Agent 自主定）。
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

	/** 染色烟剩余 tick（>0 表示有色烟生效中）；0 时烟色视为无色。 */
	@Unique
	private int quirky$smokeColorTicks = 0;

	/** 夜光火星剩余 tick（>0 表示夜光生效中）；0 时无夜光。 */
	@Unique
	private int quirky$glowTicks = 0;

	@Inject(method = "loadAdditional(Lnet/minecraft/world/level/storage/ValueInput;)V", at = @At("TAIL"))
	private void quirky$loadSmokeState(ValueInput input, CallbackInfo ci) {
		this.quirky$smokeColor = input.getInt("quirky_smoke_color").orElse(-1);
		this.quirky$glow = input.getBooleanOr("quirky_glow", false);
		this.quirky$smokeColorTicks = input.getInt("quirky_smoke_color_ticks").orElse(0);
		this.quirky$glowTicks = input.getInt("quirky_glow_ticks").orElse(0);
		// 存档修复：若旧档存了永久色但无倒计时，按“已过期”处理（限时语义）
		if (this.quirky$smokeColorTicks <= 0) this.quirky$smokeColor = -1;
		if (this.quirky$glowTicks <= 0) this.quirky$glow = false;
	}

	@Inject(method = "saveAdditional(Lnet/minecraft/world/level/storage/ValueOutput;)V", at = @At("TAIL"))
	private void quirky$saveSmokeState(ValueOutput output, CallbackInfo ci) {
		output.putInt("quirky_smoke_color", this.quirky$smokeColor);
		output.putBoolean("quirky_glow", this.quirky$glow);
		output.putInt("quirky_smoke_color_ticks", this.quirky$smokeColorTicks);
		output.putInt("quirky_glow_ticks", this.quirky$glowTicks);
	}

	@Inject(method = "getUpdateTag(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
	private void quirky$updateTagSmokeState(HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> cir) {
		cir.getReturnValue().putInt("quirky_smoke_color", this.quirky$smokeColor);
		cir.getReturnValue().putBoolean("quirky_glow", this.quirky$glow);
		cir.getReturnValue().putInt("quirky_smoke_color_ticks", this.quirky$smokeColorTicks);
		cir.getReturnValue().putInt("quirky_glow_ticks", this.quirky$glowTicks);
	}

	/** 服务端 cooldownTick TAIL：递减倒计时，到期清状态并同步。 */
	@Inject(
		method = "cooldownTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/CampfireBlockEntity;)V",
		at = @At("TAIL")
	)
	private static void quirky$expireSmokeState(Level level, BlockPos pos, BlockState state, CampfireBlockEntity entity, CallbackInfo ci) {
		CampfireBlockEntityAccessor accessor = (CampfireBlockEntityAccessor) entity;
		boolean changed = false;
		int colorTicks = accessor.quirky$getSmokeColorTicks();
		if (colorTicks > 0) {
			if (colorTicks - 1 == 0) {
				accessor.quirky$setSmokeColor(-1); // 染色到期 → 无色
				accessor.quirky$setSmokeColorTicks(0);
				changed = true;
			} else {
				accessor.quirky$setSmokeColorTicks(colorTicks - 1);
			}
		}
		int glowTicks = accessor.quirky$getGlowTicks();
		if (glowTicks > 0) {
			if (glowTicks - 1 == 0) {
				accessor.quirky$setGlow(false); // 夜光到期 → 熄灭
				accessor.quirky$setGlowTicks(0);
				changed = true;
			} else {
				accessor.quirky$setGlowTicks(glowTicks - 1);
			}
		}
		if (changed) {
			entity.setChanged();
			level.sendBlockUpdated(pos, state, state, 3);
		}
	}
}
