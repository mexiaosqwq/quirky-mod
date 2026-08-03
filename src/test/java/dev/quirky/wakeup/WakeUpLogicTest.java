package dev.quirky.wakeup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.quirky.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WakeUpLogicTest {

	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	@Test
	void deepSleepGetsFullConfiguredDuration() {
		assertEquals(240, WakeUpLogic.durationTicks(true, 12));
	}

	@Test
	void interruptedSleepGetsOneThird() {
		assertEquals(80, WakeUpLogic.durationTicks(false, 12));
	}

	@Test
	void zeroSecondsYieldsZeroTicks() {
		assertEquals(0, WakeUpLogic.durationTicks(true, 0));
		assertEquals(0, WakeUpLogic.durationTicks(false, 0));
	}

	@Test
	void negativeConfigTreatedAsZero() {
		assertEquals(0, WakeUpLogic.durationTicks(true, -5));
	}

	@Test
	void nonMultipleOfThreeRoundsDown() {
		assertEquals(40, WakeUpLogic.durationTicks(false, 7));
	}
}
