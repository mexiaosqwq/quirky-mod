package dev.quirky.client.mixin;

import java.util.Optional;

import dev.quirky.client.tooltips.TooltipShiftHelper;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 高级 tooltip 模式：未按 Shift 时取消 {@code Item.getTooltipImage} 的 mod 增强组件
 * （地图预览 / 潜影盒内容网格 / 箭袋内容网格），按 Shift 时放行。
 * 食物与属性组件不受控（{@link TooltipShiftHelper#hasGatedContent} 不含它们），默认始终显示。
 * {@code getTooltipImage} 仅客户端 tooltip 渲染调用，无搜索索引路径，直接判定即可。
 */
@Mixin(Item.class)
public abstract class TooltipComponentHideMixin {
	@Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
	private void quirky$hideTooltipImage(ItemStack stack, CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
		if (TooltipShiftHelper.shouldSuppress(stack)) {
			cir.setReturnValue(Optional.empty());
		}
	}
}
