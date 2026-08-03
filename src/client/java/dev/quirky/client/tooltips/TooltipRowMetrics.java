package dev.quirky.client.tooltips;

/**
 * 自定义 tooltip 横排行的统一度量（食物行与属性行共用，防止两套常量漂移导致观感不对齐）：
 * 16px 行高、内容垂直居中、图标-文本间距 2、单元格间距 4，与 Quark 紧凑横条同族观感。
 */
public final class TooltipRowMetrics {
	public static final int LINE_HEIGHT = 16;
	public static final int ICON_TEXT_GAP = 2;
	public static final int CELL_GAP = 4;
	public static final int TEXT_HEIGHT = 8;

	private TooltipRowMetrics() {
	}

	public static int iconY(int y, int iconSize) {
		return y + (LINE_HEIGHT - iconSize) / 2;
	}

	public static int textY(int y) {
		return y + (LINE_HEIGHT - TEXT_HEIGHT) / 2;
	}
}
