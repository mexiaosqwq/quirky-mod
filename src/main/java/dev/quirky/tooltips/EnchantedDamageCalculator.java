package dev.quirky.tooltips;

import java.util.Optional;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

/**
 * 附魔伤害加成计算：锋利/亡灵杀手/节肢杀手按 26.2 原版公式计入攻击伤害。
 *
 * <p>公式（实测 26.2 Enchantments bootstrap 源码）：
 * 锋利 = DAMAGE + AddValue(LevelBasedValue.perLevel(1.0F, 0.5F)) → 1.0 + 0.5*(等级-1)（锋利 V = +3.0）；
 * 亡灵杀手/节肢杀手 = DAMAGE + AddValue(LevelBasedValue.perLevel(2.5F)) → 2.5*等级。
 */
public final class EnchantedDamageCalculator {
	private EnchantedDamageCalculator() {
	}

	public static float addEnchantmentDamage(float base, ItemStack stack) {
		return addEnchantmentDamage(base, stack, null);
	}

	/**
	 * @param registries 可选附魔注册表来源：提供含 ENCHANTMENT 的注册表时走
	 *                   {@link EnchantmentHelper#getItemEnchantmentLevel} 注册表路径；
	 *                   tooltip 调用路径在 26.2 中拿不到数据包注册表（ENCHANTMENT 不在
	 *                   BuiltInRegistries 内），回退为按栈上附魔条目的 ResourceKey 匹配，
	 *                   两者读取的是同一份 DataComponents.ENCHANTMENTS，结果等价（有测试断言）。
	 */
	public static float addEnchantmentDamage(float base, ItemStack stack, HolderLookup.@Nullable Provider registries) {
		return base + bonus(stack, registries);
	}

	private static float bonus(ItemStack stack, HolderLookup.@Nullable Provider registries) {
		ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		if (enchantments.isEmpty()) {
			return 0.0F;
		}
		Optional<? extends HolderLookup.RegistryLookup<Enchantment>> lookup;
		if (registries == null) {
			lookup = Optional.empty();
		} else {
			lookup = registries.lookup(Registries.ENCHANTMENT);
		}
		if (lookup.isPresent()) {
			HolderLookup.RegistryLookup<Enchantment> registry = lookup.get();
			return sharpnessBonus(EnchantmentHelper.getItemEnchantmentLevel(registry.getOrThrow(Enchantments.SHARPNESS), stack))
				+ perLevelBonus(EnchantmentHelper.getItemEnchantmentLevel(registry.getOrThrow(Enchantments.SMITE), stack))
				+ perLevelBonus(EnchantmentHelper.getItemEnchantmentLevel(registry.getOrThrow(Enchantments.BANE_OF_ARTHROPODS), stack));
		}
		float bonus = 0.0F;
		for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
			if (entry.getKey().is(Enchantments.SHARPNESS)) {
				bonus += sharpnessBonus(entry.getIntValue());
			} else if (entry.getKey().is(Enchantments.SMITE) || entry.getKey().is(Enchantments.BANE_OF_ARTHROPODS)) {
				bonus += perLevelBonus(entry.getIntValue());
			}
		}
		return bonus;
	}

	private static float sharpnessBonus(int level) {
		return 1.0F + 0.5F * (level - 1);
	}

	private static float perLevelBonus(int level) {
		return 2.5F * level;
	}
}
