package dev.quirky.copper_golem_ai;

/**
 * 搬运任务状态机（纯决策逻辑，可单测；MC 交互由服务端薄层执行）。
 * IDLE→WALK_SOURCE→TAKE→WALK_DEST→PUT→DONE；任何阶段失败→FAIL。
 */
public final class CopperGolemTransportTask {
	public enum State { IDLE, WALK_SOURCE, TAKE, WALK_DEST, PUT, DONE, FAIL }

	public enum StepDecision { WALK_TO, INTERACT, FINISH, ABORT }

	private CopperGolemTransportTask() {
	}

	/**
	 * 每 tick 决策：未到达→继续走；到达→交互；TAKE 无货→FAIL；PUT 完成→DONE。
	 * containerHasRequestedItem 仅在 TAKE 阶段有意义；putSucceeded 仅在 PUT 阶段有意义。
	 */
	public static StepDecision decide(State s, boolean atTarget, boolean containerHasRequestedItem, boolean putSucceeded) {
		return switch (s) {
			case WALK_SOURCE, WALK_DEST -> atTarget ? StepDecision.INTERACT : StepDecision.WALK_TO;
			case TAKE -> containerHasRequestedItem ? StepDecision.INTERACT : StepDecision.ABORT;
			case PUT -> putSucceeded ? StepDecision.FINISH : StepDecision.ABORT;
			default -> StepDecision.FINISH;
		};
	}

	/** 状态流转：交互动作推进一步；FINISH/ABORT 落到终态。 */
	public static State nextState(State current, StepDecision decision) {
		return switch (decision) {
			case WALK_TO -> current == State.IDLE ? State.WALK_SOURCE : current;
			case INTERACT -> switch (current) {
				case WALK_SOURCE -> State.TAKE;
				case TAKE -> State.WALK_DEST;
				case WALK_DEST -> State.PUT;
				case PUT -> State.DONE;
				default -> current;
			};
			case ABORT -> State.FAIL;
			case FINISH -> State.DONE;
		};
	}

	public static boolean isTerminal(State s) {
		return s == State.DONE || s == State.FAIL;
	}
}
