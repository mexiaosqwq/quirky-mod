package dev.quirky.quiver;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * 箭袋内容 DataComponent：值类型直接复用原版 {@link ItemContainerContents}
 * （自带 codec/stream codec，潜影盒 tooltip 已验证该类型），注册仿
 * {@code DataComponents.CONTAINER} 模式。
 *
 * <p>必须在 {@code QuirkyMod.onInitialize}（注册表冻结前）调用 {@link #register()}：
 * 构建对象本身不触碰注册表，类型仅在显式注册后才可被 ItemStack 携带。</p>
 */
public final class QuiverContents {

	private static final Identifier ID = Identifier.fromNamespaceAndPath("quirky", "quiver_contents");

	public static final DataComponentType<ItemContainerContents> TYPE = DataComponentType
		.<ItemContainerContents>builder()
		.persistent(ItemContainerContents.CODEC)
		.networkSynchronized(ItemContainerContents.STREAM_CODEC)
		.cacheEncoding()
		.build();

	private QuiverContents() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, ID, TYPE);
	}
}
