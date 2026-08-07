package dev.quirky.mixin;

import dev.quirky.copper_golem_ai.CopperGolemAiService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 铜傀儡搬运任务 tick 注入：服务端 customServerAiStep 尾部跑状态机（无活跃任务立即返回）。 */
@Mixin(CopperGolem.class)
public abstract class CopperGolemTransportMixin {

	@Inject(method = "customServerAiStep", at = @At("TAIL"))
	private void quirky$tickTransport(ServerLevel level, CallbackInfo ci) {
		CopperGolemAiService.tickTransport((CopperGolem) (Object) this, level);
	}
}
