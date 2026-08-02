package dev.quirky;

import dev.quirky.block.CloudBlock;
import dev.quirky.block.MetalButtonBlock;
import dev.quirky.block.ObsidianPressurePlateBlock;
import dev.quirky.block.WoodenHopperBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
	private static final ResourceKey<Block> CLOUD_ID = ResourceKey.create(Registries.BLOCK, QuirkyMod.id("cloud"));
	private static final ResourceKey<Block> GOLD_BUTTON_ID = ResourceKey.create(Registries.BLOCK, QuirkyMod.id("gold_button"));
	private static final ResourceKey<Block> IRON_BUTTON_ID = ResourceKey.create(Registries.BLOCK, QuirkyMod.id("iron_button"));
	private static final ResourceKey<Block> OBSIDIAN_PRESSURE_PLATE_ID = ResourceKey.create(Registries.BLOCK, QuirkyMod.id("obsidian_pressure_plate"));
	private static final ResourceKey<Block> WOODEN_HOPPER_ID = ResourceKey.create(Registries.BLOCK, QuirkyMod.id("wooden_hopper"));

	public static final CloudBlock CLOUD = new CloudBlock(
		BlockBehaviour.Properties.of()
			.setId(CLOUD_ID)
			.replaceable()
			.noCollision()
			.noLootTable()
			.instabreak()
			.sound(SoundType.POWDER_SNOW)
	);

	/** 金按钮：按下保持 2 红石刻（0.1s）短脉冲。 */
	public static final MetalButtonBlock GOLD_BUTTON = new MetalButtonBlock(
		MetalButtonBlock.METAL,
		2,
		true,
		BlockBehaviour.Properties.of()
			.setId(GOLD_BUTTON_ID)
			.noCollision()
			.strength(0.5F)
			.pushReaction(PushReaction.DESTROY)
	);

	/** 铁按钮：按下保持 100 刻（5s）长脉冲。 */
	public static final MetalButtonBlock IRON_BUTTON = new MetalButtonBlock(
		MetalButtonBlock.METAL,
		100,
		false,
		BlockBehaviour.Properties.of()
			.setId(IRON_BUTTON_ID)
			.noCollision()
			.strength(0.5F)
			.pushReaction(PushReaction.DESTROY)
	);

	/** 黑曜石压力板：仅玩家踩上触发，强度与黑曜石一致（50F/1200F，需正确工具）。 */
	public static final ObsidianPressurePlateBlock OBSIDIAN_PRESSURE_PLATE = new ObsidianPressurePlateBlock(
		BlockSetType.STONE,
		BlockBehaviour.Properties.of()
			.setId(OBSIDIAN_PRESSURE_PLATE_ID)
			.mapColor(MapColor.COLOR_BLACK)
			.forceSolidOn()
			.noCollision()
			.strength(50.0F, 1200.0F)
			.requiresCorrectToolForDrops()
			.sound(SoundType.STONE)
			.pushReaction(PushReaction.DESTROY)
	);

	/** 木漏斗：传输速度 1/4、红石锁不住，木制可作燃料。 */
	public static final WoodenHopperBlock WOODEN_HOPPER = new WoodenHopperBlock(
		BlockBehaviour.Properties.of()
			.setId(WOODEN_HOPPER_ID)
			.mapColor(MapColor.WOOD)
			.strength(3.0F)
			.sound(SoundType.WOOD)
			.noOcclusion()
	);

	private ModBlocks() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.BLOCK, CLOUD_ID, CLOUD);
		Registry.register(BuiltInRegistries.BLOCK, GOLD_BUTTON_ID, GOLD_BUTTON);
		Registry.register(BuiltInRegistries.BLOCK, IRON_BUTTON_ID, IRON_BUTTON);
		Registry.register(BuiltInRegistries.BLOCK, OBSIDIAN_PRESSURE_PLATE_ID, OBSIDIAN_PRESSURE_PLATE);
		Registry.register(BuiltInRegistries.BLOCK, WOODEN_HOPPER_ID, WOODEN_HOPPER);
	}
}
