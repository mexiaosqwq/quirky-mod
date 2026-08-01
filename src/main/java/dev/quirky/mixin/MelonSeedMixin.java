package dev.quirky.mixin;

import dev.quirky.food.MelonSeedHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class MelonSeedMixin {
	@Redirect(
		method = "completeUsingItem",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"
		)
	)
	private ItemStack quirky$finishUsingMelon(ItemStack stack, Level level, LivingEntity entity) {
		return MelonSeedHandler.finishUsing(stack, level, entity);
	}
}
