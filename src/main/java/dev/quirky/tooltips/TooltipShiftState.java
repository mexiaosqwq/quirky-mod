package dev.quirky.tooltips;

/**
 * 高级 tooltip 模式（按 Shift 查看）的调用级抑制标记。
 *
 * <p>客户端 {@code ItemStack.getTooltipLines} HEAD 注入计算本次调用是否应抑制
 * mod 增强 tooltip，写入此 ThreadLocal；各 tooltip 来源（物品
 * {@code appendHoverText}、客户端 mixin）通过 {@link #isSuppressing()} 决定是否添加内容；
 * RETURN 注入追加"按 Shift 查看详情"提示后调用 {@link #exit()} 清理。
 * 搜索索引构建路径（player == null）与无客户端环境（服务端）恒不抑制。
 */
public final class TooltipShiftState {
	private static final ThreadLocal<Boolean> SUPPRESSING = ThreadLocal.withInitial(() -> false);

	private TooltipShiftState() {
	}

	/** 当前调用链是否处于"未按 Shift、应隐藏 mod 增强 tooltip"状态。 */
	public static boolean isSuppressing() {
		return SUPPRESSING.get();
	}

	/** 进入一次 getTooltipLines 调用：写入本次调用的抑制状态。 */
	public static void enter(boolean suppressing) {
		SUPPRESSING.set(suppressing);
	}

	/** 退出调用链并清理，防止 ThreadLocal 泄漏到下一次调用。 */
	public static void exit() {
		SUPPRESSING.remove();
	}
}
