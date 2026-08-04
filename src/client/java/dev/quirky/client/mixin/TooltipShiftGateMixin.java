package dev.quirky.client.mixin;

import java.util.List;

import dev.quirky.client.tooltips.TooltipShiftHelper;
import dev.quirky.tooltips.TooltipShiftState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 高级 tooltip 模式（按 Shift 查看）入口：
 * <ul>
 *   <li>{@code getTooltipLines} HEAD：计算本次调用是否应抑制 mod 增强 tooltip 并写入
 *       {@link TooltipShiftState}。搜索索引构建（SessionSearchTrees 后台线程，
 *       player == null）与创造模式检索路径放行——不抑制，内容保持可搜索。</li>
 *   <li>{@code getTooltipLines} RETURN：抑制生效时在末尾追加"按 Shift 查看详情"提示行
 *       （深灰斜体），并清理 ThreadLocal。</li>
 * </ul>
 * 各 tooltip 来源（物品 {@code appendHoverText}）经 {@link TooltipShiftState#isSuppressing()}
 * 决定是否添加内容；时钟行在 {@link ClockTooltipMixin} 内独立判定，不依赖注入顺序。
 */
@Mixin(ItemStack.class)
public abstract class TooltipShiftGateMixin {
	@Inject(method = "getTooltipLines", at = @At("HEAD"))
	private void quirky$enterShiftState(
		Item.TooltipContext context, @Nullable Player player, TooltipFlag flag, CallbackInfo ci
	) {
		if (player == null) {
			TooltipShiftState.enter(false);
			return;
		}
		TooltipShiftState.enter(TooltipShiftHelper.shouldSuppress((ItemStack) (Object) this));
	}

	@Inject(method = "getTooltipLines", at = @At("RETURN"))
	private void quirky$appendShiftHint(
		Item.TooltipContext context,
		@Nullable Player player,
		TooltipFlag flag,
		CallbackInfoReturnable<List<Component>> cir
	) {
		try {
			if (TooltipShiftState.isSuppressing()) {
				cir.getReturnValue().add(
					Component.translatable("tooltip.quirky.shift_details")
						.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)
				);
			}
		} finally {
			TooltipShiftState.exit();
		}
	}
}
