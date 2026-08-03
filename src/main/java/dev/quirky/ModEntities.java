package dev.quirky;

import dev.quirky.entity.BoomerangEntity;
import dev.quirky.torch_arrow.TorchArrowEntity;
import dev.quirky.totem.TotemEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	private static final ResourceKey<EntityType<?>> TOTEM_ID = ResourceKey.create(Registries.ENTITY_TYPE, QuirkyMod.id("totem_of_holding"));
	private static final ResourceKey<EntityType<?>> TORCH_ARROW_ID = ResourceKey.create(Registries.ENTITY_TYPE, QuirkyMod.id("torch_arrow"));
	private static final ResourceKey<EntityType<?>> BOOMERANG_ID = ResourceKey.create(Registries.ENTITY_TYPE, QuirkyMod.id("boomerang"));

	public static final EntityType<TotemEntity> TOTEM = EntityType.Builder.of(TotemEntity::new, MobCategory.MISC)
		.sized(0.8F, 0.8F)
		.clientTrackingRange(8)
		.build(TOTEM_ID);

	public static final EntityType<TorchArrowEntity> TORCH_ARROW = EntityType.Builder.<TorchArrowEntity>of(TorchArrowEntity::new, MobCategory.MISC)
		.noLootTable()
		.sized(0.5F, 0.5F)
		.eyeHeight(0.13F)
		.clientTrackingRange(4)
		.updateInterval(20)
		.build(TORCH_ARROW_ID);

	public static final EntityType<BoomerangEntity> BOOMERANG = EntityType.Builder.<BoomerangEntity>of(BoomerangEntity::new, MobCategory.MISC)
		.noLootTable()
		.sized(0.4F, 0.4F)
		.clientTrackingRange(4)
		.updateInterval(20)
		.build(BOOMERANG_ID);

	private ModEntities() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.ENTITY_TYPE, TOTEM_ID, TOTEM);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, TORCH_ARROW_ID, TORCH_ARROW);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, BOOMERANG_ID, BOOMERANG);
	}
}
