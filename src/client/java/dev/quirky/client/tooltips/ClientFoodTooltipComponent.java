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
 * 图标均为 9x9，文本 8px 高，整体行高 16px，内容垂直居中。
 */
public class ClientFoodTooltipComponent implements ClientTooltipComponent {
	private static final Identifier DRUMSTICK_SPRITE = Identifier.withDefaultNamespace("hud/food_full");
	private static final Identifier SATURATION_SPRITE = Identifier.withDefaultNamespace("mob_effect/saturation");
	private static final int ICON_SIZE = 9;
	private static final int ICON_TEXT_GAP = 2;
	private static final int ICON_ICON_GAP = 4;
	private static final int LINE_HEIGHT = 16;
	private static final int TEXT_COLOR = 0xFFFFFFFF;

	private final FoodProperties food;

	public ClientFoodTooltipComponent(FoodTooltipComponent component) {
		food = component.food();
	}

	@Override
	public int getWidth(Font font) {
		return ICON_SIZE + ICON_TEXT_GAP + font.width(nutritionText())
			+ ICON_ICON_GAP + ICON_SIZE + ICON_TEXT_GAP + font.width(saturationText());
	}

	@Override
	public int getHeight(Font font) {
		return LINE_HEIGHT;
	}

	@Override
	public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
		int yIcon = y + (LINE_HEIGHT - ICON_SIZE) / 2;
		int yText = y + (LINE_HEIGHT - 8) / 2;
		int cursor = x;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DRUMSTICK_SPRITE, cursor, yIcon, ICON_SIZE, ICON_SIZE);
		cursor += ICON_SIZE + ICON_TEXT_GAP;
		graphics.text(font, nutritionText(), cursor, yText, TEXT_COLOR);
		cursor += font.width(nutritionText()) + ICON_ICON_GAP;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SATURATION_SPRITE, cursor, yIcon, ICON_SIZE, ICON_SIZE);
		cursor += ICON_SIZE + ICON_TEXT_GAP;
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
