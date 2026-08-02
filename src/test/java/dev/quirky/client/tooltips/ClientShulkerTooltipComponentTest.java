package dev.quirky.client.tooltips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.quirky.TestBootstrap;
import dev.quirky.tooltips.ShulkerTooltipComponent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClientShulkerTooltipComponentTest {

	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
		TestBootstrap.bindItem(Items.STONE);
	}

	@Test
	void layoutIsNineByThree() {
		// 与原版潜影盒 UI 一致：9 列 x 3 行（宽 > 高），槽 18px（16 图标 + 2 边距）
		ShulkerTooltipComponent component = new ShulkerTooltipComponent(ItemContainerContents.EMPTY, null);
		ClientShulkerTooltipComponent client = new ClientShulkerTooltipComponent(component);
		Font font = mock(Font.class);
		assertEquals(9 * 18 + 8, client.getWidth(font));
		assertEquals(3 * 18 + 8, client.getHeight(font));
	}

	@Test
	void retainsContents() {
		ItemContainerContents contents = ItemContainerContents.fromItems(java.util.List.of(
			new ItemStack(Items.STONE, 3)
		));
		ShulkerTooltipComponent component = new ShulkerTooltipComponent(contents, DyeColor.RED);
		assertEquals(contents, component.contents());
		assertEquals(DyeColor.RED, component.color());
	}

	@Test
	void drawsBackgroundAndSlotBacking() {
		// 1 次整体背景 fill + 27 格槽 fill + 27*2 条边框线
		ShulkerTooltipComponent component = new ShulkerTooltipComponent(ItemContainerContents.EMPTY, null);
		ClientShulkerTooltipComponent client = new ClientShulkerTooltipComponent(component);
		Font font = mock(Font.class);
		GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);

		client.extractImage(font, 0, 0, client.getWidth(font), client.getHeight(font), graphics);

		verify(graphics, times(28)).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
		verify(graphics, times(27 * 2)).horizontalLine(anyInt(), anyInt(), anyInt(), anyInt());
		verify(graphics, times(27 * 2)).verticalLine(anyInt(), anyInt(), anyInt(), anyInt());
	}

	@Test
	void plainBoxUsesPurpleBackground() {
		// 普通盒：紫色调背景（经典潜影盒 UI 风格）
		ShulkerTooltipComponent component = new ShulkerTooltipComponent(ItemContainerContents.EMPTY, null);
		ClientShulkerTooltipComponent client = new ClientShulkerTooltipComponent(component);
		Font font = mock(Font.class);
		GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);

		client.extractImage(font, 0, 0, client.getWidth(font), client.getHeight(font), graphics);

		verify(graphics, times(1)).fill(anyInt(), anyInt(), anyInt(), anyInt(), eq(0xE03A2A5E));
	}

	@Test
	void coloredBoxUsesBoxColorTones() {
		// 16 色盒：背景 = 盒色 30% 亮度（alpha 0xE0）；边框 = 盒色 70%（alpha 0x8A）
		ShulkerTooltipComponent component = new ShulkerTooltipComponent(ItemContainerContents.EMPTY, DyeColor.RED);
		ClientShulkerTooltipComponent client = new ClientShulkerTooltipComponent(component);
		Font font = mock(Font.class);
		GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);

		client.extractImage(font, 0, 0, client.getWidth(font), client.getHeight(font), graphics);

		int red = DyeColor.RED.getTextureDiffuseColor();
		int expectedBg = 0xE0000000 | (((red >> 16) & 0xFF) * 30 / 100) << 16
			| (((red >> 8) & 0xFF) * 30 / 100) << 8 | ((red & 0xFF) * 30 / 100);
		verify(graphics, times(1)).fill(anyInt(), anyInt(), anyInt(), anyInt(), eq(expectedBg));
	}
}
