package dev.quirky.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MelonSeedMixin {
	@Inject(
		method = "completeUsingItem",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;",
			shift = At.Shift.AFTER
		)
	)
	private void quirky$dropMelonSeed(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self instanceof ServerPlayer player
			&& !player.hasInfiniteMaterials()
			&& self.getUseItem().is(Items.MELON_SLICE)) {
			ItemStack seed = new ItemStack(Items.MELON_SEEDS);
			if (!player.getInventory().add(seed)) {
				player.drop(seed, false);
			}
		}
	}
}
