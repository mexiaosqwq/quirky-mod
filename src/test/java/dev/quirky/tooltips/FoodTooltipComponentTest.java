package dev.quirky.tooltips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.quirky.TestBootstrap;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FoodTooltipComponentTest {

	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	@Test
	void oneArgumentConstructorKeepsBaseFoodData() {
		FoodProperties food = new FoodProperties(6, 9.6F, false);
		FoodTooltipComponent component = new FoodTooltipComponent(food);

		assertEquals(food, component.food());
		assertNull(component.consumable());
	}

	@Test
	void twoArgumentConstructorRetainsConsumable() {
		FoodProperties food = new FoodProperties(4, 6.0F, true);
		Consumable consumable = Consumables.defaultFood().consumeSeconds(0.8F).build();
		FoodTooltipComponent component = new FoodTooltipComponent(food, consumable);

		assertEquals(0.8F, component.consumable().consumeSeconds());
		assertTrue(component.food().canAlwaysEat());
	}
}
