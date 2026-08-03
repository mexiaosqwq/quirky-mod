package dev.quirky.ding;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 弓箭命中叮声的纯判定逻辑（无实体依赖，可单测）。
 * 由 {@code ArrowDingMixin} 在命中后调用，把"目标类型 + 暴击 + 击杀"映射为播放参数。
 */
public final class ArrowDingLogic {

	/** 命中目标分类：金属甲（铁/金/下界合金）与无甲走不同音色；盾挡/非生物不响。 */
	public enum TargetKind {
		LIVING_UNARMORED,
		LIVING_METAL_ARMOR,
		SHIELD_BLOCKED,
		NON_LIVING
	}

	/** 播放参数：音效、音高、最终音量（配置值 clamp 后乘击杀加成）。 */
	public record Ding(Holder<SoundEvent> sound, float pitch, float volume) {
	}

	private static final float KILL_VOLUME_BOOST = 1.2F;

	private ArrowDingLogic() {
	}

	public static Optional<Ding> resolve(TargetKind kind, boolean crit, boolean kill, float configVolume) {
		return switch (kind) {
			case SHIELD_BLOCKED, NON_LIVING -> Optional.empty();
			case LIVING_METAL_ARMOR -> Optional.of(
				new Ding(SoundEvents.ARMOR_EQUIP_IRON, 1.2F, finalVolume(configVolume, kill))
			);
			case LIVING_UNARMORED -> Optional.of(
				new Ding(SoundEvents.NOTE_BLOCK_BELL, crit ? 1.8F : 1.5F, finalVolume(configVolume, kill))
			);
		};
	}

	private static float finalVolume(float configVolume, boolean kill) {
		float volume = Math.clamp(configVolume, 0.0F, 1.0F);
		return kill ? volume * KILL_VOLUME_BOOST : volume;
	}

	/**
	 * 目标是否穿金属甲（铁/金/下界合金任一盔甲件）——决定叮声换金属哢声。
	 */
	public static boolean hasMetalArmor(List<ItemStack> armorItems) {
		for (ItemStack stack : armorItems) {
			if (stack.is(Items.IRON_HELMET) || stack.is(Items.IRON_CHESTPLATE)
				|| stack.is(Items.IRON_LEGGINGS) || stack.is(Items.IRON_BOOTS)
				|| stack.is(Items.GOLDEN_HELMET) || stack.is(Items.GOLDEN_CHESTPLATE)
				|| stack.is(Items.GOLDEN_LEGGINGS) || stack.is(Items.GOLDEN_BOOTS)
				|| stack.is(Items.NETHERITE_HELMET) || stack.is(Items.NETHERITE_CHESTPLATE)
				|| stack.is(Items.NETHERITE_LEGGINGS) || stack.is(Items.NETHERITE_BOOTS)) {
				return true;
			}
		}
		return false;
	}
}
