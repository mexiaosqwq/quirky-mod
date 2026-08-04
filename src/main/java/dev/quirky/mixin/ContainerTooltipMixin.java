package dev.quirky.mixin;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TooltipProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * 隐藏潜影盒 tooltip 底部原版的 CONTAINER 组件文本行（如"包含物品"）。
 *
 * <p>26.2 的 {@link ItemStack#addToTooltip} 对 CONTAINER 组件会追加一行内容描述，
 * 与我们的 9x3 网格 UI 重复（用户验收：纯属多余）。仅当 shulkerTooltip 开启且
 * 物品是潜影盒时取消该行；箱子等其他容器物品保持原版文本。
 */
@Mixin(ItemStack.class)
public abstract class ContainerTooltipMixin {
	@Inject(method = "addToTooltip", at = @At("HEAD"), cancellable = true)
	private <T extends TooltipProvider> void quirky$hideShulkerContainerLine(
		DataComponentType<T> type,
		Item.TooltipContext context,
		TooltipDisplay display,
		Consumer<Component> consumer,
		TooltipFlag flag,
		CallbackInfo ci
	) {
		if (type != DataComponents.CONTAINER) {
			return;
		}
		if (((ItemStack) (Object) this).is(ItemTags.SHULKER_BOXES)) {
			ci.cancel();
		}
	}
}
