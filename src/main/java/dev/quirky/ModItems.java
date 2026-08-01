package dev.quirky;

import dev.quirky.item.BottledCloudItem;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class ModItems {
	private static final ResourceKey<Item> BOTTLED_CLOUD_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("bottled_cloud"));

	public static final Item BOTTLED_CLOUD = new BottledCloudItem(
		new Item.Properties().stacksTo(1).craftRemainder(Items.GLASS_BOTTLE).setId(BOTTLED_CLOUD_ID)
	);

	private ModItems() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("bottled_cloud"), BOTTLED_CLOUD);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register(output -> output.accept(BOTTLED_CLOUD));
	}
}
