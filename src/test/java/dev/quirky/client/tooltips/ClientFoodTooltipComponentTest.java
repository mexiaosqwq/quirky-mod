package dev.quirky.client.tooltips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import dev.quirky.TestBootstrap;
import dev.quirky.tooltips.FoodTooltipComponent;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.TeleportRandomlyConsumeEffect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClientFoodTooltipComponentTest {

	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	@Test
	void ordinaryFoodStaysAtTheBaseRow() {
		FoodProperties food = new FoodProperties(4, 2.4F, false);
		FoodTooltipComponent component = new FoodTooltipComponent(food, Consumables.defaultFood().build());
		ClientFoodTooltipComponent client = new ClientFoodTooltipComponent(component);

		assertEquals(0, ClientFoodTooltipComponent.detailLines(component, 20.0F).size());
		assertEquals(16, client.getHeight(mock(Font.class)));
	}

	@Test
	void conditionalFactsAddRowsOnlyWhenPresent() {
		FoodProperties food = new FoodProperties(4, 6.0F, true);
		Consumable consumable = Consumable.builder().consumeSeconds(0.8F).build();
		FoodTooltipComponent component = new FoodTooltipComponent(food, consumable);
		ClientFoodTooltipComponent client = new ClientFoodTooltipComponent(component);

		assertEquals(2, ClientFoodTooltipComponent.detailLines(component, 20.0F).size());
		assertEquals(48, client.getHeight(mock(Font.class)));
	}

	@Test
	void defaultConsumeTimeIsNotPrinted() {
		FoodProperties food = new FoodProperties(4, 6.0F, false);
		Consumable consumable = Consumable.builder()
			.consumeSeconds(Consumable.DEFAULT_CONSUME_SECONDS)
			.build();
		FoodTooltipComponent component = new FoodTooltipComponent(food, consumable);

		assertEquals(0, ClientFoodTooltipComponent.detailLines(component, 20.0F).size());
	}

	@Test
	void statusEffectLineCarriesIconAndEffectName() {
		MobEffectInstance effect = new MobEffectInstance(MobEffects.REGENERATION, 200);
		ApplyStatusEffectsConsumeEffect consumeEffect = new ApplyStatusEffectsConsumeEffect(effect, 1.0F);
		Consumable consumable = Consumable.builder().onConsume(consumeEffect).build();
		FoodTooltipComponent component = new FoodTooltipComponent(new FoodProperties(4, 6.0F, false), consumable);

		List<ClientFoodTooltipComponent.DetailLine> lines = ClientFoodTooltipComponent.detailLines(component, 20.0F);
		assertEquals(1, lines.size());
		assertNotNull(lines.get(0).icon());
		assertEquals("tooltip.quirky.food.effect", ((TranslatableContents) lines.get(0).text().getContents()).getKey());
	}

	@Test
	void probabilitySuffixOnlyWhenBelowOne() {
		MobEffectInstance effect = new MobEffectInstance(MobEffects.POISON, 100);
		ApplyStatusEffectsConsumeEffect consumeEffect = new ApplyStatusEffectsConsumeEffect(effect, 0.5F);
		Consumable consumable = Consumable.builder().onConsume(consumeEffect).build();
		FoodTooltipComponent component = new FoodTooltipComponent(new FoodProperties(4, 6.0F, false), consumable);

		List<ClientFoodTooltipComponent.DetailLine> lines = ClientFoodTooltipComponent.detailLines(component, 20.0F);
		assertEquals(1, lines.size());
		assertEquals(
			"tooltip.quirky.food.effect_probability",
			((TranslatableContents) lines.get(0).text().getContents()).getKey()
		);
	}

	@Test
	void harmfulAndBeneficialEffectsUseDistinctColors() {
		Consumable beneficial = Consumable.builder()
			.onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.REGENERATION, 200)))
			.build();
		Consumable harmful = Consumable.builder()
			.onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.POISON, 100)))
			.build();

		int positiveColor = ClientFoodTooltipComponent.detailLines(
			new FoodTooltipComponent(new FoodProperties(4, 6.0F, false), beneficial), 20.0F
		).get(0).color();
		int negativeColor = ClientFoodTooltipComponent.detailLines(
			new FoodTooltipComponent(new FoodProperties(4, 6.0F, false), harmful), 20.0F
		).get(0).color();

		assertNotEquals(positiveColor, negativeColor);
	}

	@Test
	void clearAllEffectsProducesSingleLine() {
		Consumable consumable = Consumable.builder().onConsume(ClearAllStatusEffectsConsumeEffect.INSTANCE).build();
		FoodTooltipComponent component = new FoodTooltipComponent(new FoodProperties(4, 6.0F, false), consumable);

		List<ClientFoodTooltipComponent.DetailLine> lines = ClientFoodTooltipComponent.detailLines(component, 20.0F);
		assertEquals(1, lines.size());
		assertEquals(
			"tooltip.quirky.food.clear_all_effects",
			((TranslatableContents) lines.get(0).text().getContents()).getKey()
		);
	}

	@Test
	void randomTeleportProducesSingleLine() {
		Consumable consumable = Consumable.builder().onConsume(new TeleportRandomlyConsumeEffect()).build();
		FoodTooltipComponent component = new FoodTooltipComponent(new FoodProperties(4, 6.0F, false), consumable);

		List<ClientFoodTooltipComponent.DetailLine> lines = ClientFoodTooltipComponent.detailLines(component, 20.0F);
		assertEquals(1, lines.size());
		assertEquals(
			"tooltip.quirky.food.random_teleport",
			((TranslatableContents) lines.get(0).text().getContents()).getKey()
		);
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
