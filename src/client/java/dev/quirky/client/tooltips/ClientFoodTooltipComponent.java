package dev.quirky.client.tooltips;

import dev.quirky.tooltips.FoodTooltipComponent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.food.FoodProperties;

/**
 * 食物 tooltip 的客户端绘制组件：鸡腿图标 + '+营养值' + 饱和度图标 + '+饱和值'。
 * 度量与属性行统一（{@link TooltipRowMetrics}）：图标 9x9、16px 行高、垂直居中、间距 2/4。
 */
public class ClientFoodTooltipComponent implements ClientTooltipComponent {
	private static final Identifier DRUMSTICK_SPRITE = Identifier.withDefaultNamespace("hud/food_full");
	private static final Identifier SATURATION_SPRITE = Identifier.withDefaultNamespace("mob_effect/saturation");
	private static final int ICON_SIZE = 9;
	private static final int TEXT_COLOR = 0xFFFFFFFF;

	private final FoodProperties food;

	public ClientFoodTooltipComponent(FoodTooltipComponent component) {
		food = component.food();
	}

	@Override
	public int getWidth(Font font) {
		return ICON_SIZE + TooltipRowMetrics.ICON_TEXT_GAP + font.width(nutritionText())
			+ TooltipRowMetrics.CELL_GAP + ICON_SIZE + TooltipRowMetrics.ICON_TEXT_GAP + font.width(saturationText());
	}

	@Override
	public int getHeight(Font font) {
		return TooltipRowMetrics.LINE_HEIGHT;
	}

	@Override
	public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
		int yIcon = TooltipRowMetrics.iconY(y, ICON_SIZE);
		int yText = TooltipRowMetrics.textY(y);
		int cursor = x;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DRUMSTICK_SPRITE, cursor, yIcon, ICON_SIZE, ICON_SIZE);
		cursor += ICON_SIZE + TooltipRowMetrics.ICON_TEXT_GAP;
		graphics.text(font, nutritionText(), cursor, yText, TEXT_COLOR);
		cursor += font.width(nutritionText()) + TooltipRowMetrics.CELL_GAP;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SATURATION_SPRITE, cursor, yIcon, ICON_SIZE, ICON_SIZE);
		cursor += ICON_SIZE + TooltipRowMetrics.ICON_TEXT_GAP;
		graphics.text(font, saturationText(), cursor, yText, TEXT_COLOR);
	}

	private String nutritionText() {
		return "+" + food.nutrition();
	}

	private String saturationText() {
		float saturation = food.saturation();
		// 整数值不带小数（+6），否则保留一位小数（+9.6）
		if (saturation == (float) (int) saturation) {
			return "+" + (int) saturation;
		}
		return "+" + String.format("%.1f", saturation);
	}
}
