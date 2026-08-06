package dev.quirky.copper_golem_ai;

import dev.quirky.config.QuirkyConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 单只铜傀儡的对话记忆：历史、本地纠错指令、压缩触发判定。
 * 纯逻辑（不依赖 MC 服务端），线程约束：全部在主线程（tick）调用。
 */
public final class CopperGolemAiHistory {

	private CopperGolemAiHistory() {
	}

	public enum HandleResult {
		NORMAL,       // 正常消息，进历史待处理
		FORGET_ALL,   // 忘掉：清空全部历史+摘要
		FORGET_LAST,  // 忘掉上一条：删最近一条玩家消息
		COMPRESSING   // 压缩进行中：消息入队
	}

	/** 单只傀儡会话状态（每傀儡一个，存于 Service 的 Map<UUID, GolemSession>）。 */
	public static final class GolemSession {
		private final Deque<String> messages = new ArrayDeque<>();
		private final Deque<String> pending = new ArrayDeque<>();
		private boolean compressing = false;

		/** 玩家消息入口；压缩中返回 COMPRESSING（入队），纠错指令本地处理。 */
		public HandleResult addPlayerMessage(String text) {
			if (compressing) {
				pending.addLast(text);
				return HandleResult.COMPRESSING;
			}
			if (isForgetCommand(text)) {
				if (text.equals("忘掉上一条") || text.equals("forget last")) {
					removeLastPlayerMessage();
					return HandleResult.FORGET_LAST;
				}
				clear();
				return HandleResult.FORGET_ALL;
			}
			messages.addLast("player: " + text);
			return HandleResult.NORMAL;
		}

		public void addGolemReply(String text) {
			messages.addLast("golem: " + text);
		}

		/** 压缩完成后重建历史（摘要 + 尾部未压缩消息），并清压缩标记。 */
		public void setSummarized(List<String> replacement) {
			messages.clear();
			messages.addAll(replacement);
			compressing = false;
		}

		/** 压缩失败兜底：只保留最近 keep 条未压缩消息。 */
		public void dropToTail(int keep) {
			while (messages.size() > keep) {
				messages.removeFirst();
			}
			compressing = false;
		}

		public List<String> messages() {
			return new ArrayList<>(messages);
		}

		public boolean isCompressing() {
			return compressing;
		}

		public void markCompressing(boolean value) {
			this.compressing = value;
		}

		public int pendingCount() {
			return pending.size();
		}

		/** 取出一条排队消息（压缩完成后逐条处理）。 */
		public String pollPending() {
			return pending.pollFirst();
		}

		public void clear() {
			messages.clear();
			pending.clear();
		}

		/** 摘出最近一条玩家消息（压缩前移出当前消息；纠错指令 "忘掉上一条" 也用它）。 */
		public void removeLastPlayerMessage() {
			List<String> msgs = new ArrayList<>(messages);
			for (int i = msgs.size() - 1; i >= 0; i--) {
				if (msgs.get(i).startsWith("player: ")) {
					msgs.remove(i);
					messages.clear();
					messages.addAll(msgs);
					return;
				}
			}
		}
	}

	/** 本地纠错指令全词匹配（避免误触"我快忘掉了"）。 */
	public static boolean isForgetCommand(String text) {
		return text.equals("忘掉") || text.equals("forget")
			|| text.equals("忘掉上一条") || text.equals("forget last");
	}

	/** 新会话命令（隔离记忆）：清空历史+摘要，重新认识。 */
	public static boolean isResetCommand(String text) {
		return text.equals("换脑子") || text.equals("重新开始") || text.equals("从头开始")
			|| text.equals("新会话") || text.equals("重置记忆") || text.equals("reset");
	}

	/** 手动压缩命令（提前压缩上下文，不等阈值）。 */
	public static boolean isCompressCommand(String text) {
		return text.equals("压缩") || text.equals("总结一下") || text.equals("记住重点")
			|| text.equals("提前压缩") || text.equals("compress");
	}

	/** 压缩触发：历史消息数 > summaryMessages 或 估算 token > summaryTokens，任一达到即触发。 */
	public static boolean shouldCompress(GolemSession session, QuirkyConfig c) {
		int msgCount = session.messages().size();
		if (msgCount > c.aiSummaryMessages) {
			return true;
		}
		int totalTokens = 0;
		for (String m : session.messages()) {
			totalTokens += estimateTokens(m);
		}
		return totalTokens > c.aiSummaryTokens;
	}

	/** 中文约 1 字 ≈ 1 token 的粗估（英文按 1 字符 1 token 保守高估；宁可早压缩也不让 token 超限）。 */
	public static int estimateTokens(String text) {
		return Math.max(1, text.length());
	}
}
