package dev.quirky.client.usage_ticker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.quirky.client.usage_ticker.TickerElement.State;
import org.junit.jupiter.api.Test;

class TickerElementTest {

	@Test
	void tickActive_startsSlideIn() {
		TickerElement element = new TickerElement(5, 10);

		element.tick(true);

		assertEquals(State.SLIDE_IN, element.state());
		assertTrue(element.isVisible());
		assertEquals(0.0F, element.progress(), 1e-6F);
	}

	@Test
	void idleTick_doesNothing() {
		TickerElement element = new TickerElement(5, 10);

		element.tick(false);

		assertEquals(State.IDLE, element.state());
		assertFalse(element.isVisible());
	}

	@Test
	void slideIn_progressAdvancesToHold() {
		TickerElement element = new TickerElement(2, 3);
		element.tick(true);

		element.tick(false);
		assertEquals(State.SLIDE_IN, element.state());
		assertEquals(0.5F, element.progress(), 1e-6F);
		assertTrue(element.isVisible());

		element.tick(false);
		assertEquals(State.HOLD, element.state());
		assertEquals(1.0F, element.progress(), 1e-6F);
	}

	@Test
	void holdExpires_thenSlidesOutAndIdles() {
		TickerElement element = new TickerElement(2, 3);
		element.tick(true);
		element.tick(false);
		element.tick(false); // -> HOLD
		assertEquals(State.HOLD, element.state());

		element.tick(false);
		element.tick(false);
		element.tick(false); // 保持到期 -> SLIDE_OUT
		assertEquals(State.SLIDE_OUT, element.state());
		assertTrue(element.isVisible());

		element.tick(false);
		element.tick(false); // 滑出完成 -> IDLE
		assertEquals(State.IDLE, element.state());
		assertFalse(element.isVisible());
	}

	@Test
	void retriggerWhileVisible_restartsSlideIn() {
		TickerElement element = new TickerElement(2, 3);
		element.tick(true);
		element.tick(false);
		element.tick(false); // -> HOLD

		element.tick(true); // 保持期间又来新事件

		assertEquals(State.SLIDE_IN, element.state());
		assertEquals(0.0F, element.progress(), 1e-6F);
	}

	@Test
	void slideOutProgressDecreasesRenderingOffset_thenIdles() {
		TickerElement element = new TickerElement(2, 3);
		element.tick(true);
		element.tick(false);
		element.tick(false); // HOLD
		element.tick(false);
		element.tick(false);
		element.tick(false); // SLIDE_OUT

		element.tick(false);
		assertEquals(State.SLIDE_OUT, element.state());
		assertEquals(0.5F, element.progress(), 1e-6F);
	}

	@Test
	void reset_returnsToIdle() {
		TickerElement element = new TickerElement(2, 3);
		element.tick(true);
		element.tick(false);

		element.reset();

		assertEquals(State.IDLE, element.state());
		assertFalse(element.isVisible());
	}
}
