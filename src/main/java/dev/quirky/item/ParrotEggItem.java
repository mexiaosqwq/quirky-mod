package dev.quirky.item;

import dev.quirky.parrotegg.ParrotEggEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.level.Level;

/**
 * 鹦鹉蛋：投掷手感与鸡蛋一致（抛出弧线、无伤害、创造不消耗），服务端由 ParrotEggEntity 决定孵化。
 * 仅合成获取（鸡蛋 + 羽毛），见 data/quirky/recipe/parrot_egg.json。
 */
public class ParrotEggItem extends Item implements ProjectileItem {
	public static final float PROJECTILE_SHOOT_POWER = 1.5F;

	public ParrotEggItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack itemStack = player.getItemInHand(hand);
		level.playSound(
			null,
			player.getX(),
			player.getY(),
			player.getZ(),
			SoundEvents.EGG_THROW,
			SoundSource.PLAYERS,
			0.5F,
			0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F)
		);
		if (level instanceof ServerLevel serverLevel) {
			Projectile.spawnProjectileFromRotation(ParrotEggEntity::new, serverLevel, itemStack, player, 0.0F, PROJECTILE_SHOOT_POWER, 1.0F);
		}
		player.awardStat(Stats.ITEM_USED.get(this));
		itemStack.consume(1, player);
		return InteractionResult.SUCCESS;
	}

	@Override
	public Projectile asProjectile(Level level, Position position, ItemStack itemStack, Direction direction) {
		return new ParrotEggEntity(level, position.x(), position.y(), position.z(), itemStack);
	}
}
