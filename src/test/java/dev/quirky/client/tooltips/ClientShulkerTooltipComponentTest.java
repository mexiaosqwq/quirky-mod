package dev.quirky.client.tooltips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import dev.quirky.TestBootstrap;
import dev.quirky.tooltips.ShulkerTooltipComponent;
import net.minecraft.client.gui.Font;
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
	void layoutIsThreeByNine() {
		ShulkerTooltipComponent component = new ShulkerTooltipComponent(ItemContainerContents.EMPTY);
		ClientShulkerTooltipComponent client = new ClientShulkerTooltipComponent(component);
		Font font = mock(Font.class);
		assertEquals(3 * 16 + 8, client.getWidth(font));
		assertEquals(9 * 16 + 8, client.getHeight(font));
	}

	@Test
	void retainsContents() {
		ItemContainerContents contents = ItemContainerContents.fromItems(java.util.List.of(
			new ItemStack(Items.STONE, 3)
		));
		ShulkerTooltipComponent component = new ShulkerTooltipComponent(contents);
		assertEquals(contents, component.contents());
	}
}
