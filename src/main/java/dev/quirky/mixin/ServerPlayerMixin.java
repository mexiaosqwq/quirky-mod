package dev.quirky.mixin;

import dev.quirky.ModEntities;
import dev.quirky.totem.TotemEntity;
import dev.quirky.totem.TotemOfHoldingLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
	@Inject(
		method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerPlayer;dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V",
			shift = At.Shift.BEFORE
		)
	)
	private void quirky$totemProtectInventory(DamageSource source, CallbackInfo ci) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		boolean keepInventory = player.level().getGameRules().get(GameRules.KEEP_INVENTORY);
		if (!TotemOfHoldingLogic.shouldSpawnTotem(player, source, keepInventory)) {
			return;
		}
		TotemEntity.breakForOwner(player);
		List<ItemStackWithSlot> stored = TotemOfHoldingLogic.collectInventory(player);
		if (stored.isEmpty()) {
			return; // 空背包死亡不生成空图腾
		}
		TotemEntity totem = new TotemEntity(ModEntities.TOTEM, player.level());
		BlockPos pos = player.blockPosition();
		if (pos.getY() < 0) {
			pos = pos.atY(0);
		}
		totem.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		totem.initStored(player.getUUID(), stored);
		player.level().addFreshEntity(totem);
		player.getInventory().clearContent();
	}
}
