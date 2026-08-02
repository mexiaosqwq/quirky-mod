package dev.quirky.mixin;

import java.util.List;
import java.util.Optional;

import dev.quirky.config.QuirkyConfigHolder;
import dev.quirky.tooltips.AttributeTooltipComponent.AttributeLine;
import dev.quirky.tooltips.AttributeLineCollector;
import dev.quirky.tooltips.AttributeTooltipComponent;
import dev.quirky.tooltips.FoodTooltipComponent;
import dev.quirky.tooltips.ShulkerTooltipComponent;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
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
		// 只对潜影盒生效：箱子/熔炉等其他容器物品也可能带 CONTAINER 组件，
		// 不限定 tag 会把它们也渲染成潜影盒网格
		if (stack.is(ItemTags.SHULKER_BOXES)) {
			// 空盒无 CONTAINER 组件，用空内容兜底（仍显示 9x3 空网格）；
			// 盒色随外表（16 色，普通盒 null），客户端据此给底槽配色
			ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
			DyeColor color = stack.getItem() instanceof BlockItem blockItem
				&& blockItem.getBlock() instanceof ShulkerBoxBlock shulker ? shulker.getColor() : null;
			cir.setReturnValue(Optional.of(new ShulkerTooltipComponent(contents, color)));
		}
	}

	@Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
	private void quirky$attributeTooltip(ItemStack stack, CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
		if (!QuirkyConfigHolder.get().attributeTooltip) {
			return;
		}
		// 26.2 中 ENCHANTMENT 为数据包注册表，tooltip 调用路径无注册表访问，传 EMPTY 走栈上附魔组件读取
		List<AttributeLine> lines = AttributeLineCollector.collect(stack, RegistryAccess.EMPTY);
		if (!lines.isEmpty()) {
			cir.setReturnValue(Optional.of(new AttributeTooltipComponent(lines)));
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
