package dev.quirky.mixin;

import dev.quirky.item.PetWhistleItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.item.component.CustomData;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 幻翼嘲讽：被口哨选中的幻翼在嘲讽期（NBT {@code taunt_until} 内）每 tick
 * 把盘旋锚点钉在目标（玩家）头顶上方 5 格，使 CircleAroundAnchor 的盘旋与
 * SweepAttack 的俯冲都发生在玩家附近——不再升空盘旋，可用剑稳定输出。
 *
 * <p>过期自动清除 NBT，恢复正常行为。若目标丢失（死亡/换目标），锚点保持
 * 上次值，由原版 goal 接管，等过期自清。</p>
 */
@Mixin(Phantom.class)
public abstract class PhantomTauntMixin {
	/** 嘲讽期盘旋锚点高度：玩家头顶上方格数。 */
	private static final int ANCHOR_ABOVE = 5;

	@Shadow
	private @Nullable BlockPos anchorPoint;

	@Inject(method = "tick", at = @At("TAIL"))
	private void quirky$refreshTauntAnchor(CallbackInfo ci) {
		Phantom phantom = (Phantom) (Object) this;
		CustomData data = phantom.get(DataComponents.CUSTOM_DATA);
		if (data == null || data.isEmpty()) {
			return;
		}
		CompoundTag tag = data.copyTag();
		long tauntUntil = tag.getLongOr(PetWhistleItem.TAUNT_UNTIL_KEY, -1L);
		if (tauntUntil < 0) {
			return;
		}
		if (phantom.level().getGameTime() > tauntUntil) {
			// 过期：清除标记，恢复正常盘旋行为
			tag.remove(PetWhistleItem.TAUNT_UNTIL_KEY);
			phantom.setComponent(DataComponents.CUSTOM_DATA, tag.isEmpty() ? CustomData.EMPTY : CustomData.of(tag));
			return;
		}
		LivingEntity target = phantom.getTarget();
		if (target == null || !target.isAlive()) {
			return;
		}
		// 每 tick 刷新锚点跟随玩家：盘旋半径与俯冲起点都被钉在玩家附近
		this.anchorPoint = target.blockPosition().above(ANCHOR_ABOVE);
	}
}
