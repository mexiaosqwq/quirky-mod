package dev.quirky;

import dev.quirky.block.CloudBlock;
import dev.quirky.block.WoodenHopperBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
	private static final ResourceKey<Block> CLOUD_ID = ResourceKey.create(Registries.BLOCK, QuirkyMod.id("cloud"));
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
		Registry.register(BuiltInRegistries.BLOCK, WOODEN_HOPPER_ID, WOODEN_HOPPER);
	}
}
