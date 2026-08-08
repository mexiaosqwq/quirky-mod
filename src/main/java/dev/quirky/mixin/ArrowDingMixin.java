package dev.quirky.mixin;

import java.util.List;

import dev.quirky.config.QuirkyConfigHolder;
import dev.quirky.ding.ArrowDingLogic;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 任意箭（原版箭/药箭/火把箭等 AbstractArrow 子类）命中生物时播放一声清脆"叮"。
 * - 命中金属甲目标改用金属哢声（ARMOR_EQUIP_IRON）；暴击提高音高；击杀音量 ×1.2。
 * - 命中方块/非生物实体、目标举盾成功格挡不响。
 * - 音效以射手为中心播放（射手听到全音量，远射也不衰减）；非玩家射手（骷髅等）
 *   保持目标位置播放。仅服务端播放。
 * 注入点：AbstractArrow.onHitEntity(EntityHitResult) 声明在本类（mcsrc AbstractArrow.java:419），
 * 描述符 (Lnet/minecraft/world/phys/EntityHitResult;)V。
 */
@Mixin(AbstractArrow.class)
public abstract class ArrowDingMixin {

	@Inject(method = "onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V", at = @At("TAIL"))
	private void quirky$playDing(EntityHitResult hitResult, CallbackInfo ci) {
		if (!QuirkyConfigHolder.get().arrowDingEnabled) {
			return;
		}
		AbstractArrow arrow = (AbstractArrow) (Object) this;
		Level level = arrow.level();
		if (level.isClientSide()) {
			return;
		}
		Entity entity = hitResult.getEntity();
		if (!(entity instanceof LivingEntity living)) {
			return;
		}
		if (living.isBlocking()) {
			return; // 举盾成功格挡：箭被弹开，不配拥有成就感
		}
		ArrowDingLogic.TargetKind kind = ArrowDingLogic.hasMetalArmor(armorItems(living))
			? ArrowDingLogic.TargetKind.LIVING_METAL_ARMOR
			: ArrowDingLogic.TargetKind.LIVING_UNARMORED;
		boolean crit = arrow.isCritArrow();
		boolean kill = !living.isAlive();
		ArrowDingLogic.resolve(kind, crit, kill, QuirkyConfigHolder.get().arrowDingVolume)
			.ifPresent(ding -> {
				// 玩家射手：以射手位置播放（自身全音量）；非玩家射手：保持目标位置
				Entity owner = arrow.getOwner();
				double x = owner != null ? owner.getX() : living.getX();
				double y = owner != null ? owner.getY() : living.getY();
				double z = owner != null ? owner.getZ() : living.getZ();
				level.playSound(
					null,
					x,
					y,
					z,
					ding.sound(),
					SoundSource.PLAYERS,
					ding.volume(),
					ding.pitch()
				);
			});
	}

	private static List<ItemStack> armorItems(LivingEntity living) {
		return List.of(
			living.getItemBySlot(EquipmentSlot.HEAD),
			living.getItemBySlot(EquipmentSlot.CHEST),
			living.getItemBySlot(EquipmentSlot.LEGS),
			living.getItemBySlot(EquipmentSlot.FEET)
		);
	}
}
