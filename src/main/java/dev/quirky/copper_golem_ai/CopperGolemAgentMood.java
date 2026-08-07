package dev.quirky.copper_golem_ai;

import java.util.List;

/** 心情系统（纯逻辑，可单测）：玩家消息词表匹配 → 心情分数 → 状态 → system prompt 语气。 */
public final class CopperGolemAgentMood {
	public enum Mood { CALM, HAPPY, UPSET, ANGRY }

	private static final List<String> POSITIVE = List.of("谢谢", "真棒", "乖", "厉害", "感谢", "棒");
	private static final List<String> NEGATIVE = List.of("笨", "蠢", "废物", "没用", "傻", "讨厌");

	private CopperGolemAgentMood() {
	}

	/** 词表子串匹配：正向 +1 / 负向 -1 / 无命中 0。 */
	public static int processWord(String playerText) {
		if (playerText == null || playerText.isBlank()) {
			return 0;
		}
		for (String w : NEGATIVE) {
			if (playerText.contains(w)) {
				return -1;
			}
		}
		for (String w : POSITIVE) {
			if (playerText.contains(w)) {
				return 1;
			}
		}
		return 0;
	}

	/** 分数 → 心情：≥+2 开心 / 0~1 平静 / -1~-2 委屈 / ≤-3 生气。 */
	public static Mood moodFor(int score) {
		if (score >= 2) {
			return Mood.HAPPY;
		}
		if (score >= 0) {
			return Mood.CALM;
		}
		if (score >= -2) {
			return Mood.UPSET;
		}
		return Mood.ANGRY;
	}

	/** 每心跳向 0 衰减一步。 */
	public static int decay(int score) {
		return Integer.compare(score, 0) == 0 ? 0 : score - Integer.signum(score);
	}

	/** 心情 → system prompt 语气描述。 */
	public static String toPrompt(Mood mood) {
		return switch (mood) {
			case HAPPY -> "你现在心情很好，回复语气轻快，爱开玩笑，愿意主动帮忙";
			case CALM -> "你现在心情平静，回复自然简短";
			case UPSET -> "你现在有点委屈，回复语气低落，简短少话";
			case ANGRY -> "你现在有点生气，回复生硬少话；但你是好傀儡，该干的活还是照干";
		};
	}
}
