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
		assertEquals(0.0F, element.progress(0.0F), 1e-6F);
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
		assertEquals(0.5F, element.progress(0.0F), 1e-6F);
		assertTrue(element.isVisible());

		element.tick(false);
		assertEquals(State.HOLD, element.state());
		assertEquals(1.0F, element.progress(0.0F), 1e-6F);
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
	void retriggerDuringSlideIn_keepsProgress() {
		TickerElement element = new TickerElement(2, 3);
		element.tick(true);
		element.tick(false); // 滑入 1/2

		element.tick(true); // 滑入途中来新事件：不重启动画

		assertEquals(State.SLIDE_IN, element.state());
		assertEquals(0.5F, element.progress(0.0F), 1e-6F);
	}

	@Test
	void retriggerDuringHold_refreshesHoldDuration() {
		TickerElement element = new TickerElement(2, 3);
		element.tick(true);
		element.tick(false);
		element.tick(false); // -> HOLD
		element.tick(false); // 保持 1 tick

		element.tick(true); // 保持期间来新事件：保持时长重新计时，不重滑

		assertEquals(State.HOLD, element.state());
		assertEquals(1.0F, element.progress(0.0F), 1e-6F);
		element.tick(false);
		element.tick(false);
		element.tick(false); // 刷新后保持 3 tick 到期
		assertEquals(State.SLIDE_OUT, element.state());
	}

	@Test
	void retriggerDuringSlideOut_reversesIntoSlideIn() {
		TickerElement element = new TickerElement(10, 30);
		element.tick(true);
		for (int i = 0; i < 10; i++) {
			element.tick(false);
		}
		for (int i = 0; i < 30; i++) {
			element.tick(false);
		}
		// SLIDE_OUT 进行到一半（q = 5/10）
		for (int i = 0; i < 5; i++) {
			element.tick(false);
		}
		assertEquals(State.SLIDE_OUT, element.state());
		assertEquals(0.5F, element.progress(0.0F), 1e-6F);

		element.tick(true); // 滑出途中来新事件：按位置反演滑回

		assertEquals(State.SLIDE_IN, element.state());
		// q = 0.5 → p' = 1 - sqrt(0.75) ≈ 0.134 → round(1.34) = 1/10
		assertEquals(0.1F, element.progress(0.0F), 1e-6F);
		assertTrue(element.isVisible());
	}

	@Test
	void retriggerDuringSlideOut_positionStaysContinuous() {
		// ease(p) = p*(2-p)；滑出位置 = ease(q)，反向后滑入位置 = 1 - ease(p')，位置连续要求两者之和为 1
		for (int qTicks = 1; qTicks < 10; qTicks++) {
			TickerElement element = new TickerElement(10, 30);
			element.tick(true);
			for (int i = 0; i < 10; i++) {
				element.tick(false);
			}
			for (int i = 0; i < 30; i++) {
				element.tick(false);
			}
			for (int i = 0; i < qTicks; i++) {
				element.tick(false);
			}
			float q = (float) qTicks / 10;

			element.tick(true);

			float p = element.progress(0.0F);
			// 量化误差 ≤ 0.1（一个 tick），位置差 ≤ 2px
			assertEquals(1.0F, ease(q) + ease(p), 0.12F);
		}
	}

	private static float ease(float p) {
		return p * (2.0F - p);
	}

	@Test
	void progress_interpolatesWithPartialTick() {
		TickerElement element = new TickerElement(4, 3);
		element.tick(true); // 滑入 0/4
		element.tick(false); // 滑入 1/4

		assertEquals(0.375F, element.progress(0.5F), 1e-6F);
	}

	@Test
	void progress_clampsAtOne() {
		TickerElement element = new TickerElement(2, 3);
		element.tick(true);
		element.tick(false); // 滑入 1/2

		assertEquals(1.0F, element.progress(1.0F), 1e-6F); // (1+1)/2 已达上限
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
		assertEquals(0.5F, element.progress(0.0F), 1e-6F);
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
