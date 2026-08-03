package dev.quirky.client.usage_ticker;

/**
 * 挂件动画状态机：IDLE → SLIDE_IN → HOLD → SLIDE_OUT → IDLE（纯逻辑，可单测）。
 *
 * 显示周期 = animTicks 滑入 + holdTicks 保持 + animTicks 滑出；
 * 渲染侧用 {@link #state()} 与 {@link #progress(float)} 计算位移
 * （Quark 同款 ease-out 曲线，见 {@link #SLIDE_DISTANCE}）。
 *
 * 重触发语义（对齐 Quark）：挂件已显示时来新事件只刷新内容，不重启动画——
 * 滑入中保持当前进度、保持中重新计时、滑出中反向滑回。否则快速连续使用
 * （如放置方块，右键间隔 4 tick &lt; 滑入 5 tick）会把动画反复重置回屏幕下方起点，
 * 挂件永远滑不进来，看起来"没弹出"。
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
	 * @param active 有新事件触发：已显示时只刷新状态不重启动画（滑入保持进度、
	 *               保持重新计时、滑出反向滑回）；闲置时才从头滑入
	 */
	public void tick(boolean active) {
		if (active) {
			switch (this.state) {
				case IDLE -> {
					this.state = State.SLIDE_IN;
					this.ticksInState = 0;
				}
				case SLIDE_IN -> {
					// 保持当前进度，不重启动画
				}
				case HOLD -> {
					this.ticksInState = 0;
				}
				case SLIDE_OUT -> {
					// 按位置反演滑回：滑出进度 q 在 ease-out 曲线上的位置 ease(q)，
					// 反向到 SLIDE_IN 进度 p' 使位置连续（ease(p') = 1 - ease(q) → p' = 1 - sqrt(2q - q²)）
					// （简单取 p' = 1 - q 会在中途反向时最多跳变 ~10px）
					float q = (float) this.ticksInState / this.animTicks;
					float p = 1.0F - (float) Math.sqrt(2.0F * q - q * q);
					this.state = State.SLIDE_IN;
					this.ticksInState = Math.round(p * this.animTicks);
				}
			}
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
	 *
	 * @param partialTick 渲染帧间插值（0..1），平滑 tick 步进，见 {@link net.minecraft.client.DeltaTracker}
	 */
	public float progress(float partialTick) {
		return switch (this.state) {
			case IDLE -> 0.0F;
			case SLIDE_IN, SLIDE_OUT -> Math.min(1.0F, (this.ticksInState + partialTick) / this.animTicks);
			case HOLD -> 1.0F;
		};
	}

	public State state() {
		return this.state;
	}
}
