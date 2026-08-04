package dev.quirky.item;

import java.util.Collections;
import java.util.List;

import dev.quirky.config.QuirkyConfigHolder;
import dev.quirky.whistle.WhistleLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 宠物口哨：一声哨响把宠物召集到身边（白天），夜晚还能把幻翼掼到地面近战。
 *
 * <ul>
 *   <li>右键 → 半径内宠物起身寻路走向玩家；超半径（同维度）直接传送；</li>
 *   <li>潜行+右键 → 切换半径内狼/猫的坐定状态（鹦鹉不受影响）；</li>
 *   <li>夜晚（幻翼活跃条件）→ 额外嘲讽 1-3 只幻翼锁定玩家 30 秒（锚点钉在玩家头顶，
 *       幻翼自行飞入并盘旋俯冲，不传送）。</li>
 * </ul>
 */
public class PetWhistleItem extends Item {

	/** 搜索宠物时覆盖的范围（格）：超出口哨半径即传送，需覆盖到"卡在远处"的场景。 */
	private static final int PET_SEARCH_RADIUS = 128;
	/** 幻翼嘲讽搜索半径（格）。 */
	private static final int PHANTOM_TAUNT_RANGE = 48;
	/** 嘲讽持续 tick（30 秒）。 */
	private static final int TAUNT_DURATION_TICKS = 600;
	/** 幻翼嘲讽锚点高度（玩家头顶上方格数）。 */
	private static final int TAUNT_ANCHOR_ABOVE = 5;
	/** 幻翼嘲讽截止时间的 NBT 键（gameTime），PhantomTauntMixin 每 tick 读取。 */
	public static final String TAUNT_UNTIL_KEY = "taunt_until";

	public PetWhistleItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide()) {
			ServerLevel serverLevel = (ServerLevel) level;
			if (player.isShiftKeyDown()) {
				toggleSitting(serverLevel, player);
			} else {
				callPets(serverLevel, player);
				if (phantomActiveConditions(serverLevel)) {
					tauntPhantoms(serverLevel, player);
				}
			}
			playWhistleSound(serverLevel, player, player.isShiftKeyDown());
		}
		return InteractionResult.SUCCESS;
	}

	/** 召集：半径内寻路走向玩家，超半径传送（同维度）。 */
	private void callPets(ServerLevel level, Player player) {
		int radius = QuirkyConfigHolder.get().petWhistleRadius;
		double radiusSq = (double) radius * radius;
		AABB searchBox = AABB.ofSize(player.position(), 2.0 * PET_SEARCH_RADIUS, 2.0 * PET_SEARCH_RADIUS, 2.0 * PET_SEARCH_RADIUS);
		List<TamableAnimal> pets = level.getEntitiesOfClass(TamableAnimal.class, searchBox, WhistleLogic.ownedBy(player));
		for (TamableAnimal pet : pets) {
			// 坐着的先起身（同步持久化字段，防存档后重新坐下）
			if (pet.isOrderedToSit()) {
				pet.setOrderedToSit(false);
			}
			double distSq = pet.distanceToSqr(player);
			if (distSq <= radiusSq) {
				pet.getNavigation().moveTo(player, 1.5);
				spawnHearts(level, pet);
			} else {
				teleportPetToPlayer(level, pet, player);
				spawnHearts(level, pet);
			}
		}
	}

	/** 坐定指挥：切换半径内狼/猫的坐定状态（鹦鹉不受影响）。 */
	private void toggleSitting(ServerLevel level, Player player) {
		int radius = QuirkyConfigHolder.get().petWhistleRadius;
		AABB searchBox = AABB.ofSize(player.position(), 2.0 * radius, 2.0 * radius, 2.0 * radius);
		List<TamableAnimal> pets = level.getEntitiesOfClass(TamableAnimal.class, searchBox, WhistleLogic.ownedBy(player));
		for (TamableAnimal pet : pets) {
			if (pet instanceof Parrot) {
				continue;
			}
			// 与原版右键训宠同款：翻转持久化字段，SitWhenOrderedToGoal 负责姿势同步
			pet.setOrderedToSit(!pet.isOrderedToSit());
		}
	}

	/** 夜间幻翼嘲讽：锁定 1-3 只幻翼目标为玩家 30 秒（锚点钉在玩家头顶，自行飞入盘旋俯冲）。 */
	private void tauntPhantoms(ServerLevel level, Player player) {
		List<Phantom> phantoms = level.getEntitiesOfClass(
			Phantom.class,
			player.getBoundingBox().inflate(PHANTOM_TAUNT_RANGE),
			EntitySelector.ENTITY_STILL_ALIVE
		);
		if (phantoms.isEmpty()) {
			return;
		}
		int count = WhistleLogic.selectPhantoms(phantoms.size(), QuirkyConfigHolder.get().petWhistlePhantomMax, level.getRandom());
		Collections.shuffle(phantoms, new java.util.Random(level.getRandom().nextLong()));
		for (int i = 0; i < count && i < phantoms.size(); i++) {
			Phantom phantom = phantoms.get(i);
			phantom.setTarget(player);
			markTaunted(level, phantom);
			phantom.playSound(SoundEvents.PHANTOM_SWOOP, 1.0F, 1.0F);
			level.sendParticles(ParticleTypes.DUST_PLUME, phantom.getX(), phantom.getY(), phantom.getZ(), 8, 0.4, 0.1, 0.4, 0.0);
		}
	}

	/** 在幻翼 NBT 写入嘲讽截止时间（gameTime + 600 tick），PhantomTauntMixin 每 tick 读取。 */
	private static void markTaunted(ServerLevel level, Phantom phantom) {
		CustomData data = phantom.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = data != null ? data.copyTag() : new CompoundTag();
		tag.putLong(TAUNT_UNTIL_KEY, level.getGameTime() + TAUNT_DURATION_TICKS);
		phantom.setComponent(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	/** 宠物传送：玩家身边 1-2 格空位（防卡墙），落地末影粒子 + 传送音效。 */
	private static void teleportPetToPlayer(ServerLevel level, TamableAnimal pet, Player player) {
		BlockPos playerPos = player.blockPosition();
		for (int attempt = 0; attempt < 16; attempt++) {
			int xd = Mth.nextInt(pet.getRandom(), -2, 2);
			int zd = Mth.nextInt(pet.getRandom(), -2, 2);
			if (Math.abs(xd) < 1 && Math.abs(zd) < 1) {
				continue;
			}
			int yd = Mth.nextInt(pet.getRandom(), 0, 1);
			BlockPos pos = playerPos.offset(xd, yd, zd);
			if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
				&& !level.getBlockState(pos.below()).isAir()) {
				pet.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, pet.getYRot(), pet.getXRot());
				pet.getNavigation().stop();
				Vec3 at = pet.position();
				level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 0.5, at.z, 8, 0.3, 0.3, 0.3, 0.1);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.3F, 1.0F);
				return;
			}
		}
		// 全失败：兜底放玩家正上方空位
		BlockPos fallback = playerPos.above(1);
		pet.snapTo(fallback.getX() + 0.5, fallback.getY(), fallback.getZ() + 0.5, pet.getYRot(), pet.getXRot());
		pet.getNavigation().stop();
	}

	private static void spawnHearts(ServerLevel level, LivingEntity entity) {
		Vec3 at = entity.position();
		level.sendParticles(ParticleTypes.HEART, at.x, at.y + entity.getBbHeight() + 0.3, at.z, 4, 0.2, 0.2, 0.2, 0.0);
	}

	/** 与 PhantomSpawner 相同的幻翼活跃条件（天空变暗或无尽夜维度）。 */
	private static boolean phantomActiveConditions(Level level) {
		return level.getSkyDarken() >= 5 || !level.dimensionType().hasSkyLight();
	}

	/** 哨音：清亮高双音长笛（接近真实口哨，实测音高 ≥1.8 才够尖）。
	 * 26.2 playSeededSound 无 delay 参数，无法做延迟音序，用紧密双音制造"鸣哨"听感。
	 * 数值可调：召集哨 1.8/2.0（音量 2.0/1.2，略低一档与坐定区分）；
	 * 坐定指挥 2.0/2.2（音量 1.6/1.0，更高更短）。
	 * 不用山羊角号角（实测太像劫掠者号角声，听感沉闷）；不要三重音叠簇（实测沉闷有节奏感）。 */
	private static void playWhistleSound(ServerLevel level, Player player, boolean sittingCommand) {
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		if (sittingCommand) {
			level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_FLUTE, SoundSource.PLAYERS, 1.6F, 2.0F);
			level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_FLUTE, SoundSource.PLAYERS, 1.0F, 2.2F);
			return;
		}
		level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_FLUTE, SoundSource.PLAYERS, 2.0F, 1.8F);
		level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_FLUTE, SoundSource.PLAYERS, 1.2F, 2.0F);
	}
}
