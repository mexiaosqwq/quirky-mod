package dev.quirky.mixin;

import java.util.function.Predicate;

import dev.quirky.ModItems;
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
 * 自动抽箭（getProjectile RETURN）：弩/弓优先用箭袋弹药（玩家主动装入 = 意图使用），
 * 遍历背包箭袋找第一个含匹配弹药的箭袋，返回该组副本并记录来源到 tracker，
 * 供 {@link ProjectileWeaponItemAmmoMixin} 在 useAmmo 时从箭袋组件扣减。
 *
 * <p>优先级：手持弹药 > 箭袋弹药 > 散装弹药 > 创造兜底。
 * 多个箭袋按背包槽位顺序，靠前的先用；耗尽后下次 getProjectile 自动跳到下一个箭袋（每次重新解析）。</p>
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

	@Inject(method = "getProjectile(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"), cancellable = true)
	private void quirky$supplyFromQuiver(ItemStack heldWeapon, CallbackInfoReturnable<ItemStack> cir) {
		// 每次调用先清旧记录（上次射击残留）
		this.quirky$clearQuiverAmmo();
		ItemStack returnValue = cir.getReturnValue();
		// 仅对弹射武器处理（第一个 return EMPTY 是非武器场景，跳过）
		if (!(heldWeapon.getItem() instanceof ProjectileWeaponItem weapon)) {
			return;
		}
		Player self = (Player) (Object) this;
		// 箭袋优先：玩家主动装进箭袋 = 意图使用该弹药，弩/弓优先用箭袋（含烟花火箭），
		// 原版按背包槽位找散装弹药，看不到箭袋内部（2026-08-05 用户反馈：箭袋第一格烟花火箭但弩用散装箭）。
		// 手持弹药仍最优先（原版 getHeldProjectile 先跑，返回值非空时这里只查不覆盖）。
		// 用 getSupportedHeldProjectiles 匹配：弩支持手持烟花火箭（ARROW_OR_FIREWORK），
		// 而 getAllSupportedProjectiles 只认箭（ARROW_ONLY，原版自动装填仅箭）——
		// 否则箭袋里的烟花火箭永远匹配不上（2026-08-05 实测）。弓两者都是箭，不受影响。
		Predicate<ItemStack> supported = weapon.getSupportedHeldProjectiles();
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
			// 手持弹药 > 箭袋弹药 > 散装弹药：非手持时用箭袋弹药替换原版结果
			if (!returnValue.isEmpty()) {
				// 原版已找到弹药：仅当它不是手持弹药（玩家当前手持）时才用箭袋替换
				if (isHeldAmmo(self, returnValue)) {
					return;
				}
			}
			this.quirky$setQuiverAmmo(match.stack(), slot, match.groupIndex());
			cir.setReturnValue(match.stack());
			return;
		}
		// 箭袋无匹配：原版结果（散装/创造兜底）保持不变
	}

	/** 返回值是否为玩家手持的弹药（主手/副手槽位中的同一物品）。 */
	@Unique
	private static boolean isHeldAmmo(Player self, ItemStack ammo) {
		if (ammo.isEmpty()) {
			return false;
		}
		return self.getMainHandItem().is(ammo.getItem()) || self.getOffhandItem().is(ammo.getItem());
	}
}
