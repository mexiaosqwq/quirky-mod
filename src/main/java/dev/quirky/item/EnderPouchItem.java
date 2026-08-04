package dev.quirky.item;

import java.util.List;

import dev.quirky.ModItems;
import dev.quirky.config.QuirkyConfigHolder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 末影袋：便携末影箱入口。
 *
 * <ul>
 *   <li>右键（任意手）→ 服务端打开个人末影箱菜单（与原版末影箱共享同一库存）；</li>
 *   <li>潜行+右键 → 把另一只手上的物品快速塞入末影箱第一个空位；</li>
 *   <li>末影共鸣（配置开）：开启袋子 16 格内有末影人时 10% 概率激怒一只。</li>
 * </ul>
 */
public class EnderPouchItem extends Item {

	private static final Component CONTAINER_TITLE = Component.translatable("container.enderchest");
	private static final float RESONANCE_CHANCE = 0.1F;
	private static final int RESONANCE_RANGE = 16;

	public EnderPouchItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!QuirkyConfigHolder.get().enderPouchEnabled) {
			return InteractionResult.PASS;
		}
		if (player.isShiftKeyDown()) {
			return quickStore(level, player, hand);
		}
		return open(level, player);
	}

	/** 普通右键：打开末影箱菜单（仅服务端），播放开盖音效 + 末影粒子 + 共鸣判定。 */
	private InteractionResult open(Level level, Player player) {
		if (!level.isClientSide()) {
			PlayerEnderChestContainer container = player.getEnderChestInventory();
			if (container != null) {
				player.openMenu(new SimpleMenuProvider(
					(containerId, inventory, p) -> ChestMenu.threeRows(containerId, inventory, container),
					CONTAINER_TITLE
				));
				level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENDER_CHEST_OPEN, SoundSource.BLOCKS, 0.6F, 1.0F);
				if (level instanceof ServerLevel serverLevel) {
					Vec3 handPos = player.getEyePosition().add(player.getLookAngle().scale(0.4));
					serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL,
						handPos.x, handPos.y, handPos.z, 3, 0.15, 0.15, 0.15, 0.0);
					triggerEnderResonance(serverLevel, player);
				}
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** 潜行+右键：把另一只手上的物品塞入末影箱；失败（满/放不下）低沉反馈，物品留手。 */
	private InteractionResult quickStore(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide()) {
			PlayerEnderChestContainer container = player.getEnderChestInventory();
			if (container == null) {
				return InteractionResult.PASS;
			}
			InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
			ItemStack toStore = player.getItemInHand(otherHand);
			if (toStore.isEmpty()) {
				return InteractionResult.PASS;
			}
			// 末影袋本身不能塞进末影箱（防止把自己锁进容器）；静默无反馈
			if (toStore.is(ModItems.ENDER_POUCH)) {
				return InteractionResult.SUCCESS;
			}
			// container.addItem 拷贝输入、只存入放得下的部分并返回剩余；必须用手槽引用本身传入，
			// 取回 remaining 写回手槽，避免部分存入时手槽仍是原数量（复制 bug）
			ItemStack remaining = container.addItem(toStore);
			player.setItemInHand(otherHand, remaining);
			if (remaining.isEmpty()) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENDER_CHEST_OPEN, SoundSource.PLAYERS, 0.5F, 1.6F);
			} else {
				// 末影箱已满/放不下部分：低沉唔声反馈，remaining 已写回手槽不吞
				level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENDER_CHEST_CLOSE, SoundSource.PLAYERS, 0.5F, 0.5F);
			}
		} else if (otherHandEmpty(player, hand)) {
			return InteractionResult.PASS;
		}
		return InteractionResult.SUCCESS;
	}

	private static boolean otherHandEmpty(Player player, InteractionHand hand) {
		InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		return player.getItemInHand(otherHand).isEmpty();
	}

	/** 末影共鸣：16 格内存在末影人时 10% 概率挑一只激怒（锁定玩家 + 凝视音效）。 */
	private static void triggerEnderResonance(ServerLevel level, Player player) {
		if (!QuirkyConfigHolder.get().enderPouchEnderResonance) {
			return;
		}
		List<EnderMan> endermen = level.getEntitiesOfClass(
			EnderMan.class,
			player.getBoundingBox().inflate(RESONANCE_RANGE),
			EntitySelector.ENTITY_STILL_ALIVE
		);
		if (endermen.isEmpty()) {
			return;
		}
		if (level.getRandom().nextFloat() >= RESONANCE_CHANCE) {
			return;
		}
		EnderMan chosen = endermen.get(level.getRandom().nextInt(endermen.size()));
		chosen.setTarget(player);
		chosen.playStareSound();
	}
}
