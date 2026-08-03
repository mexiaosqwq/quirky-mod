package dev.quirky.whistle;

import java.util.function.Predicate;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;

/**
 * 宠物口哨核心纯逻辑：过滤谓词 / 半径判定 / 幻翼嘲讽数量选择。
 * 与 Minecraft 世界解耦（维度过滤天然由 level 查询承担），全部可单测。
 */
public final class WhistleLogic {

	private WhistleLogic() {
	}

	/** 只响应玩家自己驯服的宠物（owner UUID 匹配）。 */
	public static Predicate<TamableAnimal> ownedBy(Player owner) {
		return pet -> pet.isOwnedBy(owner);
	}

	/** 平方距离半径判定（含边界）。 */
	public static boolean withinRadius(double dx, double dy, double dz, double radius) {
		return dx * dx + dy * dy + dz * dz <= radius * radius;
	}

	/**
	 * 本次嘲讽幻翼数量：1 → min(maxCount, available) 之间随机；
	 * 无幻翼或上限为 0 时返回 0（静默跳过）。
	 */
	public static int selectPhantoms(int available, int maxCount, RandomSource random) {
		if (available <= 0 || maxCount <= 0) {
			return 0;
		}
		int max = Math.min(maxCount, available);
		return Mth.nextInt(random, 1, max);
	}
}
