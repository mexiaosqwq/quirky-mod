package dev.quirky.block;

import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 挂灯绳段：普通绳段 + 底部灯笼，亮度 15（lightLevel 在属性中配置）。
 * 行为（无碰撞/可含水/攀爬/防摔/支撑连锁）全部继承自 {@link RopeBlock}；
 * 破坏时掉落绳+灯笼（见 {@link RopeBlock#getDrops}，经 RopeSupportLogic.dropStacks）。
 * state 定义（WATERLOGGED）由 {@link RopeBlock} 构造器统一注册，方块替换时状态可直接搬运。
 */
public class RopeLanternBlock extends RopeBlock {
	public RopeLanternBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}
}
