package dev.quirky.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 金/铁按钮：按下保持时长（holdTicks，红石刻）在构造时指定，
 * 其余行为与 {@link ButtonBlock} 原版一致（六面放置、红石输出、活塞不可推动、箭不可触发）。
 */
public class MetalButtonBlock extends ButtonBlock {
	/**
	 * 金/铁按钮共用的金属方块集：金属音效（SoundType.METAL + 金属点击声）、
	 * canButtonBeActivatedByArrows=false（箭不可触发）。
	 */
	public static final BlockSetType METAL = new BlockSetType(
		"quirky_metal",
		false,
		false,
		false,
		BlockSetType.PressurePlateSensitivity.EVERYTHING,
		SoundType.METAL,
		SoundEvents.IRON_DOOR_CLOSE,
		SoundEvents.IRON_DOOR_OPEN,
		SoundEvents.IRON_TRAPDOOR_CLOSE,
		SoundEvents.IRON_TRAPDOOR_OPEN,
		SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF,
		SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
		SoundEvents.STONE_BUTTON_CLICK_OFF,
		SoundEvents.STONE_BUTTON_CLICK_ON
	);

	private final int holdTicks;

	public MetalButtonBlock(final BlockSetType type, final int holdTicks, final BlockBehaviour.Properties properties) {
		super(type, holdTicks, properties);
		// 父类 ButtonBlock 的 ticksToStayPressed 是 private，镜像存储一份供测试可读性（review S6）
		this.holdTicks = holdTicks;
	}

	/**
	 * 读取按下保持时长（红石刻），供测试验证金=2 / 铁=100。
	 */
	public static int holdTicksOf(final MetalButtonBlock button) {
		return button.holdTicks;
	}

	@Override
	protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hitResult) {
		return super.useWithoutItem(state, level, pos, player, hitResult);
	}
}
