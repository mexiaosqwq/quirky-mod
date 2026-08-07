package dev.quirky.copper_golem_ai;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CopperGolemActionLogTest {

	@Test
	void recordsActionsNewestFirstAndCapsAtTen() {
		UUID id = UUID.randomUUID();
		for (int i = 1; i <= 12; i++) {
			CopperGolemActionLog.recordAction(id, "动作" + i);
		}
		String s = CopperGolemActionLog.summary(id);
		// 容量 10：动作 3~12 保留；摘要只取最近 3 条
		assertTrue(s.contains("动作12") && s.contains("动作11") && s.contains("动作10"));
		assertFalse(s.contains("动作2"));
	}

	@Test
	void failureKeepsOnlyLatest() {
		UUID id = UUID.randomUUID();
		CopperGolemActionLog.recordFailure(id, "fail1");
		CopperGolemActionLog.recordFailure(id, "fail2");
		assertTrue(CopperGolemActionLog.summary(id).contains("fail2"));
		assertFalse(CopperGolemActionLog.summary(id).contains("fail1"));
	}

	@Test
	void emptyReturnsNull() {
		assertNull(CopperGolemActionLog.summary(UUID.randomUUID()));
	}

	@Test
	void clearRemoves() {
		UUID id = UUID.randomUUID();
		CopperGolemActionLog.recordAction(id, "x");
		CopperGolemActionLog.clear(id);
		assertNull(CopperGolemActionLog.summary(id));
	}

	@Test
	void clearWithoutSessionRemovesIdleEntries() {
		UUID active = UUID.randomUUID();
		UUID idle = UUID.randomUUID();
		CopperGolemActionLog.recordAction(active, "a1");
		CopperGolemActionLog.recordAction(idle, "i1");
		CopperGolemActionLog.recordFailure(idle, "i-fail");
		CopperGolemActionLog.clearWithoutSession(java.util.Set.of(active));
		assertNotNull(CopperGolemActionLog.summary(active));
		assertNull(CopperGolemActionLog.summary(idle));
	}
}
