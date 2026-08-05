package dev.quirky.mixin;

import java.util.function.Predicate;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.CrossbowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 弩自动装填也认烟花火箭（原版 getAllSupportedProjectiles 只认箭，烟花火箭只能手持装填——
 * 2026-08-05 用户反馈"非要把火箭拿到副手"）。
 * getProjectile（玩家自动装填）是该方法唯一调用点，村民等弩 AI 不走此路径，改动仅影响玩家。
 */
@Mixin(CrossbowItem.class)
public abstract class CrossbowItemAmmoMixin {
	@Inject(method = "getAllSupportedProjectiles", at = @At("HEAD"), cancellable = true)
	private void quirky$allowFireworksAutoLoad(CallbackInfoReturnable<Predicate<ItemStack>> cir) {
		cir.setReturnValue(stack -> stack.is(ItemTags.ARROWS) || stack.is(Items.FIREWORK_ROCKET));
	}
}
