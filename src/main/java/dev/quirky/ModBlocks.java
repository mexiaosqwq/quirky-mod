package dev.quirky;

import dev.quirky.block.CloudBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
	private static final ResourceKey<Block> CLOUD_ID = ResourceKey.create(Registries.BLOCK, QuirkyMod.id("cloud"));

	public static final CloudBlock CLOUD = new CloudBlock(
		BlockBehaviour.Properties.of()
			.setId(CLOUD_ID)
			.replaceable()
			.noCollision()
			.noLootTable()
			.instabreak()
			.sound(SoundType.POWDER_SNOW)
	);

	private ModBlocks() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.BLOCK, CLOUD_ID, CLOUD);
	}
}
