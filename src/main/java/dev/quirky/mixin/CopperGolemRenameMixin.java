package dev.quirky.mixin;

import dev.quirky.copper_golem_ai.CopperGolemAiService;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 右键改名：玩家空手右键 + 傀儡手里空 → 进入待命名（聊天栏输入名字）；
 * 傀儡手里有物品时空手右键仍走原版（拿走物品）。
 */
@Mixin(CopperGolem.class)
public abstract class CopperGolemRenameMixin {

	@Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
	private void quirky$renameOnEmptyHand(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (hand != InteractionHand.MAIN_HAND) {
			return;
		}
		CopperGolem golem = (CopperGolem) (Object) this;
		if (!player.getMainHandItem().isEmpty() || !golem.getMainHandItem().isEmpty()) {
			return; // 手持物品或傀儡手里有东西 → 原版逻辑
		}
		if (CopperGolemAiService.tryEnterRename(player, golem)) {
			cir.setReturnValue(InteractionResult.SUCCESS);
		}
	}
}
