package dev.quirky.copper_golem_ai;

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** AI 行为流水（纯逻辑，可单测）：最近成功动作 + 最后失败——心跳注入用，AI 知道自己刚做过什么，不重复劳动。 */
public final class CopperGolemActionLog {
	public static final int MAX_ACTIONS = 10;
	private static final Map<UUID, ArrayDeque<String>> ACTIONS = new ConcurrentHashMap<>(); // 头=最新
	private static final Map<UUID, String> LAST_FAILURE = new ConcurrentHashMap<>();

	private CopperGolemActionLog() {
	}

	public static void recordAction(UUID golemId, String entry) {
		ACTIONS.computeIfAbsent(golemId, k -> new ArrayDeque<>()).addFirst(entry);
		ArrayDeque<String> q = ACTIONS.get(golemId);
		while (q.size() > MAX_ACTIONS) {
			q.removeLast();
		}
	}

	public static void recordFailure(UUID golemId, String entry) {
		LAST_FAILURE.put(golemId, entry);
	}

	/** 注入文本：最近 3 条动作（旧→新）+ 最后失败；无记录返回 null。 */
	public static @Nullable String summary(UUID golemId) {
		ArrayDeque<String> q = ACTIONS.get(golemId);
		StringBuilder sb = new StringBuilder();
		if (q != null) {
			List<String> recent = new ArrayList<>(q);
			Collections.reverse(recent); // 旧→新
			int take = Math.min(3, recent.size());
			for (int i = recent.size() - take; i < recent.size(); i++) {
				if (sb.length() > 0) {
					sb.append("；");
				}
				sb.append(recent.get(i));
			}
		}
		String fail = LAST_FAILURE.get(golemId);
		if (fail != null) {
			if (sb.length() > 0) {
				sb.append("；");
			}
			sb.append("最后失败：").append(fail);
		}
		return sb.length() == 0 ? null : sb.toString();
	}

	public static void clear(UUID golemId) {
		ACTIONS.remove(golemId);
		LAST_FAILURE.remove(golemId);
	}

	public static void resetForTest() {
		ACTIONS.clear();
		LAST_FAILURE.clear();
	}
}
