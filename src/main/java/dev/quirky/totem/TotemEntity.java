package dev.quirky.totem;

import com.mojang.serialization.Codec;
import dev.quirky.ModSounds;
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
	private static final int HITS_TO_RETRIEVE = 3;
	// UUIDUtil.CODEC encodes UUIDs as int arrays; NbtOps compound keys must be strings,
	// so the map key uses UUIDUtil.STRING_CODEC (UUID.toString / UUID.fromString).
	private static final Codec<Map<UUID, Integer>> HITS_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT);

	// ==== 手感参数区（集中调参）====
	private static final float HIT_SOUND_VOLUME = 1.0F;          // 击打反馈音音量（>1 只拉长衰减距离，响度 clamp 1.0）
	private static final float HIT_SOUND_PITCH = 1.0F;            // 击打反馈音高
	private static final float RETRIEVE_SOUND_VOLUME = 0.5F;      // 取回音效音量
	private static final float AMBIENT_CHIME_VOLUME = 2.0F;       // 环境叮声音量：衰减距离 = 音量×定义衰减(64) = 128 格（自定义音效，定义音量 1.0 无折减）
	private static final float AMBIENT_CHIME_PITCH = 1.3F;        // 环境叮声音高（高=更清脆）
	private static final int AMBIENT_CHIME_INTERVAL = 100;        // 环境音间隔 tick（100 ≈ 5 秒一次，调大更稀）
	private static final int ENCHANT_PARTICLE_CHANCE = 4;         // 紫符文粒子：每 tick 1/N 概率（调大更稀）
	private static final int END_ROD_PARTICLE_CHANCE = 12;        // 白光点粒子：每 tick 1/N 概率
	private static final double PARTICLE_XZ_SPREAD = 0.45;        // 紫符文散布半径（格）
	private static final double PARTICLE_Y_SPREAD = 0.55;         // 紫符文散布高度（格）

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
			int count = this.hits.merge(player.getUUID(), 1, Integer::sum);
			if (count >= HITS_TO_RETRIEVE) {
				this.retrieveFor(player);
			} else {
				this.playSound(SoundEvents.AMETHYST_BLOCK_HIT, HIT_SOUND_VOLUME, HIT_SOUND_PITCH);
			}
		}
		return false;
	}

	private void retrieveFor(Player player) {
		for (ItemStack stack : TotemOfHoldingLogic.restoreToPlayer(player, this.stored)) {
			this.spawnAtLocation((ServerLevel) this.level(), stack);
		}
		this.playSound(SoundEvents.TOTEM_USE, RETRIEVE_SOUND_VOLUME, 1.0F);
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
			// 26.2 服务端粒子必须用 sendParticles（Level.addParticle 是空实现，仅 ClientLevel 覆盖）
			if (this.random.nextInt(ENCHANT_PARTICLE_CHANCE) == 0) {
				serverLevel.sendParticles(
					ParticleTypes.ENCHANT,
					this.getX(),
					this.getY() + 0.3,
					this.getZ(),
					1,
					PARTICLE_XZ_SPREAD,
					PARTICLE_Y_SPREAD,
					PARTICLE_XZ_SPREAD,
					0.02
				);
			}
			if (this.random.nextInt(END_ROD_PARTICLE_CHANCE) == 0) {
				serverLevel.sendParticles(
					ParticleTypes.END_ROD,
					this.getX(),
					this.getY() + 0.5,
					this.getZ(),
					1,
					0.35,
					0.3,
					0.35,
					0.01
				);
			}
			if (this.random.nextInt(AMBIENT_CHIME_INTERVAL) == 0) {
				this.playSound(ModSounds.TOTEM_CHIME, AMBIENT_CHIME_VOLUME, AMBIENT_CHIME_PITCH);
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
