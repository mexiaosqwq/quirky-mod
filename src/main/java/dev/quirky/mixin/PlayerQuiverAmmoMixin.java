package dev.quirky.mixin;

import java.util.function.Predicate;

import dev.quirky.ModItems;
import dev.quirky.config.QuirkyConfigHolder;
import dev.quirky.item.QuiverItem;
import dev.quirky.quiver.QuiverAmmoSource;
import dev.quirky.quiver.QuiverAmmoSource.QuiverAmmoRef;
import dev.quirky.quiver.QuiverContents;
import dev.quirky.quiver.QuiverLogic;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.ItemContainerContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 自动抽箭（getProjectile RETURN）：原版找不到散装弹药（返回 EMPTY）时，
 * 遍历背包箭袋找第一个含匹配弹药的箭袋，返回该组副本并记录来源到 tracker，
 * 供 {@link ProjectileWeaponItemAmmoMixin} 在 useAmmo 时从箭袋组件扣减。
 *
 * <p>散装优先（原版逻辑先跑），箭袋作备用。多个箭袋按背包槽位顺序，靠前的先用；
 * 耗尽后下次 getProjectile 自动跳到下一个箭袋（每次重新解析）。</p>
 *
 * <p>注入点：getProjectile 所有 RETURN。首个 return（非 ProjectileWeaponItem → EMPTY）
 * 因 heldWeapon 非武器也会进入，但 instanceof 守卫跳过。其余非空返回值守卫跳过。</p>
 */
@Mixin(Player.class)
public abstract class PlayerQuiverAmmoMixin implements QuiverAmmoSource {

	@Unique
	private QuiverAmmoRef quirky$ammoRef;

	@Override
	@Unique
	public void quirky$setQuiverAmmo(ItemStack copy, int quiverInvSlot, int groupIndex) {
		this.quirky$ammoRef = new QuiverAmmoRef(copy, quiverInvSlot, groupIndex);
	}

	@Override
	@Unique
	public QuiverAmmoRef quirky$getQuiverAmmo() {
		return this.quirky$ammoRef;
	}

	@Override
	@Unique
	public void quirky$clearQuiverAmmo() {
		this.quirky$ammoRef = null;
	}

	@Inject(method = "getProjectile(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
	private void quirky$supplyFromQuiver(ItemStack heldWeapon, CallbackInfoReturnable<ItemStack> cir) {
		// 每次调用先清旧记录（上次射击残留）
		this.quirky$clearQuiverAmmo();
		if (!QuirkyConfigHolder.get().quiverEnabled) {
			return;
		}
		ItemStack returnValue = cir.getReturnValue();
		// 散装优先：原版已找到非空弹药则不碰箭袋
		if (!returnValue.isEmpty()) {
			return;
		}
		// 仅对弹射武器处理（第一个 return EMPTY 是非武器场景，跳过）
		if (!(heldWeapon.getItem() instanceof ProjectileWeaponItem weapon)) {
			return;
		}
		Player self = (Player) (Object) this;
		// 创造模式原版会返回 ARROW，不会进到这里；这里兜底也跳过无限材料
		if (self.hasInfiniteMaterials()) {
			return;
		}
		Predicate<ItemStack> supported = weapon.getAllSupportedProjectiles();
		// 遍历背包槽位，找第一个含匹配弹药的箭袋
		for (int slot = 0; slot < self.getInventory().getContainerSize(); slot++) {
			ItemStack stack = self.getInventory().getItem(slot);
			if (!stack.is(ModItems.QUIVER)) {
				continue;
			}
			ItemContainerContents contents = stack.getOrDefault(QuiverContents.TYPE, ItemContainerContents.EMPTY);
			QuiverLogic.AmmoMatch match = QuiverLogic.findAmmo(contents, supported);
			if (!match.found()) {
				continue;
			}
			// 命中：返回该组副本，记录来源（箭袋槽位 + 组索引）
			this.quirky$setQuiverAmmo(match.stack(), slot, match.groupIndex());
			cir.setReturnValue(match.stack());
			return;
		}
	}
}
