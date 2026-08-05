package dev.quirky;

import dev.quirky.item.BoomerangItem;
import dev.quirky.item.BottledCloudItem;
import dev.quirky.item.EnderPouchItem;
import dev.quirky.item.FishBaitItem;
import dev.quirky.item.ParrotEggItem;
import dev.quirky.item.PetWhistleItem;
import dev.quirky.item.QuiverItem;
import dev.quirky.item.RopeItem;
import dev.quirky.item.SeedPouchItem;
import dev.quirky.torch_arrow.TorchArrowItem;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput;
import net.fabricmc.fabric.api.registry.FuelValueEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Weapon;

public final class ModItems {
	private static final ResourceKey<Item> BOTTLED_CLOUD_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("bottled_cloud"));
	private static final ResourceKey<Item> TOTEM_OF_HOLDING_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("totem_of_holding"));
	private static final ResourceKey<Item> GOLD_BUTTON_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("gold_button"));
	private static final ResourceKey<Item> IRON_BUTTON_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("iron_button"));
	private static final ResourceKey<Item> OBSIDIAN_PRESSURE_PLATE_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("obsidian_pressure_plate"));
	private static final ResourceKey<Item> TORCH_ARROW_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("torch_arrow"));
	private static final ResourceKey<Item> WOODEN_HOPPER_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("wooden_hopper"));
	private static final ResourceKey<Item> PARROT_EGG_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("parrot_egg"));
	private static final ResourceKey<Item> SEED_POUCH_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("seed_pouch"));
	private static final ResourceKey<Item> FISH_BAIT_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("fish_bait"));
	private static final ResourceKey<Item> QUIVER_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("quiver"));
	private static final ResourceKey<Item> ENDER_POUCH_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("ender_pouch"));
	private static final ResourceKey<Item> PET_WHISTLE_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("pet_whistle"));
	private static final ResourceKey<Item> ROPE_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("rope"));
	private static final ResourceKey<Item> ROPE_LANTERN_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("rope_lantern"));
	private static final ResourceKey<Item> BOOMERANG_ID = ResourceKey.create(Registries.ITEM, QuirkyMod.id("boomerang"));

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

	public static final Item PARROT_EGG = new ParrotEggItem(
		new Item.Properties().stacksTo(16).setId(PARROT_EGG_ID)
	);

	public static final Item SEED_POUCH = new SeedPouchItem(
		new Item.Properties()
			.stacksTo(1)
			// 原版 bundle 同款：默认挂空 BUNDLE_CONTENTS，创造首拿即有容器组件 → tooltip 网格/空态正常
			.component(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY)
			.setId(SEED_POUCH_ID)
	);

	public static final Item FISH_BAIT = new FishBaitItem(
		new Item.Properties().stacksTo(16).setId(FISH_BAIT_ID)
	);

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
	);

	/** 绳捆：自定放置逻辑（向下延伸/潜行批量铺/挂点判定），见 {@link RopeItem}。 */
	public static final Item ROPE = new RopeItem(
		ModBlocks.ROPE,
		new Item.Properties().setId(ROPE_ID)
	);

	/** 挂灯绳段：由绳段+灯笼在游戏中转换而来（创造可拿取），放置/延伸同绳。 */
	public static final Item ROPE_LANTERN = new RopeItem(
		ModBlocks.ROPE_LANTERN,
		new Item.Properties().setId(ROPE_LANTERN_ID)
	);

	/** 回旋镖：250 耐久武器，投掷拾取/伤害，每次完整飞行消耗 1 点耐久。
	 * 近战面板：4 攻击伤害 + 1.6 攻速（mainhand）；WEAPON 组件使附魔/武器判定生效；
	 * 投掷命中伤害固定 4（BoomerangEntity#HIT_DAMAGE，不再走配置）。 */
	public static final Item BOOMERANG = new BoomerangItem(
		new Item.Properties()
			.setId(BOOMERANG_ID)
			.durability(250)
			.attributes(
				ItemAttributeModifiers.builder()
					.add(
						Attributes.ATTACK_DAMAGE,
						new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 4.0, AttributeModifier.Operation.ADD_VALUE),
						EquipmentSlotGroup.MAINHAND
					)
					.add(
						Attributes.ATTACK_SPEED,
						new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, 1.6, AttributeModifier.Operation.ADD_VALUE),
						EquipmentSlotGroup.MAINHAND
					)
					.build()
			)
			.component(DataComponents.WEAPON, new Weapon(1))
	);

	/** 把 quirky 物品插入到原版物品（anchor）最后一次出现之后，而非追加到页签尾部。
	 * Fabric 的 modifyOutput 回调在原版页签物品填充后触发（CreativeModeTabMixin 注入
	 * getStacks：displayItems 已含原版物品 → 事件 → clear+addAll 写回），displayStacks
	 * 即最终列表。同一 anchor 多次调用按当前列表实时定位，保证分组顺序。
	 * 防御：anchor 在原版列表中找不到时回退到 accept（追加尾部），不插入到错误位置。 */
	private static void insertAfter(FabricCreativeModeTabOutput output, Item anchor, ItemStack stack) {
		java.util.List<ItemStack> stacks = output.getDisplayStacks();
		int index = -1;
		for (int i = 0; i < stacks.size(); i++) {
			if (stacks.get(i).is(anchor)) {
				index = i;
			}
		}
		if (index == -1) {
			output.accept(stack);
		} else {
			stacks.add(index + 1, stack);
		}
	}

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
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("parrot_egg"), PARROT_EGG);
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("seed_pouch"), SEED_POUCH);
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("fish_bait"), FISH_BAIT);
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("quiver"), QUIVER);
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("ender_pouch"), ENDER_POUCH);
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("pet_whistle"), PET_WHISTLE);
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("rope"), ROPE);
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("rope_lantern"), ROPE_LANTERN);
		Registry.register(BuiltInRegistries.ITEM, QuirkyMod.id("boomerang"), BOOMERANG);

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register(output -> {
				// 精确穿插到原版物品旁（而非追加页签尾部）：容器类插 BUNDLE 后，绳/宠物类插 LEAD 后，云插风弹后
				insertAfter(output, Items.BUNDLE, new ItemStack(SEED_POUCH));
				insertAfter(output, Items.BUNDLE, new ItemStack(QUIVER));
				insertAfter(output, Items.BUNDLE, new ItemStack(ENDER_POUCH));
				insertAfter(output, Items.LEAD, new ItemStack(PET_WHISTLE));
				insertAfter(output, Items.LEAD, new ItemStack(ROPE));
				insertAfter(output, Items.LEAD, new ItemStack(ROPE_LANTERN));
				insertAfter(output, Items.WIND_CHARGE, new ItemStack(BOTTLED_CLOUD));
				insertAfter(output, Items.FISHING_ROD, new ItemStack(FISH_BAIT));
			});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS)
			.register(output -> {
				// 红石件按原版分区插入：按钮→按钮区，压力板→压力板区，漏斗→漏斗区
				insertAfter(output, Items.STONE_BUTTON, new ItemStack(GOLD_BUTTON));
				insertAfter(output, Items.STONE_BUTTON, new ItemStack(IRON_BUTTON));
				insertAfter(output, Items.HEAVY_WEIGHTED_PRESSURE_PLATE, new ItemStack(OBSIDIAN_PRESSURE_PLATE));
				insertAfter(output, Items.HOPPER, new ItemStack(WOODEN_HOPPER));
			});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT)
			.register(output -> {
				// 武器/投掷物区插入：回旋镖插三叉戟后（投掷武器），火把箭插箭后，鹦鹉蛋插蛋后
				insertAfter(output, Items.TRIDENT, new ItemStack(BOOMERANG));
				insertAfter(output, Items.ARROW, new ItemStack(TORCH_ARROW));
				insertAfter(output, Items.EGG, new ItemStack(PARROT_EGG));
			});
		// TOTEM_OF_HOLDING 不进创造页签：纯内部渲染素材（死亡点图腾实体显示用），玩家不应拿到
		// 木漏斗可作熔炉燃料（300 tick = 15 秒）：26.2 没有 DataComponents.FUEL，燃料改由服务端
		// FuelValues 数据驱动，Fabric 通过 FuelValueEvents.BUILD 事件向 Builder 追加条目。
		FuelValueEvents.BUILD.register((builder, context) -> builder.add(WOODEN_HOPPER, 300));
	}
}
