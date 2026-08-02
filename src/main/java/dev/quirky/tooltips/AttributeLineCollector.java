package dev.quirky.tooltips;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.quirky.QuirkyMod;
import dev.quirky.tooltips.AttributeTooltipComponent.AttributeLine;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * 收集物品 tooltip 的属性图标行：攻击伤害（含附魔加成）/攻击速度/护甲/韧性/击退抗性/移动速度，
 * 无对应修饰符的属性省略。数值语义对齐原版 tooltip：攻击伤害/攻速叠加玩家基础值（1.0/4.0），
 * 其余属性从 0 累加修饰符；26.2 武器/盔甲修饰符均为 ADD_VALUE，与原版显示数值一致。
 */
public final class AttributeLineCollector {
	/** 玩家基础攻击伤害（26.2 Player 属性表实测为 1.0，非 ATTACK_DAMAGE 注册表默认值 2.0） */
	private static final double PLAYER_BASE_ATTACK_DAMAGE = 1.0;
	/** 玩家基础攻击速度（Attributes.DEFAULT_ATTACK_SPEED） */
	private static final double PLAYER_BASE_ATTACK_SPEED = 4.0;

	// 26.2 GUI sprite 系统：sprite id 路径映射到 textures/gui/sprites/<path>.png
	// （atlases/gui.json 只扫描 gui/sprites 目录，id 不能带 gui/ 前缀）
	private static final Identifier ATTACK_DAMAGE_ICON = QuirkyMod.id("attribute/attack_damage");
	private static final Identifier ATTACK_SPEED_ICON = QuirkyMod.id("attribute/attack_speed");
	private static final Identifier ARMOR_ICON = QuirkyMod.id("attribute/armor");
	private static final Identifier TOUGHNESS_ICON = QuirkyMod.id("attribute/toughness");
	private static final Identifier KNOCKBACK_ICON = QuirkyMod.id("attribute/knockback");
	private static final Identifier MOVEMENT_ICON = QuirkyMod.id("attribute/movement");

	private AttributeLineCollector() {
	}

	/**
	 * @param registries 附魔注册表来源，透传给 {@link EnchantedDamageCalculator}；
	 *                   26.2 中 ENCHANTMENT 为数据包注册表，tooltip 调用路径无注册表访问时传
	 *                   {@code RegistryAccess.EMPTY}，附魔等级改从栈上附魔组件读取。
	 */
	public static List<AttributeLine> collect(ItemStack stack, HolderLookup.Provider registries) {
		if (stack.isEmpty()) {
			return List.of();
		}
		ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		List<AttributeLine> lines = new ArrayList<>();
		if (hasEntry(modifiers, Attributes.ATTACK_DAMAGE)) {
			float damage = EnchantedDamageCalculator.addEnchantmentDamage(
				(float) total(modifiers, Attributes.ATTACK_DAMAGE, PLAYER_BASE_ATTACK_DAMAGE), stack, registries
			);
			lines.add(new AttributeLine(ATTACK_DAMAGE_ICON, format(damage)));
		}
		if (hasEntry(modifiers, Attributes.ATTACK_SPEED)) {
			lines.add(new AttributeLine(ATTACK_SPEED_ICON, format(total(modifiers, Attributes.ATTACK_SPEED, PLAYER_BASE_ATTACK_SPEED))));
		}
		if (hasEntry(modifiers, Attributes.ARMOR)) {
			lines.add(new AttributeLine(ARMOR_ICON, format(total(modifiers, Attributes.ARMOR, 0.0))));
		}
		if (hasEntry(modifiers, Attributes.ARMOR_TOUGHNESS)) {
			lines.add(new AttributeLine(TOUGHNESS_ICON, format(total(modifiers, Attributes.ARMOR_TOUGHNESS, 0.0))));
		}
		if (hasEntry(modifiers, Attributes.KNOCKBACK_RESISTANCE)) {
			lines.add(new AttributeLine(KNOCKBACK_ICON, format(total(modifiers, Attributes.KNOCKBACK_RESISTANCE, 0.0))));
		}
		if (hasEntry(modifiers, Attributes.MOVEMENT_SPEED)) {
			lines.add(new AttributeLine(MOVEMENT_ICON, format(total(modifiers, Attributes.MOVEMENT_SPEED, 0.0))));
		}
		return lines;
	}

	private static boolean hasEntry(ItemAttributeModifiers modifiers, Holder<Attribute> attribute) {
		for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
			if (entry.attribute().is(attribute) && !isHidden(entry)) {
				return true;
			}
		}
		return false;
	}

	private static double total(ItemAttributeModifiers modifiers, Holder<Attribute> attribute, double base) {
		double value = base;
		for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
			if (!entry.attribute().is(attribute) || isHidden(entry)) {
				continue;
			}
			double amount = entry.modifier().amount();
			value += switch (entry.modifier().operation()) {
				case ADD_VALUE -> amount;
				case ADD_MULTIPLIED_BASE -> amount * base;
				case ADD_MULTIPLIED_TOTAL -> amount * value;
			};
		}
		return value;
	}

	private static boolean isHidden(ItemAttributeModifiers.Entry entry) {
		// Display.hidden() 为单例，可安全用引用比较
		return entry.display() == ItemAttributeModifiers.Display.hidden();
	}

	/** 整数不带小数（7、8、2），小数保留 1 位（1.6、9.5） */
	private static String format(double value) {
		if (value == Math.rint(value)) {
			return String.valueOf((long) value);
		}
		return String.format(Locale.ROOT, "%.1f", value);
	}
}
