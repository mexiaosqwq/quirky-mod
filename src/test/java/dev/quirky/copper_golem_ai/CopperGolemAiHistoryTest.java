package dev.quirky.copper_golem_ai;

import dev.quirky.copper_golem_ai.CopperGolemAiHistory.GolemSession;
import dev.quirky.copper_golem_ai.CopperGolemAiHistory.HandleResult;
import dev.quirky.config.QuirkyConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CopperGolemAiHistoryTest {

	@Test
	void forgetCommandsMatchExactly() {
		assertTrue(CopperGolemAiHistory.isForgetCommand("忘掉"));
		assertTrue(CopperGolemAiHistory.isForgetCommand("forget"));
		assertTrue(CopperGolemAiHistory.isForgetCommand("忘掉上一条"));
		assertTrue(CopperGolemAiHistory.isForgetCommand("forget last"));
		assertFalse(CopperGolemAiHistory.isForgetCommand("我快忘掉了"));
		assertFalse(CopperGolemAiHistory.isForgetCommand("forgettable"));
		assertFalse(CopperGolemAiHistory.isForgetCommand(""));
	}

	@Test
	void resetCommandsMatchExactly() {
		assertTrue(CopperGolemAiHistory.isResetCommand("换脑子"));
		assertTrue(CopperGolemAiHistory.isResetCommand("重新开始"));
		assertTrue(CopperGolemAiHistory.isResetCommand("从头开始"));
		assertTrue(CopperGolemAiHistory.isResetCommand("新会话"));
		assertTrue(CopperGolemAiHistory.isResetCommand("重置记忆"));
		assertTrue(CopperGolemAiHistory.isResetCommand("reset"));
		assertFalse(CopperGolemAiHistory.isResetCommand("重新开始吧")); // 全词匹配
		assertFalse(CopperGolemAiHistory.isResetCommand("忘掉"));
	}

	@Test
	void compressCommandsMatchExactly() {
		assertTrue(CopperGolemAiHistory.isCompressCommand("压缩"));
		assertTrue(CopperGolemAiHistory.isCompressCommand("总结一下"));
		assertTrue(CopperGolemAiHistory.isCompressCommand("记住重点"));
		assertTrue(CopperGolemAiHistory.isCompressCommand("提前压缩"));
		assertTrue(CopperGolemAiHistory.isCompressCommand("compress"));
		assertFalse(CopperGolemAiHistory.isCompressCommand("压缩一下记忆吧")); // 全词匹配
		assertFalse(CopperGolemAiHistory.isCompressCommand(""));
	}

	@Test
	void forgetAllClearsHistory() {
		GolemSession s = new GolemSession();
		assertEquals(HandleResult.NORMAL, s.addPlayerMessage("你好"));
		s.addGolemReply("你好呀");
		assertEquals(HandleResult.FORGET_ALL, s.addPlayerMessage("忘掉"));
		assertTrue(s.messages().isEmpty());
	}

	@Test
	void forgetLastRemovesOnlyLastPlayerMessage() {
		GolemSession s = new GolemSession();
		s.addPlayerMessage("第一条");
		s.addGolemReply("收到");
		s.addPlayerMessage("第二条");
		assertEquals(HandleResult.FORGET_LAST, s.addPlayerMessage("忘掉上一条"));
		List<String> msgs = s.messages();
		assertEquals(1, msgs.stream().filter(m -> m.startsWith("player:")).count());
		assertEquals("player: 第一条", msgs.get(0));
	}

	@Test
	void messagesStoreAlternatingRoles() {
		GolemSession s = new GolemSession();
		s.addPlayerMessage("在吗");
		s.addGolemReply("在的");
		assertEquals(List.of("player: 在吗", "golem: 在的"), s.messages());
	}

	@Test
	void compressTriggersOnMessageCount() {
		GolemSession s = new GolemSession();
		QuirkyConfig c = new QuirkyConfig();
		c.aiSummaryMessages = 3;
		c.aiSummaryTokens = 16000;
		s.addPlayerMessage("1");
		s.addGolemReply("a");
		s.addPlayerMessage("2");
		assertFalse(CopperGolemAiHistory.shouldCompress(s, c)); // 3 条 = 阈值，不触发
		s.addGolemReply("b");
		assertTrue(CopperGolemAiHistory.shouldCompress(s, c)); // 4 条 > 3，触发
	}

	@Test
	void compressTriggersOnTokenEstimate() {
		GolemSession s = new GolemSession();
		QuirkyConfig c = new QuirkyConfig();
		c.aiSummaryMessages = 100;
		c.aiSummaryTokens = 5; // "player: 一二三四五" ≈ 13 字符 / 2 = 6 tokens > 5
		s.addPlayerMessage("一二三四五");
		assertTrue(CopperGolemAiHistory.shouldCompress(s, c));
	}

	@Test
	void compressedReplacementKeepsTailAndClearsFlag() {
		GolemSession s = new GolemSession();
		for (int i = 0; i < 8; i++) {
			s.addPlayerMessage("q" + i);
			s.addGolemReply("a" + i);
		}
		s.markCompressing(true);
		List<String> replacement = List.of("system: 摘要内容", "player: q6", "golem: a6", "player: q7", "golem: a7");
		s.setSummarized(replacement);
		assertFalse(s.isCompressing());
		assertEquals(5, s.messages().size());
		assertEquals("system: 摘要内容", s.messages().get(0));
	}

	@Test
	void messagesQueuedWhileCompressing() {
		GolemSession s = new GolemSession();
		s.markCompressing(true);
		assertEquals(HandleResult.COMPRESSING, s.addPlayerMessage("压缩中说话"));
		assertTrue(s.pendingCount() == 1);
	}

	@Test
	void detachLastPlayerMessageForCompression() {
		GolemSession s = new GolemSession();
		s.addPlayerMessage("在吗");
		s.addGolemReply("在的");
		s.addPlayerMessage("帮我把钻石搬过来");
		s.removeLastPlayerMessage();
		assertEquals(List.of("player: 在吗", "golem: 在的"), s.messages());
		// 再摘一次：移除 "player: 在吗"
		s.removeLastPlayerMessage();
		assertEquals(List.of("golem: 在的"), s.messages());
		// 无玩家消息时静默无操作
		s.removeLastPlayerMessage();
		assertEquals(List.of("golem: 在的"), s.messages());
	}
}
