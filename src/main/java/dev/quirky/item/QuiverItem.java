package dev.quirky.item;

import java.util.Map;

import dev.quirky.config.QuirkyConfigHolder;
import dev.quirky.quiver.QuiverContents;
import dev.quirky.quiver.QuiverLogic;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * 箭袋：可染色的弹药容器。
 *
 * <ul>
 *   <li>潜行+右键 → 把背包里所有箭/烟花吸入（容量上限内）；</li>
 *   <li>右键 → 取出一组，优先副手空位 → 背包空位 → 掉落脚下；</li>
 *   <li>染色走原版 DYED_COLOR 组件（炼药锅洗色自动生效）。</li>
 * </ul>
 */
public class QuiverItem extends Item {

	public QuiverItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!QuirkyConfigHolder.get().quiverEnabled) {
			return InteractionResult.PASS;
		}
		ItemStack quiver = player.getItemInHand(hand);
		if (player.isShiftKeyDown()) {
			return absorb(level, player, quiver);
		}
		return extract(level, player, quiver);
	}

	private InteractionResult absorb(Level level, Player player, ItemStack quiver) {
		NonNullList<ItemStack> inventory = player.getInventory().getNonEquipmentItems();
		int capacity = QuirkyConfigHolder.get().quiverCapacity;
		QuiverLogic.AbsorbResult dryRun = QuiverLogic.absorb(
			quiver.getOrDefault(QuiverContents.TYPE, ItemContainerContents.EMPTY),
			inventory,
			capacity
		);
		if (dryRun.nothingConsumed()) {
			// 无弹药可吸（或已满）：无动作无声音
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			// 服务端用权威数据重算并应用
			QuiverLogic.AbsorbResult result = QuiverLogic.absorb(
				quiver.getOrDefault(QuiverContents.TYPE, ItemContainerContents.EMPTY),
				inventory,
				capacity
			);
			for (Map.Entry<Integer, Integer> entry : result.consumedBySlot().entrySet()) {
				inventory.get(entry.getKey()).shrink(entry.getValue());
			}
			quiver.set(QuiverContents.TYPE, result.contents());
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0F, 1.0F);
			ServerLevel serverLevel = (ServerLevel) level;
			serverLevel.sendParticles(ParticleTypes.CRIT, player.getX(), player.getEyeY(), player.getZ(),
				6, 0.3, 0.2, 0.3, 0.05);
		}
		return InteractionResult.SUCCESS;
	}

	private InteractionResult extract(Level level, Player player, ItemStack quiver) {
		QuiverLogic.ExtractResult dryRun = QuiverLogic.extractOne(
			quiver.getOrDefault(QuiverContents.TYPE, ItemContainerContents.EMPTY)
		);
		if (dryRun.extracted().isEmpty()) {
			// 空箭袋：无动作
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			quiver.set(QuiverContents.TYPE, dryRun.contents());
			giveOrDrop(level, player, dryRun.extracted());
			// 取出一组：音高略高以示区别
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.8F, 1.3F);
		}
		return InteractionResult.SUCCESS;
	}

	/** 优先副手空位 → 第一个背包空位 → 都满则掉落脚下（不吞物品）。
	 *  不用 Block.popResource（受 BLOCK_DROPS 规则门控，规则关时会吞物品）；
	 *  箭袋取箭不是方块破坏，直接生成 ItemEntity 绕开规则。 */
	private static void giveOrDrop(Level level, Player player, ItemStack stack) {
		Inventory inventory = player.getInventory();
		if (inventory.getItem(Inventory.SLOT_OFFHAND).isEmpty()) {
			inventory.setItem(Inventory.SLOT_OFFHAND, stack);
			return;
		}
		int freeSlot = inventory.getFreeSlot();
		if (freeSlot != -1) {
			inventory.setItem(freeSlot, stack);
			return;
		}
		if (level instanceof ServerLevel serverLevel) {
			ItemEntity item = new ItemEntity(serverLevel, player.getX(), player.getY() + 0.5, player.getZ(), stack);
			item.setPickUpDelay(20);
			serverLevel.addFreshEntity(item);
		}
	}
}
