package dev.quirky.client.tooltips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import dev.quirky.QuirkyMod;
import dev.quirky.TestBootstrap;
import dev.quirky.tooltips.AttributeTooltipComponent;
import dev.quirky.tooltips.AttributeTooltipComponent.AttributeLine;
import net.minecraft.client.gui.Font;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 属性 tooltip 横排布局断言（与食物行统一度量：9x9 图标、16px 行高、间距 2/4）：
 * 单元格 = 9(图标) + 2(间距) + 文本宽，单元格间 4px；总宽 = 单元格求和，高 = 单行 16px。
 */
class ClientAttributeTooltipComponentTest {

	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	private static AttributeLine line(String iconPath, String text) {
		return new AttributeLine(QuirkyMod.id(iconPath), text);
	}

	@Test
	void widthSumsCellsHorizontally() {
		Font font = mock(Font.class);
		when(font.width("7")).thenReturn(6);
		when(font.width("1.6")).thenReturn(15);

		ClientAttributeTooltipComponent client = new ClientAttributeTooltipComponent(
			new AttributeTooltipComponent(List.of(
				line("attribute/attack_damage", "7"),
				line("attribute/attack_speed", "1.6")
			))
		);

		// (9 + 2 + 6) + 4 + (9 + 2 + 15) = 47
		assertEquals(47, client.getWidth(font));
	}

	@Test
	void heightIsSingleRow() {
		Font font = mock(Font.class);
		when(font.width("7")).thenReturn(6);
		when(font.width("1.6")).thenReturn(15);
		when(font.width("8")).thenReturn(6);
		when(font.width("2")).thenReturn(6);

		ClientAttributeTooltipComponent one = new ClientAttributeTooltipComponent(
			new AttributeTooltipComponent(List.of(line("attribute/attack_damage", "7")))
		);
		ClientAttributeTooltipComponent two = new ClientAttributeTooltipComponent(
			new AttributeTooltipComponent(List.of(
				line("attribute/attack_damage", "7"),
				line("attribute/attack_speed", "1.6")
			))
		);
		ClientAttributeTooltipComponent four = new ClientAttributeTooltipComponent(
			new AttributeTooltipComponent(List.of(
				line("attribute/armor", "8"),
				line("attribute/toughness", "2"),
				line("attribute/attack_speed", "1.6"),
				line("attribute/attack_damage", "7")
			))
		);

		// 横排：无论多少属性都只占一行（与食物行同 16px 行高）
		assertEquals(16, one.getHeight(font));
		assertEquals(16, two.getHeight(font));
		assertEquals(16, four.getHeight(font));
	}

	@Test
	void emptyLinesYieldZeroSize() {
		Font font = mock(Font.class);

		ClientAttributeTooltipComponent client = new ClientAttributeTooltipComponent(
			new AttributeTooltipComponent(List.of())
		);

		assertEquals(0, client.getWidth(font));
		assertEquals(0, client.getHeight(font));
	}
}
