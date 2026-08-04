package dev.quirky.mixin;

import dev.quirky.ModItems;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 禁止末影袋放进末影箱库存（袋子不能装进它自己打开的容器）。
 *
 * <p>{@code Container.canPlaceItem} 是接口 default 方法（返回 true），
 * PlayerEnderChestContainer 未覆写——mixin 向目标类添加同名方法即构成覆写。
 * 注意：26.2 菜单 GUI 放入（点放/快移/拖拽）只查 {@code Slot.mayPlace}，不查 canPlaceItem，
 * GUI 路径由 {@code SlotPouchGuardMixin} 负责；本 mixin 守卫其余走 canPlaceItem 的路径（如漏斗）。
 * NBT 加载走 setItem 不受影响。该容器被末影袋菜单与原版末影箱方块共享，两处同时生效。
 */
@Mixin(PlayerEnderChestContainer.class)
public abstract class EnderChestContainerMixin {

	public boolean canPlaceItem(int slot, ItemStack stack) {
		return !stack.is(ModItems.ENDER_POUCH);
	}
}
