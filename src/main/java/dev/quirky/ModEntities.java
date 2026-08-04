package dev.quirky;

import dev.quirky.demobeast.DemoBeastEntity;
import dev.quirky.parrotegg.ParrotEggEntity;
import dev.quirky.fishbait.BaitZoneEntity;
import dev.quirky.fishbait.FishBaitEntity;
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
	private static final ResourceKey<EntityType<?>> PARROT_EGG_ID = ResourceKey.create(Registries.ENTITY_TYPE, QuirkyMod.id("parrot_egg"));
	private static final ResourceKey<EntityType<?>> FISH_BAIT_ID = ResourceKey.create(Registries.ENTITY_TYPE, QuirkyMod.id("fish_bait"));
	private static final ResourceKey<EntityType<?>> BAIT_ZONE_ID = ResourceKey.create(Registries.ENTITY_TYPE, QuirkyMod.id("bait_zone"));
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

	public static final EntityType<ParrotEggEntity> PARROT_EGG = EntityType.Builder.<ParrotEggEntity>of(ParrotEggEntity::new, MobCategory.MISC)
		.noLootTable()
		.sized(0.25F, 0.25F)
		.clientTrackingRange(4)
		.updateInterval(10)
		.build(PARROT_EGG_ID);

	public static final EntityType<FishBaitEntity> FISH_BAIT = EntityType.Builder.<FishBaitEntity>of(FishBaitEntity::new, MobCategory.MISC)
		.noLootTable()
		.sized(0.25F, 0.25F)
		.clientTrackingRange(4)
		.updateInterval(10)
		.build(FISH_BAIT_ID);

	public static final EntityType<BaitZoneEntity> BAIT_ZONE = EntityType.Builder.<BaitZoneEntity>of(BaitZoneEntity::new, MobCategory.MISC)
		.noLootTable()
		.sized(0.5F, 0.5F)
		.clientTrackingRange(16)
		.updateInterval(20)
		.build(BAIT_ZONE_ID);
	public static final EntityType<BoomerangEntity> BOOMERANG = EntityType.Builder.<BoomerangEntity>of(BoomerangEntity::new, MobCategory.MISC)
		.noLootTable()
		.sized(0.4F, 0.4F)
		.clientTrackingRange(4)
		.updateInterval(20)
		.build(BOOMERANG_ID);

	private static final ResourceKey<EntityType<?>> DEMO_BEAST_ID = ResourceKey.create(Registries.ENTITY_TYPE, QuirkyMod.id("demo_beast"));

	public static final EntityType<DemoBeastEntity> DEMO_BEAST = EntityType.Builder.of(DemoBeastEntity::new, MobCategory.CREATURE)
		.sized(0.8F, 0.8F)
		.clientTrackingRange(8)
		.build(DEMO_BEAST_ID);

	private ModEntities() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.ENTITY_TYPE, TOTEM_ID, TOTEM);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, TORCH_ARROW_ID, TORCH_ARROW);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, PARROT_EGG_ID, PARROT_EGG);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, FISH_BAIT_ID, FISH_BAIT);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, BAIT_ZONE_ID, BAIT_ZONE);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, BOOMERANG_ID, BOOMERANG);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, DEMO_BEAST_ID, DEMO_BEAST);
	}
}
