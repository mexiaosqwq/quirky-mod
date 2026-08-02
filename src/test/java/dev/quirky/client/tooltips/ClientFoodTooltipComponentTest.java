package dev.quirky.client.tooltips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.quirky.TestBootstrap;
import dev.quirky.tooltips.FoodTooltipComponent;
import net.minecraft.client.gui.Font;
import net.minecraft.world.food.FoodProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClientFoodTooltipComponentTest {

	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	@Test
	void retainsNutritionAndSaturation() {
		FoodProperties food = mock(FoodProperties.class);
		when(food.nutrition()).thenReturn(6);
		when(food.saturation()).thenReturn(9.6F);

		FoodTooltipComponent component = new FoodTooltipComponent(food);
		ClientFoodTooltipComponent client = new ClientFoodTooltipComponent(component);

		assertEquals(6, component.food().nutrition());
		assertEquals(9.6F, component.food().saturation());
	}

	@Test
	void widthAccountsForIconsAndText() {
		FoodProperties food = mock(FoodProperties.class);
		when(food.nutrition()).thenReturn(6);
		when(food.saturation()).thenReturn(9.6F);

		ClientFoodTooltipComponent client = new ClientFoodTooltipComponent(new FoodTooltipComponent(food));
		Font font = mock(Font.class);
		when(font.width("+6")).thenReturn(10);
		when(font.width("+9.6")).thenReturn(18);

		// 9 + 2 + 10 + 4 + 9 + 2 + 18
		assertEquals(54, client.getWidth(font));
		assertEquals(16, client.getHeight(font));
	}

	@Test
	void integralSaturationRendersWithoutDecimal() {
		FoodProperties food = mock(FoodProperties.class);
		when(food.nutrition()).thenReturn(4);
		when(food.saturation()).thenReturn(6.0F);

		ClientFoodTooltipComponent client = new ClientFoodTooltipComponent(new FoodTooltipComponent(food));
		Font font = mock(Font.class);
		when(font.width("+4")).thenReturn(10);
		when(font.width("+6")).thenReturn(10);

		// 9 + 2 + 10 + 4 + 9 + 2 + 10
		assertEquals(46, client.getWidth(font));
	}
}
