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
		// 与原版潜影盒 UI 一致：9 列 x 3 行（宽 > 高），不是竖长条
		ShulkerTooltipComponent component = new ShulkerTooltipComponent(ItemContainerContents.EMPTY, null);
		ClientShulkerTooltipComponent client = new ClientShulkerTooltipComponent(component);
		Font font = mock(Font.class);
		assertEquals(9 * 16 + 8, client.getWidth(font));
		assertEquals(3 * 16 + 8, client.getHeight(font));
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
	void drawsSlotBackingForEverySlot() {
		// 27 格都应画底槽：1 次 fill + 4 条边框线
		ShulkerTooltipComponent component = new ShulkerTooltipComponent(ItemContainerContents.EMPTY, null);
		ClientShulkerTooltipComponent client = new ClientShulkerTooltipComponent(component);
		Font font = mock(Font.class);
		GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);

		client.extractImage(font, 0, 0, client.getWidth(font), client.getHeight(font), graphics);

		verify(graphics, times(27)).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
		verify(graphics, times(27 * 2)).horizontalLine(anyInt(), anyInt(), anyInt(), anyInt());
		verify(graphics, times(27 * 2)).verticalLine(anyInt(), anyInt(), anyInt(), anyInt());
	}

	@Test
	void slotBackingUsesBoxColor() {
		// 红色潜影盒：底槽填充色 = 红盒色 25% 亮度、alpha 0xE0
		ShulkerTooltipComponent component = new ShulkerTooltipComponent(ItemContainerContents.EMPTY, DyeColor.RED);
		ClientShulkerTooltipComponent client = new ClientShulkerTooltipComponent(component);
		Font font = mock(Font.class);
		GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);

		client.extractImage(font, 0, 0, client.getWidth(font), client.getHeight(font), graphics);

		int red = DyeColor.RED.getTextureDiffuseColor();
		int expectedFill = 0xE0000000 | (((red >> 16) & 0xFF) * 25 / 100) << 16
			| (((red >> 8) & 0xFF) * 25 / 100) << 8 | ((red & 0xFF) * 25 / 100);
		verify(graphics, times(27)).fill(anyInt(), anyInt(), anyInt(), anyInt(), eq(expectedFill));
	}
}
