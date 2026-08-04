package dev.quirky.item;

import dev.quirky.entity.BoomerangEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;

/**
 * 回旋镖：主手/副手右键投掷（生成 {@link BoomerangEntity} 实体，投出即离手），
 * 同一玩家已有活跃回旋镖时拒绝重复投掷。投掷音 ARROW_SHOOT（音高 0.8，更"呼"的挥出感）。
 * 耐久 250，每次完整飞行消耗 1 点（由实体返航时结算）。
 */
public class BoomerangItem extends Item {
	public BoomerangItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		ServerLevel serverLevel = (ServerLevel) level;
		if (!serverLevel.getEntities(EntityTypeTest.forClass(BoomerangEntity.class), e -> e.isOwnedBy(player)).isEmpty()) {
			return InteractionResult.FAIL;
		}
		ItemStack stack = player.getItemInHand(hand);
		int throwSlot = hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND;

		BoomerangEntity boomerang = new BoomerangEntity(serverLevel, player, stack, throwSlot);
		boomerang.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
		boomerang.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.0F, 0.0F);
		serverLevel.addFreshEntity(boomerang);
		serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 0.8F);
		if (!player.hasInfiniteMaterials()) {
			stack.consume(1, player);
		}
		return InteractionResult.SUCCESS;
	}
}
