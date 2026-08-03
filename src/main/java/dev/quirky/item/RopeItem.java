package dev.quirky.item;

import dev.quirky.block.RopeBlock;
import dev.quirky.config.QuirkyConfig;
import dev.quirky.config.QuirkyConfigHolder;
import dev.quirky.rope.RopeSupportLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

/**
 * 绳捆放置物品（绳 / 挂灯绳共用一个类）：
 * <ul>
 *   <li>对固体方块底面右键 → 在其下方放一段绳（挂点 = 该方块）。</li>
 *   <li>对已有绳段右键 → 在绳柱正下方延伸一段（下方为空气/水时）。</li>
 *   <li>潜行+对绳段右键 → 批量铺设到最近的落地处或耗尽（上限 {@link QuirkyConfig#ropeMaxExtendPerUse} 段/次）。</li>
 * </ul>
 * 放置音 WOOL_PLACE（音量 0.6，音高随延伸次数轻微递降）+ 线状纤维粒子；创造不消耗。
 */
public class RopeItem extends BlockItem {
	public RopeItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!QuirkyConfigHolder.get().ropeEnabled) {
			return InteractionResult.FAIL;
		}
		Level level = context.getLevel();
		Player player = context.getPlayer();
		ItemStack stack = context.getItemInHand();
		BlockPos clicked = context.getClickedPos();
		BlockState clickedState = level.getBlockState(clicked);

		if (RopeSupportLogic.isRope(clickedState)) {
			BlockPos bottom = bottomOfColumn(level, clicked);
			BlockPos below = bottom.below();
			if (player != null && player.isShiftKeyDown()) {
				return this.batchExtend(context, bottom, stack);
			}
			if (below.getY() < level.getMinY() || !RopeSupportLogic.isPlaceable(level.getBlockState(below))) {
				return InteractionResult.FAIL;
			}
			if (level.isClientSide()) {
				return InteractionResult.SUCCESS;
			}
			this.placeSegment(level, below, stack, player, 1.0F);
			return InteractionResult.SUCCESS;
		}

		// 对非绳方块：沿点击面放置（底面 → 挂在该方块下；顶面 → 挂在方块上；侧面不支持）
		Direction face = context.getClickedFace();
		if (face != Direction.DOWN && face != Direction.UP) {
			return InteractionResult.FAIL;
		}
		BlockPos target = clicked.relative(face);
		if (target.getY() < level.getMinY() || target.getY() > level.getMaxY()) {
			return InteractionResult.FAIL;
		}
		if (!RopeSupportLogic.isPlaceable(level.getBlockState(target)) || !RopeSupportLogic.isSupportedAt(level, target)) {
			return InteractionResult.FAIL;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		this.placeSegment(level, target, stack, player, 1.0F);
		return InteractionResult.SUCCESS;
	}

	/** 潜行批量铺设：从绳柱底端向下铺，撞非空气非水/世界底部/手持耗尽/上限即停。 */
	private InteractionResult batchExtend(UseOnContext context, BlockPos bottom, ItemStack stack) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		QuirkyConfig config = QuirkyConfigHolder.get();

		// 收集候选位置状态（到世界底部为止），交给纯函数决定本次铺设段数
		List<BlockState> candidates = new ArrayList<>();
		BlockPos cursor = bottom.below();
		while (cursor.getY() >= level.getMinY() && candidates.size() < config.ropeMaxExtendPerUse) {
			BlockState state = level.getBlockState(cursor);
			if (!RopeSupportLogic.isPlaceable(state)) {
				break;
			}
			candidates.add(state);
			cursor = cursor.below();
		}
		int count = RopeSupportLogic.extendStop(
			candidates,
			config.ropeMaxExtendPerUse,
			player != null && player.hasInfiniteMaterials() ? Integer.MAX_VALUE : stack.getCount()
		);
		if (count == 0) {
			return InteractionResult.FAIL;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		// 立即铺完；音高随延伸次数递降形成"唰——"的下滑感（1 tick 间隔简化为连续放置）
		for (int i = 0; i < count; i++) {
			float pitch = 1.0F - i * 0.03F;
			this.placeSegment(level, bottom.below().offset(0, -i, 0), stack, player, pitch);
		}
		return InteractionResult.SUCCESS;
	}

	/** 放置一个绳段（使用手持物品对应的方块），含水状态随目标流体；播放放置音 + 纤维粒子。 */
	private void placeSegment(Level level, BlockPos pos, ItemStack stack, Player player, float pitch) {
		BlockState target = level.getBlockState(pos);
		boolean waterlogged = target.getFluidState().is(Fluids.WATER);
		BlockState ropeState = this.getBlock().defaultBlockState()
			.setValue(RopeBlock.WATERLOGGED, waterlogged);
		level.setBlock(pos, ropeState, 3);
		level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.6F, pitch);
		if (level instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(
				new ItemParticleOption(ParticleTypes.ITEM, Items.STRING),
				pos.getX() + 0.5,
				pos.getY() + 0.5,
				pos.getZ() + 0.5,
				2,
				0.15,
				0.15,
				0.15,
				0.05
			);
		}
		if (player != null && !player.hasInfiniteMaterials()) {
			stack.consume(1, player);
		}
	}

	/** 找到以 pos 为起点的绳柱最底端（向下连续绳段）。 */
	private static BlockPos bottomOfColumn(Level level, BlockPos pos) {
		BlockPos cursor = pos;
		while (RopeSupportLogic.isRope(level.getBlockState(cursor.below())) && cursor.getY() > level.getMinY()) {
			cursor = cursor.below();
		}
		return cursor;
	}
}
