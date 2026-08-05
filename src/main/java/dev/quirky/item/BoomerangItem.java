package dev.quirky.item;

import dev.quirky.entity.BoomerangEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;

/**
 * 回旋镖：主手/副手右键**蓄力投掷**（bow 模式：use 启动蓄力，releaseUsing 释放投出）。
 * 蓄力力度 0.4~1.5，影响初速与射程——轻点近程小弧，满蓄远程大弧，自然产生多种轨迹。
 * 同一玩家已有活跃回旋镖时拒绝重复投掷。投掷音 ARROW_SHOOT（音高随力度变化）。
 * 耐久 250，每次完整飞行消耗 1 点（由实体返航时结算）。
 */
public class BoomerangItem extends Item {
	/** 满蓄 tick（1 秒）。 */
	private static final int FULL_CHARGE_TICKS = 20;
	/** 最低力度（轻点仍能投出）。 */
	private static final float MIN_POWER = 0.4F;
	/** 最高力度（满蓄）。 */
	private static final float MAX_POWER = 1.5F;

	public BoomerangItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			return InteractionResult.CONSUME;
		}
		// 已有活跃回旋镖时拒绝（蓄力前即检查，避免白蓄）
		ServerLevel serverLevel = (ServerLevel) level;
		if (!serverLevel.getEntities(EntityTypeTest.forClass(BoomerangEntity.class), e -> e.isOwnedBy(player)).isEmpty()) {
			return InteractionResult.FAIL;
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return FULL_CHARGE_TICKS;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.SPEAR;
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
		if (!(entity instanceof Player player) || level.isClientSide()) {
			return false;
		}
		int timeHeld = this.getUseDuration(stack, entity) - remainingTime;
		float power = powerForTime(timeHeld);
		if (power < MIN_POWER * 0.9F) {
			// 蓄力过短（几乎没蓄）不投出，物品不消耗
			return false;
		}

		ServerLevel serverLevel = (ServerLevel) level;
		// 投掷前再次确认无活跃回旋镖（蓄力期间可能上一只刚回来）
		if (!serverLevel.getEntities(EntityTypeTest.forClass(BoomerangEntity.class), e -> e.isOwnedBy(player)).isEmpty()) {
			return false;
		}
		InteractionHand hand = player.getUsedItemHand();
		int throwSlot = hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND;
		boolean clockwise = hand == InteractionHand.MAIN_HAND;

		BoomerangEntity boomerang = new BoomerangEntity(serverLevel, player, stack, throwSlot, clockwise, power);
		float throwSpeed = (float) (0.7 * power);
		boomerang.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
		boomerang.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, throwSpeed, 0.0F);
		serverLevel.addFreshEntity(boomerang);
		// 音高随力度：满蓄更低沉（0.7），轻点更清脆（1.1）
		serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.1F - power * 0.27F);
		if (!player.hasInfiniteMaterials()) {
			stack.consume(1, player);
		}
		return true;
	}

	/** 蓄力力度：0~FULL 时线性 0→1，FULL+ 钳 1；再映射到 [0, MAX_POWER]，最低 MIN_POWER。 */
	private static float powerForTime(int timeHeld) {
		if (timeHeld <= 0) {
			return 0.0F;
		}
		float t = Math.min(1.0F, (float) timeHeld / FULL_CHARGE_TICKS);
		return MIN_POWER + (MAX_POWER - MIN_POWER) * t;
	}
}
