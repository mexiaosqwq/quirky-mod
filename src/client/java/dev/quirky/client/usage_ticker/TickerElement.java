package dev.quirky.client.usage_ticker;

/**
 * 挂件动画状态机：IDLE → SLIDE_IN → HOLD → SLIDE_OUT → IDLE（纯逻辑，可单测）。
 *
 * 显示周期 = animTicks 滑入 + holdTicks 保持 + animTicks 滑出；
 * 渲染侧用 {@link #state()} 与 {@link #progress()} 计算位移
 * （Quark 同款 ease-out 曲线，见 {@link #SLIDE_DISTANCE}）。
 */
public class TickerElement {
	public enum State {
		IDLE, SLIDE_IN, HOLD, SLIDE_OUT
	}

	/** 从下方滑入的位移（px），动画曲线 offset = -p*(p-2)*20，p ∈ [0,1]。 */
	public static final int SLIDE_DISTANCE = 20;

	private final int animTicks;
	private final int holdTicks;
	private State state = State.IDLE;
	private int ticksInState;

	public TickerElement(int animTicks, int holdTicks) {
		this.animTicks = animTicks;
		this.holdTicks = holdTicks;
	}

	/**
	 * 每 tick 调用一次。
	 *
	 * @param active 有新事件触发：无论当前处于哪个阶段，都重新从滑入开始（显示周期重新计时）
	 */
	public void tick(boolean active) {
		if (active) {
			this.state = State.SLIDE_IN;
			this.ticksInState = 0;
			return;
		}
		if (this.state == State.IDLE) {
			return;
		}
		this.ticksInState++;
		switch (this.state) {
			case SLIDE_IN -> {
				if (this.ticksInState >= this.animTicks) {
					this.state = State.HOLD;
					this.ticksInState = 0;
				}
			}
			case HOLD -> {
				if (this.ticksInState >= this.holdTicks) {
					this.state = State.SLIDE_OUT;
					this.ticksInState = 0;
				}
			}
			case SLIDE_OUT -> {
				if (this.ticksInState >= this.animTicks) {
					this.state = State.IDLE;
					this.ticksInState = 0;
				}
			}
			case IDLE -> {
			}
		}
	}

	/** 立即回到闲置状态（例如功能被配置关闭时）。 */
	public void reset() {
		this.state = State.IDLE;
		this.ticksInState = 0;
	}

	/** 当前是否应当绘制。 */
	public boolean isVisible() {
		return this.state != State.IDLE;
	}

	/**
	 * 动画进度 p ∈ [0,1]：滑入/滑出期间从 0 递增到 1，保持期间恒为 1，闲置为 0。
	 */
	public float progress() {
		return switch (this.state) {
			case IDLE -> 0.0F;
			case SLIDE_IN, SLIDE_OUT -> (float) this.ticksInState / this.animTicks;
			case HOLD -> 1.0F;
		};
	}

	public State state() {
		return this.state;
	}
}
