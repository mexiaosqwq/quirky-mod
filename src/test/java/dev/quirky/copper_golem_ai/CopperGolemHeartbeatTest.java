package dev.quirky.copper_golem_ai;

import org.junit.jupiter.api.Test;

import static dev.quirky.copper_golem_ai.CopperGolemHeartbeat.shouldHeartbeat;
import static org.junit.jupiter.api.Assertions.*;

class CopperGolemHeartbeatTest {

	@Test
	void intervalZeroDisables() {
		assertFalse(shouldHeartbeat(0, 600, 0, true, false));
	}

	@Test
	void busySuppresses() {
		assertFalse(shouldHeartbeat(30, 600, 0, true, true));
	}

	@Test
	void noPlayerNearbySuppresses() {
		assertFalse(shouldHeartbeat(30, 600, 0, false, false));
	}

	@Test
	void intervalNotElapsedSuppresses() {
		// 30s = 600 tick；距上次 599 tick → 不触发
		assertFalse(shouldHeartbeat(30, 599, 0, true, false));
	}

	@Test
	void intervalElapsedFires() {
		assertTrue(shouldHeartbeat(30, 600, 0, true, false));
		assertTrue(shouldHeartbeat(30, 601, 0, true, false));
	}
}
