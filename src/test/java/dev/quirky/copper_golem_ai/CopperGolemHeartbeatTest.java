package dev.quirky.copper_golem_ai;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static dev.quirky.copper_golem_ai.CopperGolemHeartbeat.nextHeartbeatTick;
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
	void notDueYetSuppresses() {
		// 下次触发 601；当前 600 → 不到点
		assertFalse(shouldHeartbeat(30, 600, 601, true, false));
	}

	@Test
	void dueFires() {
		assertTrue(shouldHeartbeat(30, 600, 600, true, false));
		assertTrue(shouldHeartbeat(30, 601, 600, true, false));
	}

	@Test
	void jitterStaysWithinBounds() {
		// 30s = 600 tick → 下次 ∈ [now+450, now+750]（0.75x~1.25x）
		RandomSource r = RandomSource.create(42L);
		for (int i = 0; i < 200; i++) {
			long next = nextHeartbeatTick(r, 30, 1000);
			assertTrue(next >= 1000 + 450, "too early: " + next);
			assertTrue(next <= 1000 + 750, "too late: " + next);
		}
	}

	@Test
	void jitterSpreadsPhases() {
		// 多傀儡同一时刻出发 → 下一轮 next 不同（相位错开，防齐射）
		RandomSource r = RandomSource.create(7L);
		long a = nextHeartbeatTick(r, 30, 1000);
		long b = nextHeartbeatTick(r, 30, 1000);
		assertNotEquals(a, b);
	}
}
