package dev.quirky.item;

import java.util.function.Consumer;

import dev.quirky.fishbait.FishBaitEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * 鱼饵球：可投掷（同鸡蛋手感），落水生成诱鱼区。
 */
public class FishBaitItem extends Item implements ProjectileItem {
	public static final float PROJECTILE_SHOOT_POWER = 1.5F;

	public FishBaitItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		level.playSound(
			null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F)
		);
		if (level instanceof ServerLevel serverLevel) {
			Projectile.spawnProjectileFromRotation(FishBaitEntity::new, serverLevel, stack, player, 0.0F, PROJECTILE_SHOOT_POWER, 1.0F);
		}
		player.awardStat(Stats.ITEM_USED.get(this));
		stack.consume(1, player);
		return InteractionResult.SUCCESS;
	}

	@Override
	public Projectile asProjectile(Level level, Position position, ItemStack stack, Direction direction) {
		return new FishBaitEntity(level, position.x(), position.y(), position.z(), stack);
	}

	@Override
	public void appendHoverText(
		ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag
	) {
		builder.accept(Component.translatable("tooltip.quirky.fish_bait").withStyle(ChatFormatting.GRAY));
	}
}
