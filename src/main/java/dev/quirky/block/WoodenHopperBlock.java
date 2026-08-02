package dev.quirky.block;

import dev.quirky.ModBlockEntityTypes;
import dev.quirky.block.be.WoodenHopperBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * 木漏斗方块：形状/放置/红石状态机沿用原版 {@link HopperBlock}，
 * 但挂接 {@link WoodenHopperBlockEntity}（4 倍慢传输、红石锁不住）。
 * 继承自原版 {@code HopperBlock}，因此天然获得原版的碰撞箱、方向放置、
 * 比较器输出与相邻方块更新行为。
 * {@code codec()} 沿用父类（{@code MapCodec} 不变型，子类无法覆写，与 Quark 木漏斗一致）。
 */
public class WoodenHopperBlock extends HopperBlock {
	public WoodenHopperBlock(final BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(final BlockPos worldPosition, final BlockState blockState) {
		return new WoodenHopperBlockEntity(worldPosition, blockState);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(final Level level, final BlockState blockState, final BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntityTypes.WOODEN_HOPPER, WoodenHopperBlockEntity::pushItemsTick);
	}

	@Override
	protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hitResult) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof WoodenHopperBlockEntity hopper) {
			player.openMenu(hopper);
			player.awardStat(Stats.INSPECT_HOPPER);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void entityInside(
		final BlockState state, final Level level, final BlockPos pos, final Entity entity, final InsideBlockEffectApplier effectApplier, final boolean isPrecise
	) {
		if (level.getBlockEntity(pos) instanceof WoodenHopperBlockEntity hopper) {
			WoodenHopperBlockEntity.entityInside(level, pos, state, entity, hopper);
		}
	}
}
