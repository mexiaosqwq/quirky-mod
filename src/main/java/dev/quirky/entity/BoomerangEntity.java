package dev.quirky.entity;

import dev.quirky.ModEntities;
import dev.quirky.boomerang.BoomerangBlockLogic;
import dev.quirky.boomerang.BoomerangPhysics;
import dev.quirky.config.QuirkyConfigHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 回旋镖飞行实体：自实现 tick 物理（无重力直线 + 出程右偏弧线，返程向投掷者转向）。
 * 飞行中拾取地面物品（记入 NBT 列表），触碰生物造成轻伤+击退（每生物每次飞行只判定一次），
 * 撞方块小概率打碎（秒破类必碎，免疫 tag / 冒险模式不打碎），并远程激活钟/按钮等方块。
 * 投掷者 UUID 由 {@link Projectile#getOwner()}（EntityReference）查找，防内存悬挂。
 */
public class BoomerangEntity extends Projectile implements ItemSupplier {
	/** 出程右偏弧度/格。 */
	private static final double ARC_RADIANS = 0.03;
	/** 返程单步最大转向（弧度/tick，约 20°/tick）。 */
	private static final double RETURN_TURN_RATE = 0.35;
	/** 回手判定距离 1.5 格（平方）。 */
	private static final double CATCH_DISTANCE_SQ = 2.25;
	/** 命中生物固定伤害（武器化后不再走配置；对齐近战属性 4）。 */
	private static final float HIT_DAMAGE = 4.0F;
	/** 10 秒兜底自毁上限。 */
	private static final int MAX_LIFETIME_TICKS = 200;
	/** 出程速度（格/tick）。 */
	private static final double THROW_SPEED = 1.0;

	private static final String TAG_RETURNING = "Returning";
	private static final String TAG_HITS = "HitEntities";
	private static final String TAG_COLLECTED = "CollectedItems";
	private static final String TAG_MAX_RANGE = "MaxRange";
	private static final String TAG_THROW_SLOT = "ThrowSlot";
	private static final String TAG_TRAVELED = "TraveledDistance";
	private static final String TAG_HIT_UUID_MOST = "M";
	private static final String TAG_HIT_UUID_LEAST = "L";

	/** 物品同步：entityData 广播到客户端（渲染器用），照原版 ThrowableItemProjectile 的 DATA_ITEM_STACK 模式。 */
	private static final EntityDataAccessor<ItemStack> DATA_ITEM_STACK = SynchedEntityData.defineId(
		BoomerangEntity.class, EntityDataSerializers.ITEM_STACK
	);

	private final List<ItemStack> collected = new ArrayList<>();
	private final Set<UUID> hitEntities = new HashSet<>();
	private boolean returning;
	private int maxRange = 12;
	private int throwSlot = -1;
	private double traveledDistance;
	private int lifetimeTicks;

	public BoomerangEntity(EntityType<? extends BoomerangEntity> type, Level level) {
		super(type, level);
	}

	public BoomerangEntity(ServerLevel level, Player owner, ItemStack item, int throwSlot) {
		super(ModEntities.BOOMERANG, level);
		this.setItem(item);
		this.throwSlot = throwSlot;
		this.maxRange = QuirkyConfigHolder.get().boomerangRange;
		this.setOwner(owner);
	}

	/** 写入 entityData（客户端可见），同原版 setItem 语义。 */
	public void setItem(ItemStack source) {
		this.getEntityData().set(DATA_ITEM_STACK, source.copyWithCount(1));
	}

	@Override
	public ItemStack getItem() {
		return this.getEntityData().get(DATA_ITEM_STACK);
	}

	/** 是否属于该玩家（用于投掷时防重复飞行）。 */
	public boolean isOwnedBy(Player player) {
		Entity owner = this.getOwner();
		return owner != null && owner.getUUID().equals(player.getUUID());
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
		builder.define(DATA_ITEM_STACK, ItemStack.EMPTY);
	}

	// ==== NBT ====

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean(TAG_RETURNING, this.returning);
		output.putInt(TAG_MAX_RANGE, this.maxRange);
		output.putInt(TAG_THROW_SLOT, this.throwSlot);
		output.putDouble(TAG_TRAVELED, this.traveledDistance);
		output.store("Item", ItemStack.CODEC, this.getItem());
		output.store(TAG_COLLECTED, ItemStack.OPTIONAL_CODEC.listOf(), this.collected);
		ValueOutput.ValueOutputList list = output.childrenList(TAG_HITS);
		for (UUID id : this.hitEntities) {
			ValueOutput child = list.addChild();
			child.putLong(TAG_HIT_UUID_MOST, id.getMostSignificantBits());
			child.putLong(TAG_HIT_UUID_LEAST, id.getLeastSignificantBits());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.returning = input.getBooleanOr(TAG_RETURNING, false);
		this.maxRange = input.getIntOr(TAG_MAX_RANGE, 12);
		this.throwSlot = input.getIntOr(TAG_THROW_SLOT, -1);
		this.traveledDistance = input.getDoubleOr(TAG_TRAVELED, 0.0);
		this.collected.clear();
		this.collected.addAll(input.read(TAG_COLLECTED, ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()));
		input.read("Item", ItemStack.CODEC).ifPresent(this::setItem);
		this.hitEntities.clear();
		for (ValueInput child : input.childrenListOrEmpty(TAG_HITS)) {
			this.hitEntities.add(new UUID(child.getLongOr(TAG_HIT_UUID_MOST, 0L), child.getLongOr(TAG_HIT_UUID_LEAST, 0L)));
		}
	}

	// ==== 命中过滤 ====

	@Override
	protected boolean canHitEntity(Entity entity) {
		if (!(entity instanceof LivingEntity) || entity instanceof net.minecraft.world.entity.decoration.ArmorStand) {
			return false;
		}
		Entity owner = this.getOwner();
		if (owner != null && entity.getUUID().equals(owner.getUUID())) {
			return false;
		}
		if (this.hitEntities.contains(entity.getUUID())) {
			return false;
		}
		return super.canHitEntity(entity);
	}

	// ==== 物理 tick ====

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			return;
		}
		ServerLevel level = (ServerLevel) this.level();
		this.lifetimeTicks++;
		Entity owner = this.getOwner();
		// 兜底：10 秒上限 / 坠入虚空（不掉落）/ 投掷者死亡·离线·跨维度（就地掉落）
		if (this.lifetimeTicks > MAX_LIFETIME_TICKS || this.getY() < level.getMinY() - 32.0) {
			this.dropAllAndDiscard(level);
			return;
		}
		if (owner == null || !owner.isAlive() || owner.level() != level) {
			this.dropAllAndDiscard(level);
			return;
		}

		Vec3 pos = this.position();
		Vec3 vel = this.getDeltaMovement();
		if (vel.lengthSqr() < 1e-6) {
			vel = this.getLookAngle().scale(THROW_SPEED);
		}

		if (!this.returning) {
			this.traveledDistance += vel.length();
			vel = BoomerangPhysics.applyArc(vel, ARC_RADIANS);
			if (this.traveledDistance >= this.maxRange) {
				this.returning = true;
			}
		} else {
			Vec3 target = owner.position().add(0.0, owner.getEyeHeight() * 0.5, 0.0);
			if (pos.distanceToSqr(target) <= CATCH_DISTANCE_SQ) {
				this.retrieveFor(level, owner);
				return;
			}
			vel = BoomerangPhysics.returnVector(pos, target, vel, RETURN_TURN_RATE);
		}

		Vec3 newPos = BoomerangPhysics.step(pos, vel, 1.0);

		// 实体命中（比方块优先，贴合原版弹射物流程）
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
			level, this, pos, newPos, this.getBoundingBox().expandTowards(vel).inflate(1.0), this::canHitEntity
		);
		if (entityHit != null && entityHit.getEntity() instanceof LivingEntity target) {
			this.hitLiving(target);
			newPos = entityHit.getLocation();
		} else {
			BlockHitResult blockHit = level.clipIncludingBorder(new ClipContext(pos, newPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
			if (blockHit.getType() != HitResult.Type.MISS) {
				newPos = blockHit.getLocation();
				this.handleBlockHit(level, blockHit);
				// handleBlockHit 可能反射速度（未碎反弹）或保持原速（打碎穿透），以实体最新速度为继续飞行的速度
				vel = this.getDeltaMovement();
			}
		}

		// 拾取地面物品（0.5 格内吸附）
		for (ItemEntity itemEntity : level.getEntities(
			EntityTypeTest.forClass(ItemEntity.class), this.getBoundingBox().expandTowards(vel).inflate(0.5), Entity::isAlive
		)) {
			this.absorb(itemEntity);
		}

		this.setPos(newPos);
		this.setDeltaMovement(vel);
		this.updateRotationFromVelocity();
	}

	private void hitLiving(LivingEntity target) {
		ServerLevel level = (ServerLevel) this.level();
		DamageSource source = level.damageSources().thrown(this, this.getOwner());
		target.hurtServer(level, source, HIT_DAMAGE);
		Vec3 vel = this.getDeltaMovement();
		target.knockback(0.3, vel.x, vel.z, source, HIT_DAMAGE);
		this.hitEntities.add(target.getUUID());
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5F, 1.0F);
	}

	private void handleBlockHit(ServerLevel level, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);

		// 远程激活：钟/目标块/蜡烛/营火/TNT 等走原版 onProjectileHit 语义
		if (BoomerangBlockLogic.canActivate(state)) {
			state.onProjectileHit(level, state, hit, this);
		}
		// 26.2 按钮只响应 AbstractArrow（checkPressed 按箭头类型查询），这里手动补按钮触发
		if (state.getBlock() instanceof ButtonBlock button && !state.getValue(ButtonBlock.POWERED)) {
			button.press(state, level, pos, null);
		}

		// 打碎判定：秒破类必碎 / 免疫与冒险不打碎 / 其余默认 5% 摇骰
		if (this.breakBlock(level, pos, state)) {
			return; // 打碎 → 穿透继续飞
		}

		// 未碎 → 反弹：进入返程（若仍在出程）+ 沿法线反射速度
		if (!this.returning) {
			this.returning = true;
		}
		Vec3 vel = this.getDeltaMovement();
		Vec3 normal = hit.getDirection().getUnitVec3();
		this.setDeltaMovement(vel.subtract(normal.scale(2.0 * vel.dot(normal))));
	}

	/** 打碎判定与执行；返回是否打碎（打碎后回旋镖穿透继续飞）。 */
	private boolean breakBlock(ServerLevel level, BlockPos pos, BlockState state) {
		Entity owner = this.getOwner();
		boolean adventure = owner instanceof Player player && player.gameMode() == GameType.ADVENTURE;
		boolean immune = state.is(BoomerangBlockLogic.UNBREAKABLE_TAG);
		BoomerangBlockLogic.BreakResult result = BoomerangBlockLogic.decideBreak(state.getDestroySpeed(level, pos), immune, adventure);
		if (result == BoomerangBlockLogic.BreakResult.CANNOT_BREAK) {
			return false;
		}
		if (result == BoomerangBlockLogic.BreakResult.ROLL && this.random.nextDouble() >= QuirkyConfigHolder.get().boomerangBreakChance) {
			return false;
		}
		// destroyBlock 内部会触发 2001 破坏粒子，掉落受 BLOCK_DROPS 规则管辖
		// 创造模式不掉物（对齐原版创造手挖 ServerPlayerGameMode 的 preventsBlockDrops）
		boolean dropBlocks = !(owner instanceof Player p && p.hasInfiniteMaterials());
		level.destroyBlock(pos, dropBlocks, this, 3);
		level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.8F, 1.0F);
		return true;
	}

	private void absorb(ItemEntity itemEntity) {
		this.collected.add(itemEntity.getItem());
		itemEntity.discard();
		this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 1.0F);
	}

	/** 回到投掷者：消耗 1 点耐久（归零则直接损坏消失），归还物品与拾取物。 */
	private void retrieveFor(ServerLevel level, Entity owner) {
		boolean broken = this.applyFlightDurability(level, owner);
		if (owner instanceof Player player) {
			boolean creative = player.hasInfiniteMaterials();
			// 回旋镖本体：仅生存模式归还（投掷时消耗了 1 个）；创造模式手里未消耗，不归还以免复制
			if (!broken && !creative) {
				if (this.returnToInventory(player)) {
					player.playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.5F, 1.0F);
				} else {
					level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY(), player.getZ(), this.getItem()));
				}
			}
			// 拾取物：放入背包，部分放入的剩余就地掉落（不吞物品）
			for (ItemStack stack : this.collected) {
				player.getInventory().add(stack);
				if (!stack.isEmpty()) {
					level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY(), player.getZ(), stack));
				}
			}
		} else {
			level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(), this.getZ(), this.getItem()));
			for (ItemStack stack : this.collected) {
				level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(), this.getZ(), stack));
			}
		}
		this.discard();
	}

	/** 每次完整飞行消耗 1 点耐久；归零时物品 shrink 消失并播放 ITEM_BREAK，返回是否已损坏。 */
	private boolean applyFlightDurability(ServerLevel level, Entity owner) {
		if (this.getItem().isDamageableItem() && !this.getItem().isBroken()) {
			this.getItem().hurtAndBreak(1, level, owner instanceof ServerPlayer player ? player : null, broken -> {
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8F, 1.0F);
			});
		}
		return this.getItem().isEmpty();
	}

	/** 优先回填原手持槽，其次背包空位。 */
	private boolean returnToInventory(Player player) {
		Inventory inventory = player.getInventory();
		if (this.throwSlot >= 0 && this.throwSlot < inventory.getContainerSize() && inventory.getItem(this.throwSlot).isEmpty()) {
			inventory.setItem(this.throwSlot, this.getItem());
			return true;
		}
		return inventory.add(this.getItem());
	}

	/** 兜底掉落（10 秒上限/投掷者不可用）：坠入虚空不掉落，其余就地掉落。 */
	private void dropAllAndDiscard(ServerLevel level) {
		if (this.getY() >= level.getMinY() - 32.0) {
			// 创造模式不掉落回旋镖本体（手里未消耗，掉落即复制）；拾取物照常掉落
			Entity owner = this.getOwner();
			boolean creative = owner instanceof Player p && p.hasInfiniteMaterials();
			if (!creative) {
				level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(), this.getZ(), this.getItem()));
			}
			for (ItemStack stack : this.collected) {
				level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(), this.getZ(), stack));
			}
		}
		this.discard();
	}

	private void updateRotationFromVelocity() {
		Vec3 vel = this.getDeltaMovement();
		double horizontal = vel.horizontalDistance();
		this.setYRot((float) (Math.toDegrees(Math.atan2(vel.x, vel.z))));
		this.setXRot((float) (Math.toDegrees(Math.atan2(vel.y, horizontal))));
		this.yRotO = this.getYRot();
		this.xRotO = this.getXRot();
	}
}
