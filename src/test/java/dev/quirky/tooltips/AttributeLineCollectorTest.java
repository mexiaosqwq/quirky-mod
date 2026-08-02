package dev.quirky.tooltips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import dev.quirky.QuirkyMod;
import dev.quirky.TestBootstrap;
import dev.quirky.tooltips.AttributeTooltipComponent.AttributeLine;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 属性行收集断言：钻石剑 = 7 攻击伤害 + 1.6 攻速；锋利 V = 10（7 + 3.0，26.2 实测公式）；
 * 钻石胸甲 = 8 护甲 + 2 韧性（26.2 实测韧性 2.0）；无属性物品返回空。
 */
class AttributeLineCollectorTest {
	private static TestEnchantmentRegistry enchantments;

	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
		TestBootstrap.bindItem(Items.DIAMOND_SWORD);
		TestBootstrap.bindItem(Items.DIAMOND_CHESTPLATE);
		TestBootstrap.bindItem(Items.IRON_CHESTPLATE);
		TestBootstrap.bindItem(Items.DIRT);
		TestBootstrap.bindItem(Items.COBBLESTONE);
		// 附魔注册表必须在 Bootstrap 之后创建（Enchantment 类初始化需要已引导注册表）
		enchantments = new TestEnchantmentRegistry();
	}

	@Test
	void diamondSword_showsAttackDamageAndSpeed() {
		List<AttributeLine> lines = AttributeLineCollector.collect(new ItemStack(Items.DIAMOND_SWORD), RegistryAccess.EMPTY);
		assertEquals(List.of(line("attack_damage", "7"), line("attack_speed", "1.6")), lines);
	}

	@Test
	void diamondSwordSharpnessFive_includesEnchantmentBonus() {
		ItemStack stack = enchantments.enchanted(Items.DIAMOND_SWORD, enchantments.holder(Enchantments.SHARPNESS), 5);
		List<AttributeLine> lines = AttributeLineCollector.collect(stack, enchantments.provider());
		assertEquals(List.of(line("attack_damage", "10"), line("attack_speed", "1.6")), lines);
	}

	@Test
	void diamondChestplate_showsArmorAndToughness() {
		List<AttributeLine> lines = AttributeLineCollector.collect(new ItemStack(Items.DIAMOND_CHESTPLATE), RegistryAccess.EMPTY);
		assertEquals(List.of(line("armor", "8"), line("toughness", "2")), lines);
	}

	@Test
	void knockbackResistance_showsWhenPresent() {
		// 下界合金甲绑定需要 damage_type/is_fire 标签（测试环境无数据包），改用合成修饰符验证该行
		ItemStack stack = new ItemStack(Items.IRON_CHESTPLATE);
		stack.set(
			DataComponents.ATTRIBUTE_MODIFIERS,
			ItemAttributeModifiers.builder()
				.add(
					Attributes.KNOCKBACK_RESISTANCE,
					new AttributeModifier(QuirkyMod.id("test_knockback_resistance"), 0.1, AttributeModifier.Operation.ADD_VALUE),
					EquipmentSlotGroup.ARMOR
				)
				.build()
		);
		assertEquals(List.of(line("knockback", "0.1")), AttributeLineCollector.collect(stack, RegistryAccess.EMPTY));
	}

	@Test
	void noAttributeItems_returnEmpty() {
		assertTrue(AttributeLineCollector.collect(new ItemStack(Items.DIRT), RegistryAccess.EMPTY).isEmpty());
		assertTrue(AttributeLineCollector.collect(new ItemStack(Items.COBBLESTONE), RegistryAccess.EMPTY).isEmpty());
	}

	@Test
	void emptyStack_returnsEmpty() {
		assertTrue(AttributeLineCollector.collect(ItemStack.EMPTY, RegistryAccess.EMPTY).isEmpty());
	}

	private static AttributeLine line(String icon, String text) {
		return new AttributeLine(QuirkyMod.id("gui/quirky/attribute/" + icon), text);
	}
}
