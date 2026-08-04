package dev.quirky.rope;

import dev.quirky.ModBlocks;
import dev.quirky.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * 绳捆支撑/连锁掉落的纯函数逻辑（单测覆盖），以及少量生产环境辅助方法。
 *
 * 支撑规则：绳段由正上方的方块支撑——完整固体底面 / 栅栏·墙·地狱栅栏顶部 / 另一段绳。
 * 失去支撑时自下而上连锁掉落为物品。
 */
public final class RopeSupportLogic {
	private RopeSupportLogic() {
	}

	// ==== 纯函数 ====

	/**
	 * 支撑判定：上方是完整固体 / 栅栏墙顶 / 另一段绳 之一即可支撑。
	 *
	 * @param aboveIsSolid     上方方块 isSolid（完整固体底面）
	 * @param aboveIsFenceWall 上方方块是栅栏/墙（#minecraft:fences / #minecraft:walls，单测环境无 tag 数据，由调用方传入）
	 * @param aboveIsRope      上方方块是另一段绳/挂灯绳
	 */
	public static boolean isSupported(boolean aboveIsSolid, boolean aboveIsFenceWall, boolean aboveIsRope) {
		return aboveIsSolid || aboveIsFenceWall || aboveIsRope;
	}

	/**
	 * 连锁掉落段计算：给定一段连续绳柱快照（自顶向下排列），
	 * 从第一个不受支撑的段开始，其自身及其下方所有段掉落；
	 * 返回列表为自下而上顺序（下方段先掉）。
	 *
	 * @param columnTopToBottom 连续绳柱的位置列表，最高段在前
	 * @param supportCheck      每个位置当前是否受支撑（正上方判定）
	 */
	public static List<BlockPos> fallingSegments(List<BlockPos> columnTopToBottom, Predicate<BlockPos> supportCheck) {
		List<BlockPos> falling = new ArrayList<>();
		boolean cascade = false;
		for (BlockPos pos : columnTopToBottom) {
			if (!cascade && !supportCheck.test(pos)) {
				cascade = true;
			}
			if (cascade) {
				falling.add(pos);
			}
		}
		Collections.reverse(falling);
		return falling;
	}

	/**
	 * 批量铺设停止：遍历候选位置（自顶向下），撞到任何非空气非水方块即停，
	 * 段数不超过 maxSegments 与 available。
	 */
	public static int extendStop(List<BlockState> segmentStates, int maxSegments, int available) {
		int count = 0;
		for (BlockState state : segmentStates) {
			if (count >= maxSegments || count >= available) {
				break;
			}
			if (!isPlaceable(state)) {
				break;
			}
			count++;
		}
		return count;
	}

	/** 绳段可放置：空气或水（绳可含水）。 */
	public static boolean isPlaceable(BlockState state) {
		return state.isAir() || state.is(Blocks.WATER);
	}

	// ==== 生产环境辅助 ====

	/** 判断某位置是否被上方方块支撑（组装 isSupported 的三个输入）。 */
	public static boolean isSupportedAt(Level level, BlockPos pos) {
		BlockState above = level.getBlockState(pos.above());
		return isSupported(
			above.isSolid(),
			above.is(BlockTags.FENCES) || above.is(BlockTags.WALLS),
			above.is(ModBlocks.ROPE) || above.is(ModBlocks.ROPE_LANTERN)
		);
	}

	/** 判断方块是否为绳段（绳或挂灯绳）。 */
	public static boolean isRope(BlockState state) {
		return state.is(ModBlocks.ROPE) || state.is(ModBlocks.ROPE_LANTERN);
	}

	/** 从 pos 出发向上找到连续绳柱的柱顶，再向下收集整列（自顶向下）。 */
	public static List<BlockPos> columnTopToBottom(Level level, BlockPos pos) {
		BlockPos top = pos;
		while (isRope(level.getBlockState(top.above())) && top.getY() < level.getMaxY()) {
			top = top.above();
		}
		List<BlockPos> column = new ArrayList<>();
		BlockPos cursor = top;
		while (isRope(level.getBlockState(cursor))) {
			column.add(cursor);
			cursor = cursor.below();
		}
		return column;
	}

	/** 某个绳段的破坏掉落物：挂灯绳段掉落绳+灯笼，普通绳段掉落绳。 */
	public static List<ItemStack> dropStacks(BlockState state) {
		if (state.is(ModBlocks.ROPE_LANTERN)) {
			return List.of(new ItemStack(ModItems.ROPE), new ItemStack(Items.LANTERN));
		}
		return List.of(new ItemStack(ModItems.ROPE));
	}

	/**
	 * 支撑校验 + 连锁掉落（服务端）：给定位置若是绳段且失去支撑，则整段自下而上掉落为物品。
	 * 每段播放 WOOL_BREAK（0.4）并生成少量线状粒子。
	 * 方块移除会触发上方绳段 neighborChanged 递归校验，循环内用 {@link #isRope} 防御去重。
	 */
	public static void checkSupport(Level level, BlockPos pos) {
		if (level.isClientSide() || !isRope(level.getBlockState(pos))) {
			return;
		}
		List<BlockPos> falling = fallingSegments(columnTopToBottom(level, pos), p -> isSupportedAt(level, p));
		for (BlockPos dropPos : falling) {
			BlockState state = level.getBlockState(dropPos);
			if (!isRope(state)) {
				continue;
			}
			level.setBlock(dropPos, Blocks.AIR.defaultBlockState(), 3);
			for (ItemStack stack : dropStacks(state)) {
				// 不用 Block.popResource（受 BLOCK_DROPS 规则门控，规则关时吞物品）；
				// 绳掉落不是方块破坏，直接生成 ItemEntity 绕开规则
				ItemEntity item = new ItemEntity(level,
					dropPos.getX() + 0.5, dropPos.getY() + 0.5, dropPos.getZ() + 0.5, stack);
				item.setPickUpDelay(20);
				level.addFreshEntity(item);
			}
			level.playSound(null, dropPos, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 0.4F, 1.0F);
			if (level instanceof ServerLevel serverLevel) {
				serverLevel.sendParticles(
					new ItemParticleOption(ParticleTypes.ITEM, Items.STRING),
					dropPos.getX() + 0.5,
					dropPos.getY() + 0.5,
					dropPos.getZ() + 0.5,
					2,
					0.15,
					0.15,
					0.15,
					0.05
				);
			}
		}
	}
}
