package dev.quirky.mixin;

import dev.quirky.ModEntities;
import dev.quirky.config.QuirkyConfigHolder;
import dev.quirky.totem.TotemEntity;
import dev.quirky.totem.TotemOfHoldingLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.level.Level;
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
		if (!QuirkyConfigHolder.get().totemEnabled) {
			return;
		}
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
		} else {
			// 土径/耕地等非整高方块顶部死亡：blockPosition() floor 取整使基准低 1 格，图腾贴地——上移一格修正
			pos = TotemOfHoldingLogic.raiseSpawnBase(player.getY(), pos);
		}
		int offset = QuirkyConfigHolder.get().spawnHeightOffset;
		BlockPos totemSpot = TotemOfHoldingLogic.findSpawnPosition(player.level(), pos, offset);
		totem.setPos(pos.getX() + 0.5, totemSpot.getY() + 0.5, pos.getZ() + 0.5);
		totem.initStored(player.getUUID(), stored);
		player.level().addFreshEntity(totem);
		player.level().playSound(null, totem.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.0F);
		BlockPos spawnPos;
		ServerPlayer.RespawnConfig respawnConfig = player.getRespawnConfig();
		if (respawnConfig != null) {
			spawnPos = respawnConfig.respawnData().globalPos().pos();
		} else {
			spawnPos = player.level().getLevelData().getRespawnData().globalPos().pos();
		}
		ResourceKey<Level> deathDimension = player.level().dimension();
		String dimensionKey = "dimension." + deathDimension.identifier().getNamespace() + "." + deathDimension.identifier().getPath();
		Component dimensionName = Component.translatableWithFallback(dimensionKey, dimensionKey);
		boolean sameDimension = deathDimension.equals(player.getRespawnConfig() != null
			? player.getRespawnConfig().respawnData().globalPos().dimension()
			: player.level().getLevelData().getRespawnData().globalPos().dimension());
		if (sameDimension) {
			int dx = pos.getX() - spawnPos.getX();
			int dz = pos.getZ() - spawnPos.getZ();
			int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
			player.sendSystemMessage(Component.translatable("message.quirky.totem_spawned",
				dimensionName, pos.getX(), pos.getY(), pos.getZ(), distance));
		} else {
			player.sendSystemMessage(Component.translatable("message.quirky.totem_spawned_cross",
				dimensionName, pos.getX(), pos.getY(), pos.getZ()));
		}
		player.getInventory().clearContent();
	}
}
