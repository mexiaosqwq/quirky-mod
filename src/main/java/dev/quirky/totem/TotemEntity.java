package dev.quirky.totem;

import com.mojang.serialization.Codec;
import dev.quirky.config.QuirkyConfig;
import dev.quirky.config.QuirkyConfigHolder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TotemEntity extends Entity {
	private static final String TAG_OWNER_MOST = "OwnerMost";
	private static final String TAG_OWNER_LEAST = "OwnerLeast";
	private static final String TAG_ITEMS = "Items";
	private static final String TAG_HITS = "Hits";
	// UUIDUtil.CODEC encodes UUIDs as int arrays; NbtOps compound keys must be strings,
	// so the map key uses UUIDUtil.STRING_CODEC (UUID.toString / UUID.fromString).
	private static final Codec<Map<UUID, Integer>> HITS_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT);

	private UUID owner;
	private List<ItemStackWithSlot> stored = List.of();
	private final Map<UUID, Integer> hits = new HashMap<>();

	public TotemEntity(EntityType<TotemEntity> type, Level level) {
		super(type, level);
	}

	public void initStored(UUID owner, List<ItemStackWithSlot> stored) {
		this.owner = owner;
		this.stored = stored;
	}

	public UUID getOwner() {
		return this.owner;
	}

	public List<ItemStackWithSlot> getStored() {
		return this.stored;
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (!this.level().isClientSide()
			&& source.getEntity() instanceof Player player
			&& source.is(DamageTypes.PLAYER_ATTACK)) {
			QuirkyConfig config = QuirkyConfigHolder.get();
			int count = this.hits.merge(player.getUUID(), 1, Integer::sum);
			if (count >= config.hitsToRetrieve) {
				this.retrieveFor(player);
			} else {
				this.playSound(SoundEvents.AMETHYST_BLOCK_HIT, config.hitSoundVolume, config.hitSoundPitch);
			}
		}
		return false;
	}

	private void retrieveFor(Player player) {
		for (ItemStack stack : TotemOfHoldingLogic.restoreToPlayer(player, this.stored)) {
			this.spawnAtLocation((ServerLevel) this.level(), stack);
		}
		this.playSound(SoundEvents.TOTEM_USE, QuirkyConfigHolder.get().retrieveSoundVolume, 1.0F);
		this.discard();
	}

	public static void breakForOwner(ServerPlayer player) {
		for (ServerLevel level : player.level().getServer().getAllLevels()) {
			for (TotemEntity totem : level.getEntities(EntityTypeTest.forClass(TotemEntity.class), e -> player.getUUID().equals(e.getOwner()))) {
				for (ItemStackWithSlot entry : totem.getStored()) {
					totem.spawnAtLocation(level, entry.stack());
				}
				totem.discard();
			}
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel serverLevel) {
			QuirkyConfig config = QuirkyConfigHolder.get();
			int enchantChance = Math.max(1, config.enchantParticleChance);
			int endRodChance = Math.max(1, config.endRodParticleChance);
			if (this.random.nextInt(enchantChance) == 0) {
				serverLevel.sendParticles(
					ParticleTypes.ENCHANT,
					this.getX(),
					this.getY() + 0.3,
					this.getZ(),
					1,
					config.particleXzSpread,
					config.particleYSpread,
					config.particleXzSpread,
					0.02
				);
			}
			if (this.random.nextInt(endRodChance) == 0) {
				serverLevel.sendParticles(
					ParticleTypes.END_ROD,
					this.getX(),
					this.getY() + 0.5,
					this.getZ(),
					1,
					config.endRodParticleXzSpread,
					config.endRodParticleYSpread,
					config.endRodParticleXzSpread,
					0.01
				);
			}
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		if (this.owner != null) {
			output.putLong(TAG_OWNER_MOST, this.owner.getMostSignificantBits());
			output.putLong(TAG_OWNER_LEAST, this.owner.getLeastSignificantBits());
		}
		output.store(TAG_ITEMS, ItemStackWithSlot.CODEC.listOf(), this.stored);
		output.store(TAG_HITS, HITS_CODEC, this.hits);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		long most = input.getLongOr(TAG_OWNER_MOST, 0L);
		long least = input.getLongOr(TAG_OWNER_LEAST, 0L);
		this.owner = most == 0L && least == 0L ? null : new UUID(most, least);
		this.stored = input.read(TAG_ITEMS, ItemStackWithSlot.CODEC.listOf()).orElse(List.of());
		this.hits.clear();
		this.hits.putAll(input.read(TAG_HITS, HITS_CODEC).orElse(Map.of()));
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
	}
}
