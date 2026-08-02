package dev.quirky;

import dev.quirky.totem.TotemEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	private static final ResourceKey<EntityType<?>> TOTEM_ID = ResourceKey.create(Registries.ENTITY_TYPE, QuirkyMod.id("totem_of_holding"));

	public static final EntityType<TotemEntity> TOTEM = EntityType.Builder.of(TotemEntity::new, MobCategory.MISC)
		.sized(0.5F, 0.6F)
		.clientTrackingRange(8)
		.build(TOTEM_ID);

	private ModEntities() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.ENTITY_TYPE, TOTEM_ID, TOTEM);
	}
}
