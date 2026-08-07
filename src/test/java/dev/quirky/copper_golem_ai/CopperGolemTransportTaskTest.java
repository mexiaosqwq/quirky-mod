package dev.quirky.copper_golem_ai;

import dev.quirky.copper_golem_ai.CopperGolemTransportTask.State;
import dev.quirky.copper_golem_ai.CopperGolemTransportTask.StepDecision;
import org.junit.jupiter.api.Test;

import static dev.quirky.copper_golem_ai.CopperGolemTransportTask.State.*;
import static dev.quirky.copper_golem_ai.CopperGolemTransportTask.StepDecision.*;
import static dev.quirky.copper_golem_ai.CopperGolemTransportTask.StepDecision.ABORT;
import static dev.quirky.copper_golem_ai.CopperGolemTransportTask.StepDecision.FINISH;
import static org.junit.jupiter.api.Assertions.*;

class CopperGolemTransportTaskTest {

	@Test
	void walkStatesNeedMovementThenInteract() {
		assertEquals(WALK_TO, CopperGolemTransportTask.decide(WALK_SOURCE, false, false, false));
		assertEquals(INTERACT, CopperGolemTransportTask.decide(WALK_SOURCE, true, false, false));
		assertEquals(INTERACT, CopperGolemTransportTask.decide(WALK_DEST, true, true, false));
	}

	@Test
	void takeMissingItemFails() {
		assertEquals(ABORT, CopperGolemTransportTask.decide(TAKE, true, false, false));
	}

	@Test
	void stateTransitionsFollowSpec() {
		assertEquals(TAKE, CopperGolemTransportTask.nextState(WALK_SOURCE, INTERACT));
		assertEquals(WALK_DEST, CopperGolemTransportTask.nextState(TAKE, INTERACT));
		assertEquals(PUT, CopperGolemTransportTask.nextState(WALK_DEST, INTERACT));
		assertEquals(DONE, CopperGolemTransportTask.nextState(PUT, INTERACT));
		assertEquals(FAIL, CopperGolemTransportTask.nextState(TAKE, ABORT));
		assertEquals(WALK_SOURCE, CopperGolemTransportTask.nextState(IDLE, WALK_TO));
		assertEquals(DONE, CopperGolemTransportTask.nextState(DONE, FINISH));
	}

	@Test
	void terminalStates() {
		assertTrue(CopperGolemTransportTask.isTerminal(DONE));
		assertTrue(CopperGolemTransportTask.isTerminal(FAIL));
		assertFalse(CopperGolemTransportTask.isTerminal(IDLE));
		assertFalse(CopperGolemTransportTask.isTerminal(WALK_SOURCE));
	}
}
