package dev.quirky.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * 黑曜石压力板：只有玩家踩上才输出满信号（15）；
 * 动物/掉落物等其他实体不触发。形状与信号逻辑复用 {@link PressurePlateBlock}。
 */
public class ObsidianPressurePlateBlock extends PressurePlateBlock {
	public ObsidianPressurePlateBlock(final BlockSetType type, final BlockBehaviour.Properties properties) {
		super(type, properties);
	}

	@Override
	protected int getSignalStrength(final Level level, final BlockPos pos) {
		// 实体列表收集限定为 Player.class：牛、猪、掉落物等均不触发
		return getEntityCount(level, TOUCH_AABB.move(pos), Player.class) > 0 ? 15 : 0;
	}
}
