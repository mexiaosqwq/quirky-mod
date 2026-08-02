package dev.quirky.mixin;

import java.util.Optional;

import dev.quirky.config.QuirkyConfigHolder;
import dev.quirky.tooltips.FoodTooltipComponent;
import dev.quirky.tooltips.ShulkerTooltipComponent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Item.getTooltipImage 的统一扩展点：潜影盒/食物/属性 tooltip 三分支。
 * 与 MapTooltipMixin（地图分支）互斥条件，互不干扰。
 */
@Mixin(Item.class)
public abstract class TooltipDetailsMixin {
	@Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
	private void quirky$shulkerTooltip(ItemStack stack, CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
		if (!QuirkyConfigHolder.get().shulkerTooltip) {
			return;
		}
		if (stack.has(DataComponents.CONTAINER)) {
			cir.setReturnValue(Optional.of(new ShulkerTooltipComponent(stack.get(DataComponents.CONTAINER))));
		}
	}

	@Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
	private void quirky$foodTooltip(ItemStack stack, CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
		if (!QuirkyConfigHolder.get().foodTooltip) {
			return;
		}
		// FOOD 组件无默认值：非食物物品 stack.get 返回 null
		var food = stack.get(DataComponents.FOOD);
		if (food != null) {
			cir.setReturnValue(Optional.of(new FoodTooltipComponent(food)));
		}
	}
}
