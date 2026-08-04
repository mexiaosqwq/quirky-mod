package dev.quirky.fishbait;

import net.minecraft.world.phys.Vec3;

/**
 * 鱼饵球的纯逻辑：诱鱼区包含判定 + 雨天时长。不依赖实体/世界，可单测。
 */
public final class BaitZoneLogic {
	private BaitZoneLogic() {
	}

	/** 雨天加成系数 5/3（90s → 150s）。 */
	public static final double RAIN_MULTIPLIER = 5.0 / 3.0;

	/**
	 * 2D 区域包含判定：浮漂与区域中心水平距离 ≤ radius（|dx| ≤ r 且 |dz| ≤ r）。
	 * 与诱鱼区实体的 AABB 语义一致（实体 x/z 盒子即以其位置为中心）。
	 */
	public static boolean isInside(Vec3 bobber, Vec3 zone, double radius) {
		return Math.abs(bobber.x - zone.x) <= radius && Math.abs(bobber.z - zone.z) <= radius;
	}

	/**
	 * 诱鱼阶段每 tick 的额外递减（mixin 在 tick RETURN 对区内浮漂调用）：在原版 -1 之后
	 * 再减 3（clamp ≥1）→ 合计 -4/tick（约 ×4 速，300-600 tick 起始 → 4-8 秒，2026-08-04 用户调激进）。
	 * 保留 1 是关键：原版转换（timeUntilLured<=0 → timeUntilHooked）
	 * 只在其自身递减后触发；若这里 clamp 到 0，下一 tick 原版看到 0 会走 else 分支
	 * 重掷 100-600，本次诱鱼进度全部丢失 → 加速失效（历史 bug，见 BaitZoneLogicTest）。
	 */
	public static int extraLureDecrement(int timeUntilLured) {
		return Math.max(1, timeUntilLured - 3);
	}

	/** 诱鱼区持续 tick 数：雨天且开启加成时 ×5/3，否则为基准秒数 ×20。
	 * 用整数运算（×100/3）避免浮点误差：90s 雨天 → 3000 tick（150s）。
	 */
	public static int durationTicks(int baseSeconds, boolean raining, boolean rainBonusEnabled) {
		return raining && rainBonusEnabled ? baseSeconds * 100 / 3 : baseSeconds * 20;
	}
}
