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
 * GUI 放入（点放/快移/拖拽）都经过 canPlaceItem；NBT 加载走 setItem 不受影响。
 * 该容器被末影袋菜单与原版末影箱方块共享，两处同时生效（语义一致：袋子不进末影箱）。
 */
@Mixin(PlayerEnderChestContainer.class)
public abstract class EnderChestContainerMixin {

	public boolean canPlaceItem(int slot, ItemStack stack) {
		return !stack.is(ModItems.ENDER_POUCH);
	}
}
