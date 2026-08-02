package dev.quirky.tooltips;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.quirky.TestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 附魔伤害公式断言，期望值按 26.2 实测公式计算：
 * 锋利 = 1.0 + 0.5*(等级-1)，亡灵杀手/节肢杀手 = 2.5*等级。
 */
class EnchantedDamageCalculatorTest {
	private static TestEnchantmentRegistry enchantments;

	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
		TestBootstrap.bindItem(Items.DIAMOND_SWORD);
		// 附魔注册表必须在 Bootstrap 之后创建（Enchantment 类初始化需要已引导注册表）
		enchantments = new TestEnchantmentRegistry();
	}

	@Test
	void sharpnessFive_addsThreePointZero() {
		ItemStack stack = enchantments.enchanted(Items.DIAMOND_SWORD, enchantments.holder(Enchantments.SHARPNESS), 5);
		// 7.0 + 3.0（规格 5.3 的 9.5 按 1.20 时代公式估算，26.2 实测锋利 V 为 +3.0）
		assertEquals(10.0F, EnchantedDamageCalculator.addEnchantmentDamage(7.0F, stack), 0.001F);
	}

	@Test
	void smiteFive_addsTwelvePointFive() {
		ItemStack stack = enchantments.enchanted(Items.DIAMOND_SWORD, enchantments.holder(Enchantments.SMITE), 5);
		assertEquals(19.5F, EnchantedDamageCalculator.addEnchantmentDamage(7.0F, stack), 0.001F);
	}

	@Test
	void baneOfArthropodsThree_addsSevenPointFive() {
		ItemStack stack = enchantments.enchanted(Items.DIAMOND_SWORD, enchantments.holder(Enchantments.BANE_OF_ARTHROPODS), 3);
		assertEquals(14.5F, EnchantedDamageCalculator.addEnchantmentDamage(7.0F, stack), 0.001F);
	}

	@Test
	void noEnchantment_returnsBase() {
		assertEquals(7.0F, EnchantedDamageCalculator.addEnchantmentDamage(7.0F, new ItemStack(Items.DIAMOND_SWORD)), 0.001F);
	}

	@Test
	void registryPath_matchesEntryPath() {
		ItemStack stack = enchantments.enchanted(Items.DIAMOND_SWORD, enchantments.holder(Enchantments.SHARPNESS), 5);
		float viaEntries = EnchantedDamageCalculator.addEnchantmentDamage(7.0F, stack);
		float viaRegistry = EnchantedDamageCalculator.addEnchantmentDamage(7.0F, stack, enchantments.provider());
		assertEquals(10.0F, viaRegistry, 0.001F);
		assertEquals(viaEntries, viaRegistry, 0.001F);
	}

	@Test
	void getItemEnchantmentLevel_readsStackLevel() {
		ItemStack stack = enchantments.enchanted(Items.DIAMOND_SWORD, enchantments.holder(Enchantments.SHARPNESS), 5);
		assertEquals(5, EnchantmentHelper.getItemEnchantmentLevel(enchantments.holder(Enchantments.SHARPNESS), stack));
	}
}
