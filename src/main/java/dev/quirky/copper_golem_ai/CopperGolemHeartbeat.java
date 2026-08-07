package dev.quirky.copper_golem_ai;

import net.minecraft.util.RandomSource;

/** 心跳触发判定（纯逻辑，可单测）。 */
public final class CopperGolemHeartbeat {
	/** 心跳时附近玩家判定范围（格）。 */
	public static final int HEARTBEAT_PLAYER_RANGE = 32;
	/** 主动搭话限流（tick）。 */
	public static final long CHATTER_COOLDOWN_TICKS = 6000; // 5 分钟

	private CopperGolemHeartbeat() {
	}

	/** 是否触发心跳：到点 + 附近有玩家 + 不忙（无进行中任务/对话）。nextHeartbeatTick 为下次触发 tick（见 Service）。 */
	public static boolean shouldHeartbeat(int intervalSeconds, long nowTick, long nextHeartbeatTick,
		boolean playerNearby, boolean busy) {
		if (intervalSeconds <= 0) {
			return false;
		}
		if (busy || !playerNearby) {
			return false;
		}
		return nowTick >= nextHeartbeatTick;
	}

	/** 下次心跳 tick：基准间隔 ±25% 随机抖动（0.75x~1.25x）——多傀儡相位错开，防同一时刻齐射（同时行动/同时请求）。 */
	public static long nextHeartbeatTick(RandomSource random, int intervalSeconds, long nowTick) {
		long base = intervalSeconds * 20L;
		long jitter = random.nextInt((int) (base / 2)); // 0 ~ 0.5x
		return nowTick + base * 3 / 4 + jitter;
	}
}
