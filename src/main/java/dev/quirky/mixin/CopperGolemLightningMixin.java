package dev.quirky.mixin;

import dev.quirky.copper_golem_ai.CopperGolemAiService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 闪电感知：傀儡被闪电劈中（原版行为=除锈一级）时记录时间，供 AI 感知（get_self_status）。 */
@Mixin(CopperGolem.class)
public abstract class CopperGolemLightningMixin {

	@Inject(method = "thunderHit", at = @At("TAIL"))
	private void quirky$recordLightning(ServerLevel level, LightningBolt lightningBolt, CallbackInfo ci) {
		CopperGolemAiService.recordLightning((CopperGolem) (Object) this);
	}
}
