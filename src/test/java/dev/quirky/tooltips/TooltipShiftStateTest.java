package dev.quirky.tooltips;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** {@link TooltipShiftState} 生命周期：默认不抑制，enter/exit 成对生效。 */
class TooltipShiftStateTest {

	@Test
	void defaultsToNotSuppressing() {
		assertFalse(TooltipShiftState.isSuppressing());
	}

	@Test
	void enterTrueThenExit() {
		TooltipShiftState.enter(true);
		assertTrue(TooltipShiftState.isSuppressing());
		TooltipShiftState.exit();
		assertFalse(TooltipShiftState.isSuppressing());
	}

	@Test
	void enterFalseDoesNotSuppress() {
		TooltipShiftState.enter(false);
		assertFalse(TooltipShiftState.isSuppressing());
		TooltipShiftState.exit();
	}

	@Test
	void enterOverwritesPreviousState() {
		TooltipShiftState.enter(true);
		TooltipShiftState.enter(false);
		assertFalse(TooltipShiftState.isSuppressing());
		TooltipShiftState.exit();
	}
}
