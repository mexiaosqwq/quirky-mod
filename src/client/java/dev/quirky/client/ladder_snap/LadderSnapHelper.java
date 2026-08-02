package dev.quirky.client.ladder_snap;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;

/**
 * 爬梯吸附（spec 5.9）修正向量计算（纯逻辑，可单测）：
 * 玩家在梯子/藤蔓上爬行且未按左右键时，身体朝梯子所在方块中心线平滑吸附。
 */
public final class LadderSnapHelper {
	/** 偏移低于该值（格）视为已居中，不再修正。 */
	private static final double CENTERED_THRESHOLD = 0.01;
	/** 自动攀爬：抬头/低头触发阈值（度），平视区间内悬停 */
	public static final float CLIMB_PITCH_UP = -30.0F;
	public static final float CLIMB_PITCH_DOWN = 30.0F;
	/** 自动攀爬垂直速度（对齐原版爬梯最大速度 0.15） */
	public static final double CLIMB_SPEED = 0.15;

	private LadderSnapHelper() {
	}

	/**
	 * 自动攀爬垂直速度（基岩版式）：未按手动键时，抬头（pitch &lt; -30°）上升、
	 * 低头（pitch &gt; 30°）下降、平视悬停；手动按键时返回 NaN（不干预）。
	 */
	public static double climbVelocity(float pitch, boolean manualInput) {
		if (manualInput) {
			return Double.NaN;
		}
		if (pitch < CLIMB_PITCH_UP) {
			return CLIMB_SPEED;
		}
		if (pitch > CLIMB_PITCH_DOWN) {
			return -CLIMB_SPEED;
		}
		return 0.0;
	}

	/**
	 * 是否为目标吸附方块（spec：梯子/藤蔓；脚手架虽在 {@code #minecraft:climbable} 中，
	 * 但玩家在其上应自由走动，不做吸附）。
	 */
	public static boolean isClimbableTarget(BlockState state) {
		return state.is(BlockTags.CLIMBABLE) && !state.is(Blocks.SCAFFOLDING);
	}

	/**
	 * 计算指向 (centerX, centerZ) 的修正速度分量（x = 横向，y = 纵向）。
	 *
	 * @param playerX 玩家当前 x
	 * @param playerZ 玩家当前 z
	 * @param centerX 梯子方块中心 x
	 * @param centerZ 梯子方块中心 z
	 * @param strength 修正强度（每 tick 偏移的修正比例）
	 * @return 修正向量；单轴偏移 &lt; 0.01 时该分量为 0，两轴都居中时为 (0, 0)
	 */
	public static Vec2 correction(double playerX, double playerZ, double centerX, double centerZ, double strength) {
		// config 输入无边界校验（26.2 cloth-config 无 float 边界注解），运行时 clamp 到 0.1~1.0
		double clamped = Math.max(0.1, Math.min(1.0, strength));
		return new Vec2(
			component(centerX - playerX, clamped),
			component(centerZ - playerZ, clamped)
		);
	}

	/**
	 * 单轴修正：偏移 × strength，clamp 到 |偏移| 保证不越过中心（防过冲）；
	 * |偏移| &lt; 0.01 视为已居中，返回 0。
	 */
	private static float component(double offset, double strength) {
		if (Math.abs(offset) < CENTERED_THRESHOLD) {
			return 0.0F;
		}
		double correction = offset * strength;
		double bound = Math.abs(offset);
		return (float) Math.max(-bound, Math.min(bound, correction));
	}
}
