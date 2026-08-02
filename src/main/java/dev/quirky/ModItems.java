package dev.quirky;

import dev.quirky.item.BottledCloudItem;
import dev.quirky.torch_arrow.TorchArrowItem;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class ModItems {
	private static final ResourceKey<Item> BOTTLED_CLOUD_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("bottled_cloud"));
	private static final ResourceKey<Item> TOTEM_OF_HOLDING_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("totem_of_holding"));
	private static final ResourceKey<Item> GOLD_BUTTON_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("gold_button"));
	private static final ResourceKey<Item> IRON_BUTTON_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("iron_button"));
	private static final ResourceKey<Item> OBSIDIAN_PRESSURE_PLATE_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("obsidian_pressure_plate"));
	private static final ResourceKey<Item> TORCH_ARROW_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("torch_arrow"));

	public static final Item BOTTLED_CLOUD = new BottledCloudItem(
		new Item.Properties().stacksTo(1).craftRemainder(Items.GLASS_BOTTLE).usingConvertsTo(Items.GLASS_BOTTLE).setId(BOTTLED_CLOUD_ID)
	);

	public static final Item TOTEM_OF_HOLDING = new Item(
		new Item.Properties().stacksTo(1).setId(TOTEM_OF_HOLDING_ID)
	);

	public static final Item GOLD_BUTTON = new BlockItem(
		ModBlocks.GOLD_BUTTON,
		new Item.Properties().setId(GOLD_BUTTON_ID)
	);

	public static final Item IRON_BUTTON = new BlockItem(
		ModBlocks.IRON_BUTTON,
		new Item.Properties().setId(IRON_BUTTON_ID)
	);

	public static final Item OBSIDIAN_PRESSURE_PLATE = new BlockItem(
		ModBlocks.OBSIDIAN_PRESSURE_PLATE,
		new Item.Properties().setId(OBSIDIAN_PRESSURE_PLATE_ID)
	public static final Item TORCH_ARROW = new TorchArrowItem(
		new Item.Properties().setId(TORCH_ARROW_ID)
	);

	private ModItems() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("bottled_cloud"), BOTTLED_CLOUD);
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("totem_of_holding"), TOTEM_OF_HOLDING);
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("gold_button"), GOLD_BUTTON);
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("iron_button"), IRON_BUTTON);
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("obsidian_pressure_plate"), OBSIDIAN_PRESSURE_PLATE);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register(output -> output.accept(BOTTLED_CLOUD));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS)
			.register(output -> {
				output.accept(GOLD_BUTTON);
				output.accept(IRON_BUTTON);
				output.accept(OBSIDIAN_PRESSURE_PLATE);
			});
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("torch_arrow"), TORCH_ARROW);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register(output -> output.accept(BOTTLED_CLOUD));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT)
			.register(output -> output.accept(TORCH_ARROW));
		// TOTEM_OF_HOLDING 不进创造页签：纯内部渲染素材（死亡点图腾实体显示用），玩家不应拿到
	}
}
