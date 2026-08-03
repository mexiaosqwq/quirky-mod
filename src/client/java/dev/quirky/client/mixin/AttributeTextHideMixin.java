package dev.quirky.client.mixin;

import java.util.function.Consumer;

import dev.quirky.config.QuirkyConfigHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 未按 Shift 时隐藏原版 "Attribute Modifiers" 竖排文本段（空行 + 灰色槽位标题 + 修饰符行），
 * 由紧凑横条（ClientAttributeTooltipComponent）替代——对齐 Quark AttributeTooltips 的
 * removeAttributeTooltips 行为；按 Shift 时放行原版文本，横条同时隐藏（对照查看）。
 * 26.2 实现点：ItemStack.addAttributeTooltips 是本类私有方法，HEAD 取消安全。
 */
@Mixin(ItemStack.class)
public abstract class AttributeTextHideMixin {
	@Inject(method = "addAttributeTooltips", at = @At("HEAD"), cancellable = true)
	private void quirky$hideVanillaAttributeText(
		Consumer<Component> consumer, TooltipDisplay display, @Nullable Player player, CallbackInfo ci
	) {
		if (!QuirkyConfigHolder.get().attributeTooltip) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		// 单测/服务端无客户端实例视为未按 Shift（与 ClientAttributeTooltipComponent.shiftHidesLines 一致）
		if (minecraft == null || !minecraft.hasShiftDown()) {
			ci.cancel();
		}
	}
}
