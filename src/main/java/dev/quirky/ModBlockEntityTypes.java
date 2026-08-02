package dev.quirky;

import dev.quirky.block.be.WoodenHopperBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;

/**
 * 方块实体注册。26.2 无 {@code BlockEntityType.Builder}，直接构造
 * {@code new BlockEntityType<>(factory, validBlocks)} 并注册 ResourceKey。
 */
public final class ModBlockEntityTypes {
	private static final ResourceKey<BlockEntityType<?>> WOODEN_HOPPER_ID = ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, QuirkyMod.id("wooden_hopper"));

	public static final BlockEntityType<WoodenHopperBlockEntity> WOODEN_HOPPER = new BlockEntityType<>(
		WoodenHopperBlockEntity::new,
		Set.of(ModBlocks.WOODEN_HOPPER)
	);

	private ModBlockEntityTypes() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, WOODEN_HOPPER_ID, WOODEN_HOPPER);
	}
}
