package dev.quirky.client.mixin;

import java.util.List;

import dev.quirky.client.tooltips.TooltipShiftHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ClockTooltipMixin {
	@Inject(method = "getTooltipLines", at = @At("RETURN"))
	private void quirky$appendTooltip(
		Item.TooltipContext context,
		@Nullable Player player,
		TooltipFlag flag,
		CallbackInfoReturnable<List<Component>> cir
	) {
		ItemStack stack = (ItemStack) (Object) this;
		if (player == null) {
			return;
		}
		// 高级 tooltip 模式：未按 Shift 时隐藏时钟行（提示行由 TooltipShiftGateMixin 追加）
		if (TooltipShiftHelper.shouldSuppress(stack)) {
			return;
		}
		TooltipDisplay display = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
		if (!flag.isCreative() && display.hideTooltip()) {
			return;
		}
		if (stack.is(Items.CLOCK)) {
			long dayTime = player.level().getDefaultClockTime();
			cir.getReturnValue().add(
				Component.translatable("tooltip.quirky.clock", dayTime / 24000L + 1L, formatTime(dayTime))
					.withStyle(ChatFormatting.GRAY)
			);
		}
	}

	private static String formatTime(long dayTime) {
		int ticks = (int) (dayTime % 24000L);
		int hours = (ticks / 1000 + 6) % 24;
		int minutes = (ticks % 1000) * 60 / 1000;
		return String.format("%02d:%02d", hours, minutes);
	}
}
