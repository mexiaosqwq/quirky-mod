package dev.quirky.mixin;

import dev.quirky.ModParticles;
import dev.quirky.particle.DyedCampfireSmokeOption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 营火染色烟（服务端权威 + 客户端粒子替换）：
 * - 手持染料右键点燃的营火 → 消耗 1 染料、烟柱染色；白染料复原（清色）；重染同时清夜光。
 * - 染料/荧光石粉/火药/烈焰粉以物品实体丢入营火 → 分别染色 / 夜光火星 / 一次性猛烟爆+火星 / 火焰星花迸发（不改变底色；火药/烈焰粉均为一次性表演）。
 * - 静态 makeParticles 替换为染色粒子；placeLiquid 熄灭（水浇）时清色清夜光。
 * 注入点均声明在 CampfireBlock 本类：useItemOn(CampfireBlock.java:91)、entityInside(:116)、
 * makeParticles(:233 静态)、placeLiquid(:202)。
 */
@Mixin(CampfireBlock.class)
public abstract class CampfireBlockMixin {

	/** 染色烟限时（tick）：约 5 分钟，到期自动恢复无色。 */
	private static final int SMOKE_COLOR_TICKS = 6000;
	/** 萤石夜光限时（tick）：约 2 分钟，到期自动熄灭。 */
	private static final int GLOW_TICKS = 2400;

	/** 手持染料右键（useItemOn HEAD，未点燃时走原版）。 */
	@Inject(
		method = "useItemOn(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void quirky$dyeCampfire(
		ItemStack itemStack,
		BlockState state,
		Level level,
		BlockPos pos,
		Player player,
		InteractionHand hand,
		BlockHitResult hitResult,
		CallbackInfoReturnable<InteractionResult> cir
	) {
		if (!state.getValue(CampfireBlock.LIT)) {
			return; // 未点燃：走原版行为
		}
		ItemStack itemInHand = player.getItemInHand(hand);
		if (!(itemInHand.getItem() instanceof DyeItem)) {
			return;
		}
		DyeColor dye = itemInHand.get(DataComponents.DYE);
		if (dye == null) {
			return;
		}
		if (level.isClientSide()) {
			cir.setReturnValue(InteractionResult.SUCCESS); // 挥臂 + 发包，实际处理在服务端
			return;
		}
		if (level.getBlockEntity(pos) instanceof CampfireBlockEntity campfire) {
			applyDye((ServerLevel) level, pos, state, campfire, dye);
			itemInHand.consume(1, player);
		}
		cir.setReturnValue(InteractionResult.SUCCESS_SERVER);
	}

	/** 物品实体丢入营火（entityInside HEAD）：染料染色 / 荧光石粉夜光 / 火药烟爆。 */
	@Inject(
		method = "entityInside(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/InsideBlockEffectApplier;Z)V",
		at = @At("HEAD")
	)
	private void quirky$consumeItemInFire(
		BlockState state,
		Level level,
		BlockPos pos,
		Entity entity,
		InsideBlockEffectApplier effectApplier,
		boolean isPrecise,
		CallbackInfo ci
	) {
		if (!state.getValue(CampfireBlock.LIT)) {
			return;
		}
		if (!(entity instanceof ItemEntity itemEntity) || !(level instanceof ServerLevel serverLevel)) {
			return; // 服务端权威消费
		}
		ItemStack stack = itemEntity.getItem();
		if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof CampfireBlockEntity campfire)) {
			return;
		}
		CampfireBlockEntityAccessor accessor = (CampfireBlockEntityAccessor) campfire;
		if (stack.getItem() instanceof DyeItem) {
			DyeColor dye = stack.get(DataComponents.DYE);
			if (dye == null) {
				return;
			}
			applyDye(serverLevel, pos, state, campfire, dye);
		} else if (stack.is(Items.GLOWSTONE_DUST)) {
			accessor.quirky$setGlow(true);
			accessor.quirky$setGlowTicks(GLOW_TICKS); // 约 2 分钟限时，到期自动熄灭
			notifyChanged(serverLevel, pos, state, campfire);
			serverLevel.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3F, 1.0F);
		} else if (stack.is(Items.GUNPOWDER)) {
			gunpowderBurst(serverLevel, pos, state, accessor.quirky$getSmokeColor());
		} else if (stack.is(Items.BLAZE_POWDER)) {
			blazeBurst(serverLevel, pos, state);
		} else {
			return;
		}
		stack.shrink(1);
		if (stack.isEmpty()) {
			itemEntity.discard();
		}
	}

	/** 染色烟粒子替换（makeParticles 静态方法 HEAD）：有色则生成染色粒子并取消原版。 */
	@Inject(method = "makeParticles(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ZZ)V", at = @At("HEAD"), cancellable = true)
	private static void quirky$dyedSmokeParticles(Level level, BlockPos pos, boolean isSignalFire, boolean smoking, CallbackInfo ci) {
		if (!(level.getBlockEntity(pos) instanceof CampfireBlockEntity campfire)) {
			return;
		}
		CampfireBlockEntityAccessor accessor = (CampfireBlockEntityAccessor) campfire;
		int color = accessor.quirky$getSmokeColor();
		boolean replaced = false;
		if (color != -1) {
			RandomSource random = level.getRandom();
			DyedCampfireSmokeOption smoke = new DyedCampfireSmokeOption(color, isSignalFire);
			level.addAlwaysVisibleParticle(
				smoke,
				true,
				pos.getX() + 0.5 + random.nextDouble() / 3.0 * (random.nextBoolean() ? 1 : -1),
				pos.getY() + random.nextDouble() + random.nextDouble(),
				pos.getZ() + 0.5 + random.nextDouble() / 3.0 * (random.nextBoolean() ? 1 : -1),
				0.0,
				0.07,
				0.0
			);
			if (smoking) {
				level.addParticle(
					new DyedCampfireSmokeOption(color, isSignalFire),
					pos.getX() + 0.5 + random.nextDouble() / 4.0 * (random.nextBoolean() ? 1 : -1),
					pos.getY() + 0.4,
					pos.getZ() + 0.5 + random.nextDouble() / 4.0 * (random.nextBoolean() ? 1 : -1),
					0.0,
					0.005,
					0.0
				);
			}
			replaced = true;
		}
		if (accessor.quirky$getGlow() && level.isDarkOutside()) {
			RandomSource random = level.getRandom();
			int glowColor = color != -1 ? color : 0xFFE082; // 无色烟时用暖黄微光
			// 夜光火星：每 tick 4 颗向上飘散（原 1 颗太稀，玩家反馈“特效太少”）
			for (int i = 0; i < 4; i++) {
				level.addAlwaysVisibleParticle(
					ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, ARGB.color(255, glowColor)),
					pos.getX() + 0.5 + random.nextDouble() * 0.8 * (random.nextBoolean() ? 1 : -1),
					pos.getY() + random.nextDouble() * 1.5,
					pos.getZ() + 0.5 + random.nextDouble() * 0.8 * (random.nextBoolean() ? 1 : -1),
					(random.nextDouble() - 0.5) * 0.02,
					0.05 + random.nextDouble() * 0.03,
					(random.nextDouble() - 0.5) * 0.02
				);
			}
		}
		if (replaced) {
			ci.cancel(); // 只替换烟柱；无色+夜光时保留原版烟
		}
	}

	/** 灭火即清色清夜光：统一在 {@link CampfireBlockEntityStateMixin} 的 setBlockState 注入处理（覆盖水浇/铲子扑灭/投射物等所有 LIT 变 false 路径）。重新点燃后是无色烟。 */

	/** 染料落地：染色 + 嘶声 + 对应色烟爆发 + 同步。白染料清色（复原，0 tick）；重染清夜光。 */
	private static void applyDye(ServerLevel level, BlockPos pos, BlockState state, CampfireBlockEntity campfire, DyeColor dye) {
		CampfireBlockEntityAccessor accessor = (CampfireBlockEntityAccessor) campfire;
		boolean isWhite = dye == DyeColor.WHITE;
		accessor.quirky$setSmokeColor(isWhite ? -1 : dye.getTextureDiffuseColor());
		accessor.quirky$setSmokeColorTicks(isWhite ? 0 : SMOKE_COLOR_TICKS); // 约 5 分钟限时，白染料立即清
		accessor.quirky$setGlow(false);
		accessor.quirky$setGlowTicks(0);
		notifyChanged(level, pos, state, campfire);
		level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3F, 1.0F);
		level.sendParticles(
			new DyedCampfireSmokeOption(dye.getTextureDiffuseColor(), state.getValue(CampfireBlock.SIGNAL_FIRE)),
			pos.getX() + 0.5,
			pos.getY() + 0.5,
			pos.getZ() + 0.5,
			12,
			0.3,
			0.4,
			0.3,
			0.05
		);
	}

	/** 火药烟爆：已染色用该色喷一大股 + 火星；未染色喷中性灰白浓烟 + 火星。一次性猛爆，不改变底色。 */
	private static void gunpowderBurst(ServerLevel level, BlockPos pos, BlockState state, int currentColor) {
		level.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.BLOCKS, 0.9F, 1.0F);
		boolean signalFire = state.getValue(CampfireBlock.SIGNAL_FIRE);
		int burstColor = currentColor != -1 ? currentColor : 0xD3D3D3; // 未染色 → 中性浅灰
		level.sendParticles(
			new DyedCampfireSmokeOption(burstColor, signalFire),
			pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
			45, 0.7, 0.8, 0.7, 0.13
		);
		// 火星：FLAME 向外迸射 + LAVA 闪烁
		level.sendParticles(ParticleTypes.FLAME,
			pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
			18, 0.5, 0.4, 0.5, 0.16);
		level.sendParticles(ParticleTypes.LAVA,
			pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5,
			8, 0.4, 0.3, 0.4, 0.0);
	}

	/** 烈焰粉火焰星花：迸发 FLAME + LAVA 火星，像拨旺火堆，暖橙一次性表演（不染色不夜光）。 */
	private static void blazeBurst(ServerLevel level, BlockPos pos, BlockState state) {
		level.playSound(null, pos, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 0.8F, 1.3F);
		level.sendParticles(ParticleTypes.FLAME,
			pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
			28, 0.45, 0.5, 0.45, 0.18);
		level.sendParticles(ParticleTypes.LAVA,
			pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5,
			12, 0.4, 0.3, 0.4, 0.0);
	}

	private static void notifyChanged(ServerLevel level, BlockPos pos, BlockState state, CampfireBlockEntity campfire) {
		campfire.setChanged();
		level.sendBlockUpdated(pos, state, state, 3);
	}
}
