package dev.quirky;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public final class TestBootstrap {
	private static boolean initialized;

	private TestBootstrap() {
	}

	public static void boot() {
		if (initialized) {
			return;
		}
		SharedConstants.tryDetectVersion();
		// BuiltInRegistries requires the guard before ModItems can register pre-freeze items.
		setBootstrapped(true);
		BuiltInRegistries.REGISTRY.keySet();
		ModBlocks.register();
		ModItems.register();
		ModEntities.register(); // EntityType 构造会创建 intrusive holder，必须在注册窗口内加载
		setBootstrapped(false);
		Bootstrap.bootStrap();
		RegistryAccess.Frozen registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
		// Vanilla component initialization needs datapack registries, so bind only the items under test.
		bindInitializer(ModItems.BOTTLED_CLOUD, registries);
		bindInitializer(Items.MELON_SLICE, registries);
		bindMinimalComponents(Items.MELON_SEEDS);
		bindMinimalComponents(Items.GLASS_BOTTLE);
		initialized = true;
	}

	/**
	 * Binds vanilla component initializers (equippable, food, etc.) for an
	 * additional item under test, e.g. armor used by equip-swap tests.
	 */
	public static void bindItem(Item item) {
		RegistryAccess.Frozen registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
		bindInitializer(item, registries);
	}

	@SuppressWarnings("deprecation")
	private static void bindInitializer(Item item, HolderLookup.Provider context) {
		ResourceKey<Item> key = item.builtInRegistryHolder().key();
		DataComponentMap.Builder builder = DataComponentMap.builder();
		try {
			Field field = DataComponentInitializers.class.getDeclaredField("initializers");
			field.setAccessible(true);
			List<?> initializers = (List<?>) field.get(BuiltInRegistries.DATA_COMPONENT_INITIALIZERS);
			for (Object entry : initializers) {
				Field keyField = entry.getClass().getDeclaredField("key");
				keyField.setAccessible(true);
				if (key.equals(keyField.get(entry))) {
					Method run = entry.getClass().getDeclaredMethod("run", DataComponentMap.Builder.class, HolderLookup.Provider.class);
					run.setAccessible(true);
					run.invoke(entry, builder, context);
				}
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Unable to bind item component initializers", e);
		}
		item.builtInRegistryHolder().bindComponents(builder.build());
	}

	@SuppressWarnings("deprecation")
	private static void bindMinimalComponents(Item item) {
		item.builtInRegistryHolder().bindComponents(
			DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 16).build()
		);
	}

	private static void setBootstrapped(boolean value) {
		try {
			Field field = Bootstrap.class.getDeclaredField("isBootstrapped");
			field.setAccessible(true);
			field.setBoolean(null, value);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Unable to mark Bootstrap as bootstrapped", e);
		}
	}
}
