package dev.quirky.tooltips;

import java.util.List;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * 测试用微型附魔注册表：26.2 中 ENCHANTMENT 是数据包注册表（不在 BuiltInRegistries），
 * 测试环境无法从世界注册表取引用 holder，这里手工注册锋利/亡灵杀手/节肢杀手并产出引用。
 */
final class TestEnchantmentRegistry {
	private final Registry<Enchantment> registry;

	TestEnchantmentRegistry() {
		registry = new MappedRegistry<>(Registries.ENCHANTMENT, Lifecycle.stable());
		register(Enchantments.SHARPNESS);
		register(Enchantments.SMITE);
		register(Enchantments.BANE_OF_ARTHROPODS);
	}

	private void register(ResourceKey<Enchantment> key) {
		Enchantment.EnchantmentDefinition definition = Enchantment.definition(
			HolderSet.direct(), HolderSet.direct(), 10, 5,
			Enchantment.dynamicCost(1, 11), Enchantment.dynamicCost(21, 11), 1, EquipmentSlotGroup.MAINHAND
		);
		Registry.register(registry, key, Enchantment.enchantment(definition).build(key.identifier()));
	}

	Holder<Enchantment> holder(ResourceKey<Enchantment> key) {
		return registry.getOrThrow(key);
	}

	/** 含 ENCHANTMENT 注册表的 HolderLookup.Provider，供注册表路径测试 */
	HolderLookup.Provider provider() {
		return new RegistryAccess.ImmutableRegistryAccess(List.of(registry)).freeze();
	}

	ItemStack enchanted(Item item, Holder<Enchantment> enchantment, int level) {
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		mutable.set(enchantment, level);
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
		return stack;
	}
}
