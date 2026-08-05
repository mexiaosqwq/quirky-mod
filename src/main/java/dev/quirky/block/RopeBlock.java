package dev.quirky.block;

import dev.quirky.ModBlocks;
import dev.quirky.rope.RopeSupportLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 绳段方块：无碰撞（可穿过）、可含水、光照透明、攀爬（加入 #minecraft:climbable tag）。
 * 支撑规则由 {@link RopeSupportLogic} 判定，失去支撑时连锁掉落。
 * {@code entityInside} 提供坠落防摔：非潜行下落实体重置摔落距离并把垂直速度钳到 -0.15（缓滑手感）。
 * 手持灯笼右键绳段时把该段替换为挂灯绳段 {@link ModBlocks#ROPE_LANTERN}（消耗 1 个灯笼）。
 */
public class RopeBlock extends Block implements SimpleWaterloggedBlock {
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	/** 本段上方是否无绳段（自己是悬挂顶端）：true → 顶部绳结变体；false → 连续绳身变体（与上方绳段连接）。 */
	public static final BooleanProperty TOP = BooleanProperty.create("top");
	/** 贴墙段（泰拉瑞亚式）：true = 绳贴在方块侧面，{@link #FACING} 为墙所在方向；false = 悬挂段。 */
	public static final BooleanProperty WALL = BooleanProperty.create("wall");
	/** 贴墙段的墙方向（WALL=true 时有效）。26.2 无 DirectionProperty，统一为 EnumProperty。 */
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	/** 2px 细柱轮廓：无碰撞但可被玩家射线选中（交互/延伸）。 */
	private static final VoxelShape OUTLINE = Block.box(7.0, 0.0, 7.0, 9.0, 16.0, 9.0);
	/** 贴墙段轮廓：柱靠墙侧（FACING 对应方向），与模型一致。 */
	private static final VoxelShape OUTLINE_NORTH = Block.box(7.0, 0.0, 1.0, 9.0, 16.0, 3.0);
	private static final VoxelShape OUTLINE_EAST = Block.box(13.0, 0.0, 7.0, 15.0, 16.0, 9.0);
	private static final VoxelShape OUTLINE_SOUTH = Block.box(7.0, 0.0, 13.0, 9.0, 16.0, 15.0);
	private static final VoxelShape OUTLINE_WEST = Block.box(1.0, 0.0, 7.0, 3.0, 16.0, 9.0);

	public RopeBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any()
			.setValue(WATERLOGGED, false)
			.setValue(TOP, true)
			.setValue(WALL, false)
			.setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(WATERLOGGED, TOP, WALL, FACING);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (state.getValue(WALL)) {
			return switch (state.getValue(FACING)) {
				case EAST -> OUTLINE_EAST;
				case SOUTH -> OUTLINE_SOUTH;
				case WEST -> OUTLINE_WEST;
				default -> OUTLINE_NORTH;
			};
		}
		return OUTLINE;
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	@Override
	protected BlockState updateShape(
		BlockState state,
		LevelReader level,
		ScheduledTickAccess ticks,
		BlockPos pos,
		Direction directionToNeighbour,
		BlockPos neighbourPos,
		BlockState neighbourState,
		RandomSource random
	) {
		if (state.getValue(WATERLOGGED)) {
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}
		return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
	}

	/**
	 * 坠落防摔：垂直下落且非潜行的实体贴住绳段——重置摔落距离、垂直速度钳到 -0.15（缓滑下落）。
	 * 潜行 = 松手直接穿过。
	 */
	@Override
	protected void entityInside(
		BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise
	) {
		if (entity instanceof LivingEntity living
			&& living.getDeltaMovement().y() < 0.0
			&& !living.isShiftKeyDown()
			&& !living.isFallFlying()) {
			living.resetFallDistance();
			Vec3 movement = living.getDeltaMovement();
			living.setDeltaMovement(movement.x, -0.15, movement.z);
		}
	}

	/**
	 * 手持灯笼右键绳段 → 替换为挂灯绳段（保留含水状态），消耗 1 个灯笼。
	 * 其余物品（绳等）走默认 {@code TRY_WITH_EMPTY_HAND}，由物品的 useOn 继续处理。
	 */
	@Override
	protected InteractionResult useItemOn(
		ItemStack itemStack,
		BlockState state,
		Level level,
		BlockPos pos,
		Player player,
		InteractionHand hand,
		BlockHitResult hitResult
	) {
		if (state.is(ModBlocks.ROPE) && itemStack.is(Items.LANTERN) && !state.getValue(WALL)) {
			if (level.isClientSide()) {
				return InteractionResult.SUCCESS;
			}
			boolean waterlogged = state.getValue(WATERLOGGED);
			boolean top = !RopeSupportLogic.isRope(level.getBlockState(pos.above()));
			level.setBlock(pos, ModBlocks.ROPE_LANTERN.defaultBlockState().setValue(WATERLOGGED, waterlogged).setValue(TOP, top), 3);
			if (!player.hasInfiniteMaterials()) {
				itemStack.consume(1, player);
			}
			level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.6F, 1.0F);
			return InteractionResult.SUCCESS;
		}
		return super.useItemOn(itemStack, state, level, pos, player, hand, hitResult);
	}

	/** 放置后双保险：下一 tick 校验支撑（配合 neighborChanged 立即检查）。 */
	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		level.scheduleTick(pos, this, 1);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		RopeSupportLogic.checkSupport(level, pos);
	}

	/** 邻居变化（支撑被破坏/活塞推走）→ 立即校验支撑，连锁掉落；并刷新自身 top 状态。 */
	@Override
	protected void neighborChanged(
		BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston
	) {
		if (!level.isClientSide()) {
			RopeSupportLogic.checkSupport(level, pos);
			refreshTop(level, pos);
		}
	}

	/**
	 * 刷新 top 状态：上方是否绳段决定本段渲染成顶部绳结变体还是连续绳身变体。
	 * 贴墙段同样遵循（顶段带结，与悬挂段视觉一致）；仅在状态变化时 setBlock（flags=2，不触发 tick），
	 * 变化会经邻居通知收敛，无递归风险。
	 */
	private static void refreshTop(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!RopeSupportLogic.isRope(state)) {
			return;
		}
		boolean top = !RopeSupportLogic.isRope(level.getBlockState(pos.above()));
		if (state.getValue(TOP) != top) {
			level.setBlock(pos, state.setValue(TOP, top), 2);
		}
	}

	/** 破坏掉落：普通绳段掉落绳（挂灯绳段掉绳+灯笼）。 */
	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		return RopeSupportLogic.dropStacks(state);
	}
}
