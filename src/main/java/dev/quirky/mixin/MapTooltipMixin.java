package dev.quirky.mixin;

import java.util.Optional;

import dev.quirky.config.QuirkyConfigHolder;
import dev.quirky.tooltips.MapTooltipComponent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class MapTooltipMixin {
	@Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
	private void quirky$mapTooltip(ItemStack stack, CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
		if (!QuirkyConfigHolder.get().mapPreview) {
			return;
		}
		if (stack.has(DataComponents.MAP_ID)) {
			cir.setReturnValue(Optional.of(new MapTooltipComponent(stack.get(DataComponents.MAP_ID))));
		}
	}
}
