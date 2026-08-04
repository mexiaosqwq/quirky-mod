package dev.quirky.mixin;

import dev.quirky.ModItems;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.2 菜单插入路径只问 {@code Slot.mayPlace}（base 返回 true），从不查
 * {@code container.canPlaceItem}——EnderChestContainerMixin 的 canPlaceItem 覆写
 * 对 GUI 无效（实测末影袋仍可拖进末影箱）。这里直接在 Slot.mayPlace 加守卫：
 * 容器是玩家末影箱时拒绝放入末影袋，覆盖点放/快移/拖拽/交换全部 GUI 路径。
 * 只拦截 {@code PlayerEnderChestContainer} 的槽位，不影响其他菜单。
 * NBT 加载走 setItem 不受影响。
 */
@Mixin(Slot.class)
public abstract class SlotPouchGuardMixin {

	@Shadow
	public Container container;

	@Inject(method = "mayPlace(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
	private void quirky$blockPouchInEnderChest(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (this.container instanceof PlayerEnderChestContainer && stack.is(ModItems.ENDER_POUCH)) {
			cir.setReturnValue(false);
		}
	}
}
