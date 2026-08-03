package dev.quirky.client.tooltips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.quirky.TestBootstrap;
import dev.quirky.tooltips.ShulkerTooltipComponent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ClientShulkerTooltipComponentTest {

	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
		TestBootstrap.bindItem(Items.STONE);
	}

	@Test
	void layoutIsNineByThree() {
		// 与原版潜影盒 UI 一致：9 列 x 3 行（宽 > 高），槽 18px（16 图标 + 2 边距）
		ShulkerTooltipComponent component = new ShulkerTooltipComponent(ItemContainerContents.EMPTY);
		ClientShulkerTooltipComponent client = new ClientShulkerTooltipComponent(component);
		Font font = mock(Font.class);
		assertEquals(9 * 18, client.getWidth(font));
		assertEquals(3 * 18, client.getHeight(font));
	}

	@Test
	void retainsContents() {
		ItemContainerContents contents = ItemContainerContents.fromItems(java.util.List.of(
			new ItemStack(Items.STONE, 3)
		));
		ShulkerTooltipComponent component = new ShulkerTooltipComponent(contents);
		assertEquals(contents, component.contents());
	}

	@Test
	void drawsVanillaSlotSpritesWithoutCustomBackground() {
		ShulkerTooltipComponent component = new ShulkerTooltipComponent(ItemContainerContents.EMPTY);
		ClientShulkerTooltipComponent client = new ClientShulkerTooltipComponent(component);
		Font font = mock(Font.class);
		GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
		Identifier slotSprite = Identifier.withDefaultNamespace("container/slot");

		client.extractImage(font, 0, 0, client.getWidth(font), client.getHeight(font), graphics);

		verify(graphics, times(27)).blitSprite(
			eq(RenderPipelines.GUI_TEXTURED), eq(slotSprite), anyInt(), anyInt(), eq(18), eq(18)
		);
		verify(graphics, times(0)).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
		verify(graphics, times(0)).horizontalLine(anyInt(), anyInt(), anyInt(), anyInt());
		verify(graphics, times(0)).verticalLine(anyInt(), anyInt(), anyInt(), anyInt());
	}

	@Test
	void drawsItemAndCountAtOnePixelInsideSlot() {
		ItemStack stack = new ItemStack(Items.STONE, 3);
		ItemContainerContents contents = ItemContainerContents.fromItems(java.util.List.of(stack));
		ShulkerTooltipComponent component = new ShulkerTooltipComponent(contents);
		ClientShulkerTooltipComponent client = new ClientShulkerTooltipComponent(component);
		Font font = mock(Font.class);
		GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);

		client.extractImage(font, 0, 0, client.getWidth(font), client.getHeight(font), graphics);

		ArgumentCaptor<ItemStack> renderedStack = ArgumentCaptor.forClass(ItemStack.class);
		verify(graphics).item(renderedStack.capture(), eq(1), eq(1));
		assertTrue(renderedStack.getValue().is(Items.STONE));
		assertEquals(3, renderedStack.getValue().getCount());
		verify(graphics).itemDecorations(eq(font), any(ItemStack.class), eq(1), eq(1));
	}
}
