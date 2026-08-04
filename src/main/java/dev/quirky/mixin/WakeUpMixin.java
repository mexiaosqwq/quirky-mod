package dev.quirky.mixin;

import dev.quirky.config.QuirkyConfigHolder;
import dev.quirky.wakeup.WakeUpLogic;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 起床保护：玩家从床上醒来（自然天亮醒/手动起床/被怪物惊醒）后获得短暂缓降（Slow Falling）。
 * - 双注入：HEAD 快照 isSleepingLongEnough() 到 @Unique 字段（TAIL 时玩家已醒、快照不可再读），TAIL 消费。
 * - 深睡（!forcefulWakeUp 且睡满整晚）= 配置秒数；被惊醒/中途起床 = 1/3。
 * - 仅服务端加效果（客户端/服务端都会调用 stopSleepInBed）。
 * 注入点：Player.stopSleepInBed(boolean, boolean) 与 Player.isSleepingLongEnough() 均声明在 Player 本类
 * （mcsrc Player.java:1321 / :1335），描述符 (ZZ)V。
 */
@Mixin(Player.class)
public abstract class WakeUpMixin {

	@Unique
	private boolean quirky$sleptLongEnough;

	@Inject(method = "stopSleepInBed(ZZ)V", at = @At("HEAD"))
	private void quirky$snapshotSleepState(boolean forcefulWakeUp, boolean updateLevelList, CallbackInfo ci) {
		this.quirky$sleptLongEnough = ((Player) (Object) this).isSleepingLongEnough();
	}

	@Inject(method = "stopSleepInBed(ZZ)V", at = @At("TAIL"))
	private void quirky$applyWakeUpProtection(boolean forcefulWakeUp, boolean updateLevelList, CallbackInfo ci) {
		Player player = (Player) (Object) this;
		if (player.level().isClientSide()) {
			return; // 仅服务端执行，避免双端叠加音效/效果
		}
		boolean deepSleep = !forcefulWakeUp && this.quirky$sleptLongEnough;
		int durationTicks = WakeUpLogic.durationTicks(deepSleep, QuirkyConfigHolder.get().wakeUpSlowFallingSeconds);
		if (durationTicks <= 0) {
			return;
		}
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, durationTicks));
		player.level().playSound(
			null,
			player.getX(),
			player.getY(),
			player.getZ(),
			SoundEvents.NOTE_BLOCK_CHIME,
			SoundSource.PLAYERS,
			0.3F,
			1.2F
		);
	}
}
