package dev.quirky.client.ladder_snap;

import net.minecraft.world.phys.Vec2;

/**
 * 爬梯吸附（spec 5.9）修正向量计算（纯逻辑，可单测）：
 * 玩家在梯子/藤蔓上爬行且未按左右键时，身体朝梯子所在方块中心线平滑吸附。
 */
public final class LadderSnapHelper {
	/** 偏移低于该值（格）视为已居中，不再修正。 */
	private static final double CENTERED_THRESHOLD = 0.01;

	private LadderSnapHelper() {
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
		return new Vec2(
			component(centerX - playerX, strength),
			component(centerZ - playerZ, strength)
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
