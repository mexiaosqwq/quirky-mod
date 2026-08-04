package dev.quirky.quiver;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 自动抽箭追踪器（{@link Player} 通过 mixin 实现此接口）。
 *
 * <p>{@link dev.quirky.mixin.PlayerQuiverAmmoMixin} 在 {@code Player.getProjectile} RETURN
 * 注入：原版找不到散装弹药（返回 EMPTY）时，遍历背包箭袋找匹配弹药，返回该组的副本，
 * 并把 (副本引用, 箭袋所在背包槽位, 组索引) 记录到本 tracker。
 *
 * <p>{@link dev.quirky.mixin.ProjectileWeaponItemAmmoMixin} 在
 * {@code ProjectileWeaponItem.useAmmo} HEAD 注入：若 {@code projectile} 引用等于
 * tracker 记录的副本，则从对应箭袋组件扣减弹药（而非让原版 split 作用于临时副本）。
 *
 * <p>tracker 在每次 getProjectile 调用时覆写（每次射击重新解析箭袋来源）。
 */
public interface QuiverAmmoSource {
	/** 记录上一次 getProjectile 从箭袋取出的弹药副本及其来源。 */
	void quirky$setQuiverAmmo(ItemStack copy, int quiverInvSlot, int groupIndex);

	/** 取出当前 tracker 记录。返回 null 表示无箭袋来源（散装或空）。 */
	QuiverAmmoRef quirky$getQuiverAmmo();

	/** 清空 tracker（每次 getProjectile 调用前/射击完成后）。 */
	void quirky$clearQuiverAmmo();

	/** tracker 数据载体。 */
	record QuiverAmmoRef(ItemStack copy, int quiverInvSlot, int groupIndex) {
	}
}
