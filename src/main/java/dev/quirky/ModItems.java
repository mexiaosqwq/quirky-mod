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
	private static final ResourceKey<Item> TOTEM_OF_HOLDING_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("totem_of_holding"));

	public static final Item BOTTLED_CLOUD = new BottledCloudItem(
		new Item.Properties().stacksTo(1).craftRemainder(Items.GLASS_BOTTLE).usingConvertsTo(Items.GLASS_BOTTLE).setId(BOTTLED_CLOUD_ID)
	);

	public static final Item TOTEM_OF_HOLDING = new Item(
		new Item.Properties().stacksTo(1).setId(TOTEM_OF_HOLDING_ID)
	);

	private ModItems() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("bottled_cloud"), BOTTLED_CLOUD);
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("totem_of_holding"), TOTEM_OF_HOLDING);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register(output -> output.accept(BOTTLED_CLOUD));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register(output -> output.accept(TOTEM_OF_HOLDING));
	}
}
