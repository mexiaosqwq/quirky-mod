package dev.quirky;

import dev.quirky.item.BottledCloudItem;
<<<<<<< HEAD
<<<<<<< HEAD
import dev.quirky.item.ParrotEggItem;
=======
import dev.quirky.item.FishBaitItem;
import dev.quirky.item.SeedPouchItem;
>>>>>>> feat/batch-b-farm-fish
=======
import dev.quirky.item.EnderPouchItem;
import dev.quirky.item.PetWhistleItem;
import dev.quirky.item.QuiverItem;
>>>>>>> feat/batch-c-storage-pets
import dev.quirky.torch_arrow.TorchArrowItem;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.registry.FuelValueEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

public final class ModItems {
	private static final ResourceKey<Item> BOTTLED_CLOUD_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("bottled_cloud"));
	private static final ResourceKey<Item> TOTEM_OF_HOLDING_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("totem_of_holding"));
	private static final ResourceKey<Item> GOLD_BUTTON_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("gold_button"));
	private static final ResourceKey<Item> IRON_BUTTON_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("iron_button"));
	private static final ResourceKey<Item> OBSIDIAN_PRESSURE_PLATE_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("obsidian_pressure_plate"));
	private static final ResourceKey<Item> TORCH_ARROW_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("torch_arrow"));
	private static final ResourceKey<Item> WOODEN_HOPPER_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("wooden_hopper"));
<<<<<<< HEAD
<<<<<<< HEAD
	private static final ResourceKey<Item> PARROT_EGG_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("parrot_egg"));
=======
	private static final ResourceKey<Item> SEED_POUCH_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("seed_pouch"));
	private static final ResourceKey<Item> FISH_BAIT_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("fish_bait"));
>>>>>>> feat/batch-b-farm-fish
=======
	private static final ResourceKey<Item> QUIVER_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("quiver"));
	private static final ResourceKey<Item> ENDER_POUCH_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("ender_pouch"));
	private static final ResourceKey<Item> PET_WHISTLE_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("pet_whistle"));
>>>>>>> feat/batch-c-storage-pets

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
	);

	public static final Item TORCH_ARROW = new TorchArrowItem(
		new Item.Properties().setId(TORCH_ARROW_ID)
	);

	public static final Item WOODEN_HOPPER = new BlockItem(
		ModBlocks.WOODEN_HOPPER,
		new Item.Properties().setId(WOODEN_HOPPER_ID)
	);

<<<<<<< HEAD
<<<<<<< HEAD
	public static final Item PARROT_EGG = new ParrotEggItem(
		new Item.Properties().stacksTo(16).setId(PARROT_EGG_ID)
=======
	public static final Item SEED_POUCH = new SeedPouchItem(
		new Item.Properties().stacksTo(1).setId(SEED_POUCH_ID)
	);

	public static final Item FISH_BAIT = new FishBaitItem(
		new Item.Properties().stacksTo(16).setId(FISH_BAIT_ID)
>>>>>>> feat/batch-b-farm-fish
=======
	public static final Item QUIVER = new QuiverItem(
		new Item.Properties()
			.stacksTo(1)
			// 皮革染色：挂默认 DYED_COLOR 组件即获得炼药锅水洗褪色 + 染料合成染色
			.component(DataComponents.DYED_COLOR, new DyedItemColor(DyedItemColor.LEATHER_COLOR))
			.setId(QUIVER_ID)
	);

	public static final Item ENDER_POUCH = new EnderPouchItem(
		new Item.Properties().stacksTo(1).setId(ENDER_POUCH_ID)
	);

	public static final Item PET_WHISTLE = new PetWhistleItem(
		new Item.Properties().stacksTo(1).setId(PET_WHISTLE_ID)
>>>>>>> feat/batch-c-storage-pets
	);

	private ModItems() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("bottled_cloud"), BOTTLED_CLOUD);
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("totem_of_holding"), TOTEM_OF_HOLDING);
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("gold_button"), GOLD_BUTTON);
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("iron_button"), IRON_BUTTON);
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("obsidian_pressure_plate"), OBSIDIAN_PRESSURE_PLATE);
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("torch_arrow"), TORCH_ARROW);
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("wooden_hopper"), WOODEN_HOPPER);
<<<<<<< HEAD
<<<<<<< HEAD
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("parrot_egg"), PARROT_EGG);
=======
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("seed_pouch"), SEED_POUCH);
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("fish_bait"), FISH_BAIT);
>>>>>>> feat/batch-b-farm-fish
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register(output -> output.accept(BOTTLED_CLOUD));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register(output -> output.accept(SEED_POUCH));
=======
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("quiver"), QUIVER);
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("ender_pouch"), ENDER_POUCH);
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("pet_whistle"), PET_WHISTLE);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register(output -> {
				output.accept(BOTTLED_CLOUD);
				output.accept(QUIVER);
				output.accept(ENDER_POUCH);
				output.accept(PET_WHISTLE);
			});
>>>>>>> feat/batch-c-storage-pets
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS)
			.register(output -> {
				output.accept(GOLD_BUTTON);
				output.accept(IRON_BUTTON);
				output.accept(OBSIDIAN_PRESSURE_PLATE);
				output.accept(WOODEN_HOPPER);
			});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT)
<<<<<<< HEAD
			.register(output -> output.accept(TORCH_ARROW));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register(output -> output.accept(PARROT_EGG));
=======
			.register(output -> {
				output.accept(TORCH_ARROW);
				output.accept(FISH_BAIT);
			});
>>>>>>> feat/batch-b-farm-fish
		// TOTEM_OF_HOLDING 不进创造页签：纯内部渲染素材（死亡点图腾实体显示用），玩家不应拿到
		// 木漏斗可作熔炉燃料（300 tick = 15 秒）：26.2 没有 DataComponents.FUEL，燃料改由服务端
		// FuelValues 数据驱动，Fabric 通过 FuelValueEvents.BUILD 事件向 Builder 追加条目。
		FuelValueEvents.BUILD.register((builder, context) -> builder.add(WOODEN_HOPPER, 300));
	}
}
