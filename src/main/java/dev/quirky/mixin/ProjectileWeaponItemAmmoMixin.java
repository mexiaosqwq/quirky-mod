package dev.quirky.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.quirky.ModItems;
import dev.quirky.quiver.QuiverAmmoSource;
import dev.quirky.quiver.QuiverAmmoSource.QuiverAmmoRef;
import dev.quirky.quiver.QuiverContents;
import dev.quirky.quiver.QuiverLogic;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * 自动抽箭扣减（useAmmo HEAD）：若本次 useAmmo 的 projectile 引用等于
 * {@link PlayerQuiverAmmoMixin} 在 getProjectile 记录的箭袋副本，则从箭袋组件
 * 扣减弹药，复刻原版 useAmmo 的返回值语义（普通箭返回 split 后的 count=1 栈，
 * 无限附魔返回 INTANGIBLE 副本不消费源）。
 *
 * <p>仅处理 i==0 那发（原版 draw 传原引用）：多重射击 i>0 用 projectile.copy()，
 * 引用不等 → 不进本分支 → 走原版 INTANGIBLE（与原版一致，多重射击只扣 1）。</p>
 *
 * <p>注入点：{@code ProjectileWeaponItem.useAmmo}（protected static）HEAD cancellable。
 * 描述符：{@code (Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;
 * Lnet/minecraft/world/entity/LivingEntity;Z)Lnet/minecraft/world/item/ItemStack;}。</p>
 */
@Mixin(ProjectileWeaponItem.class)
public abstract class ProjectileWeaponItemAmmoMixin {

	@Inject(
		method = "useAmmo(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Z)Lnet/minecraft/world/item/ItemStack;",
		at = @At("HEAD"),
		cancellable = true
	)
	private static void quirky$useQuiverAmmo(
		ItemStack weapon,
		ItemStack projectile,
		LivingEntity holder,
		boolean forceInfinite,
		CallbackInfoReturnable<ItemStack> cir
	) {
		if (!(holder instanceof Player player)) {
			return;
		}
		QuiverAmmoRef ref = ((QuiverAmmoSource) player).quirky$getQuiverAmmo();
		if (ref == null) {
			return; // 散装弹药，走原版
		}
		// 引用相等才接管（仅 getProjectile 返回的箭袋副本，draw i==0）
		if (projectile != ref.copy()) {
			return;
		}
		// 定位箭袋 ItemStack（背包槽位可能已变动，但同一次 releaseUsing 内不会）
		ItemStack quiverStack = player.getInventory().getItem(ref.quiverInvSlot());
		if (!quiverStack.is(ModItems.QUIVER)) {
			return; // 槽位内容变了（极端情况），放弃接管走原版
		}
		// 复刻原版 useAmmo 逻辑，但消费源改为箭袋组件
		int ammoToUse = !forceInfinite && !player.hasInfiniteMaterials() && holder.level() instanceof ServerLevel serverLevel
			? EnchantmentHelper.processAmmoUse(serverLevel, weapon, projectile, 1)
			: 0;
		if (ammoToUse > projectile.getCount()) {
			cir.setReturnValue(ItemStack.EMPTY);
			return;
		}
		if (ammoToUse == 0) {
			// 无限附魔：不消费，返回 INTANGIBLE 副本（原版语义）
			ItemStack copy = projectile.copyWithCount(1);
			copy.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
			cir.setReturnValue(copy);
			return;
		}
		// 从箭袋扣减 ammoToUse 支
		ItemContainerContents contents = quiverStack.getOrDefault(QuiverContents.TYPE, ItemContainerContents.EMPTY);
		ItemContainerContents after = QuiverLogic.decrementGroup(contents, ref.groupIndex(), ammoToUse);
		quiverStack.set(QuiverContents.TYPE, after);
		// 返回消费的那支（count=ammoToUse，与原版 split 返回一致）
		cir.setReturnValue(projectile.copyWithCount(ammoToUse));
	}
}
