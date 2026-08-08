package dev.quirky.client.mixin;

import java.util.function.Consumer;

import dev.quirky.client.tooltips.AttributeTooltipVisibility;
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
 * 隐藏条件与横条显示条件互斥等价（见 {@link AttributeTooltipVisibility}），
 * 横条无替代内容（仅非 6 类属性修饰符）时不隐藏，避免属性信息静默丢失。
 * 26.2 实现点：ItemStack.addAttributeTooltips 是本类私有方法，HEAD 取消安全；
 * 创造/配方搜索索引（SessionSearchTrees）经 getTooltipLines 传 null player 构建，
 * 该路径放行，属性文本保留可搜索。
 */
@Mixin(ItemStack.class)
public abstract class AttributeTextHideMixin {
	@Inject(method = "addAttributeTooltips", at = @At("HEAD"), cancellable = true)
	private void quirky$hideVanillaAttributeText(
		Consumer<Component> consumer, TooltipDisplay display, @Nullable Player player, CallbackInfo ci
	) {
		if (!QuirkyConfigHolder.get().attributeTooltipEnabled) {
			return; // 放行原版属性文本段
		}
		// 搜索索引构建路径（player == null），不干预
		if (player == null) {
			return;
		}
		if (AttributeTooltipVisibility.vanillaTextHidden(
			Minecraft.getInstance(), (ItemStack) (Object) this
		)) {
			ci.cancel();
		}
	}
}
