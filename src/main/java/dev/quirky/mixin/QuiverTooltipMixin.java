package dev.quirky.mixin;

import java.util.Optional;

import dev.quirky.ModItems;
import dev.quirky.config.QuirkyConfigHolder;
import dev.quirky.quiver.QuiverContents;
import dev.quirky.tooltips.QuiverTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 箭袋 tooltip：getTooltipImage HEAD 注入输出内容网格（含数量）。
 * 空箭袋也输出组件——客户端组件渲染"空"字样而非空格子。
 */
@Mixin(Item.class)
public abstract class QuiverTooltipMixin {
	@Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
	private void quirky$quiverContents(ItemStack stack, CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
		if (cir.isCancelled()) {
			return;
		}
		if (!stack.is(ModItems.QUIVER)) {
			return;
		}
		ItemContainerContents contents = stack.getOrDefault(QuiverContents.TYPE, ItemContainerContents.EMPTY);
		cir.setReturnValue(Optional.of(new QuiverTooltipComponent(contents, QuirkyConfigHolder.get().quiverCapacity)));
	}
}
