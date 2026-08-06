package dev.quirky.copper_golem_ai;

/** 心跳触发判定（纯逻辑，可单测）。 */
public final class CopperGolemHeartbeat {
	/** 心跳时附近玩家判定范围（格）。 */
	public static final int HEARTBEAT_PLAYER_RANGE = 32;
	/** 主动搭话限流（tick）。 */
	public static final long CHATTER_COOLDOWN_TICKS = 6000; // 5 分钟

	private CopperGolemHeartbeat() {
	}

	/** 是否触发心跳：间隔到 + 附近有玩家 + 不忙（无进行中任务/对话）。 */
	public static boolean shouldHeartbeat(int intervalSeconds, long nowTick, long lastHeartbeatTick,
		boolean playerNearby, boolean busy) {
		if (intervalSeconds <= 0) {
			return false;
		}
		if (busy || !playerNearby) {
			return false;
		}
		return nowTick - lastHeartbeatTick >= intervalSeconds * 20L;
	}
}
