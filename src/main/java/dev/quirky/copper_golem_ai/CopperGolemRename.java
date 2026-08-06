package dev.quirky.copper_golem_ai;

import java.util.UUID;

/** 待命名状态机（纯逻辑，可单测）：右键进入 → 发起者下一条消息 = 名字；空白取消；超时失效。 */
public final class CopperGolemRename {
	/** 待命名有效期（tick）。 */
	public static final long RENAME_TIMEOUT_TICKS = 600; // 30 秒
	/** 名字最大长度。 */
	public static final int NAME_MAX_LENGTH = 50;

	public record RenameState(UUID ownerId, long expireTick) {
		/** 消息是否来自发起者。 */
		public static boolean isOwner(RenameState state, UUID candidate) {
			return state.ownerId().equals(candidate);
		}

		/** 是否已过期（30 秒）。 */
		public static boolean isExpired(RenameState state, long nowTick) {
			return nowTick > state.expireTick();
		}
	}

	private CopperGolemRename() {
	}

	/** 名字截断（≤50 字符）。 */
	public static String truncate(String name) {
		return name.length() > NAME_MAX_LENGTH ? name.substring(0, NAME_MAX_LENGTH) : name;
	}
}
