package dev.quirky.entity;

import dev.quirky.ModEntities;
import dev.quirky.boomerang.BoomerangBlockLogic;
import dev.quirky.boomerang.BoomerangPhysics;
import dev.quirky.config.QuirkyConfigHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
 * 回旋镖飞行实体：自实现 tick 物理，出程/返程分支。出程 precess(弧线) → converge(水平收敛) →
 * 速度调制(近快远慢) → 保留投掷仰角(不锁高度)；返程 precess(弧线) → converge3D(垂直水平同步收敛) →
 * returnSpeed(近慢远快)。
 * 出程按距离 smoothstep 触发，到达峰值距离或撞墙后进入返程（returning 硬切换 + returnSpeed 反向减速），
 * 可靠回手。飞行中拾取地面物品（记入 NBT 列表），触碰生物造成轻伤+击退（每生物每次飞行只判定一次），
 * 撞方块小概率打碎（秒破类必碎，免疫 tag / 冒险模式不打碎）——未打碎则化为掉落物。
 * 耐久扣回玩家背包真实堆叠（consumed 快照判定归还/掉落，防切换游戏模式导致删除/复制）。
 * 投掷者 UUID 由 {@link Projectile#getOwner()}（EntityReference）查找，防内存悬挂。
 */
public class BoomerangEntity extends Projectile implements ItemSupplier {
	/** 进动偏转速率（弧度/tick；可调）。仅在远端（dist 接近 maxRange）通过 smoothstep 触发，出程近似直线可命中敌人。 */
	private static final double PRECESSION_RATE = 0.25;
	/** 朝投掷者收敛强度（每帧水平方向 blend 比例；可调）。同样按距离触发，返程强收敛快速回手。 */
	private static final double CONVERGE_STRENGTH = 0.35;
	/** 远端最低速度倍率（可调）。0.3 让远端明显减速。 */
	private static final double MIN_SPEED_SCALE = 0.3;
	/** 空气阻力衰减（每帧 vel*=AIR_DRAG，模拟回旋镖越飞越慢；可调）。0.99 让 20 tick 后速度保留 82%。 */
	private static final double AIR_DRAG = 0.99;
	/** 返程 trigger 平滑升速（每帧增量；可调）。从 returning 触发时的 smoothstep 值单调升到 1.0，防 trigger 硬跳变导致垂直 vel 突变。 */
	private static final double RETURN_RAMP_RATE = 0.15;
	/** 回手判定水平距离 2.0 格（平方；可调）。略放大让接住更宽容。 */
	private static final double CATCH_DISTANCE_SQ = 4.0;
	/** 回手判定垂直容差 1.5 格（跳跃/下落时仍能接住；可调）。 */
	private static final double CATCH_VERTICAL_TOLERANCE = 1.5;
	/** 返程牵引半径(可调)：玩家离投掷点超过此距离，回旋镖不再追，飞回投掷点掉落——避免无限跟随移动的玩家。 */
	private static final double LEASH_RADIUS = 8.0;
	/** 命中生物固定伤害（武器化后不再走配置；对齐近战属性 4）。 */
	private static final float HIT_DAMAGE = 4.0F;
	/** 10 秒兜底自毁上限。 */
	private static final int MAX_LIFETIME_TICKS = 200;
	/** 初始速度基准（格/tick；可调）。实例化时按蓄力 power 缩放。 */
	private static final double BASE_THROW_SPEED = 0.7;

	private static final String TAG_RETURNING = "Returning";
	private static final String TAG_HITS = "HitEntities";
	private static final String TAG_COLLECTED = "CollectedItems";
	private static final String TAG_MAX_RANGE = "MaxRange";
	private static final String TAG_THROW_SLOT = "ThrowSlot";
	private static final String TAG_TRAVELED = "TraveledDistance";
	private static final String TAG_HIT_UUID_MOST = "M";
	private static final String TAG_HIT_UUID_LEAST = "L";
	private static final String TAG_PEAK_DISTANCE = "PeakDistance";

	/** 命中检测盒膨胀（格；可调）。越大越容易碰到敌人。 */
	private static final double HIT_INFLATE = 1.5;
	/** 物品同步：entityData 广播到客户端（渲染器用），照原版 ThrowableItemProjectile 的 DATA_ITEM_STACK 模式。 */
	private static final EntityDataAccessor<ItemStack> DATA_ITEM_STACK = SynchedEntityData.defineId(
		BoomerangEntity.class, EntityDataSerializers.ITEM_STACK
	);
	/** 进动方向同步：true=右手回旋(主手)，false=左手回旋(副手)。客户端预测物理需读取。 */
	private static final EntityDataAccessor<Boolean> DATA_CLOCKWISE = SynchedEntityData.defineId(
		BoomerangEntity.class, EntityDataSerializers.BOOLEAN
	);
	/** 初始速度同步：客户端预测物理需用真实初速（蓄力后 ≠ 默认 0.7），否则预测步长偏离服务端。 */
	private static final EntityDataAccessor<Float> DATA_THROW_SPEED = SynchedEntityData.defineId(
		BoomerangEntity.class, EntityDataSerializers.FLOAT
	);
	/** 射程同步：客户端预测的 smoothstep 触发依赖真实 maxRange（蓄力后 ≠ 默认 12）。 */
	private static final EntityDataAccessor<Integer> DATA_MAX_RANGE = SynchedEntityData.defineId(
		BoomerangEntity.class, EntityDataSerializers.INT
	);

	private final List<ItemStack> collected = new ArrayList<>();
	private final Set<UUID> hitEntities = new HashSet<>();
	/** 连续模型已废弃出程/返程切换；保留字段仅供旧存档 NBT 兼容读取。
	 * 注：returning 仍活跃使用（撞墙后/峰值后硬切换返程 + returnSpeed 反向减速），字段注释仅指旧阈值模型。 */
	private boolean returning;
	/** 出程飞行峰值距离，用于检测开始返程（dist 开始减小）。 */
	private double peakDistance;
	/** 返程 trigger 平滑值：returning 触发后每帧 +RETURN_RAMP_RATE 升到 1.0（垂直过渡用，防弹簧权重突变）。 */
	private double returnRamp;
	/** 投掷时的初始垂直速度分量（首帧 tick 记录 vel.y；spawn 包必带 velocity，两端首帧快照一致），用于出程保留投掷方向。 */
	private double initialVelY;
	/** 投掷点(home)：首帧记录 position()。返程牵引锚点——玩家在 LEASH_RADIUS 内则追踪接住，跑远则飞回此处掉落。两端首帧各自记录(spawn 位置一致)，无需同步。 */
	private Vec3 homePos;
	private int maxRange = 12;
	private double throwSpeed = BASE_THROW_SPEED;
	/** 投掷时是否已从玩家背包消耗物品（生存模式投掷=已消耗；创造=未消耗）。
	 * 用投掷时快照判定归还/掉落，防飞行中切换游戏模式导致物品删除/复制。 */
	private boolean consumed;
	private int throwSlot = -1;
	/** 连续模型已废弃距离阈值；保留字段仅供旧存档 NBT 兼容读取。 */
	private double traveledDistance;
	private int lifetimeTicks;

	public BoomerangEntity(EntityType<? extends BoomerangEntity> type, Level level) {
		super(type, level);
	}

	public BoomerangEntity(ServerLevel level, Player owner, ItemStack item, int throwSlot, boolean clockwise, float power, boolean consumed) {
		super(ModEntities.BOOMERANG, level);
		this.setItem(item);
		this.throwSlot = throwSlot;
		this.setClockwise(clockwise);
		// 蓄力缩放：力度 0.4~1.5 → 初速 0.28~1.05、射程 5~18 格（钳制）
		this.throwSpeed = BASE_THROW_SPEED * power;
		this.maxRange = Math.max(5, Math.min(20, (int) (QuirkyConfigHolder.get().boomerangRange * power)));
		this.setThrowSpeed(this.throwSpeed);
		this.setMaxRange(this.maxRange);
		this.consumed = consumed;
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

	/** 进动方向（true=右手回旋/主手，false=左手回旋/副手）。客户端预测物理读取此值。 */
	public boolean isClockwise() {
		return this.getEntityData().get(DATA_CLOCKWISE);
	}

	public void setClockwise(boolean clockwise) {
		this.getEntityData().set(DATA_CLOCKWISE, clockwise);
	}

	/** 初始速度（entityData 同步到客户端预测）。 */
	public double getThrowSpeed() {
		return this.getEntityData().get(DATA_THROW_SPEED);
	}

	public void setThrowSpeed(double throwSpeed) {
		this.getEntityData().set(DATA_THROW_SPEED, (float) throwSpeed);
	}

	/** 射程（entityData 同步到客户端预测）。 */
	public int getMaxRange() {
		return this.getEntityData().get(DATA_MAX_RANGE);
	}

	public void setMaxRange(int maxRange) {
		this.getEntityData().set(DATA_MAX_RANGE, maxRange);
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
		builder.define(DATA_ITEM_STACK, ItemStack.EMPTY);
		builder.define(DATA_CLOCKWISE, true);
		builder.define(DATA_THROW_SPEED, (float) BASE_THROW_SPEED);
		builder.define(DATA_MAX_RANGE, 12);
	}

	// ==== NBT ====

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean(TAG_RETURNING, this.returning);
		output.putBoolean("Consumed", this.consumed);
		output.putInt(TAG_MAX_RANGE, this.getMaxRange());
		output.putInt(TAG_THROW_SLOT, this.throwSlot);
		// traveledDistance 已废弃（连续模型不再用距离阈值）；保留读取兼容旧存档但不写入
		// peakDistance / returning 由返程逻辑运行时维护，存档保留
		output.putDouble(TAG_PEAK_DISTANCE, this.peakDistance);
		output.putBoolean("Clockwise", this.isClockwise());
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
		this.consumed = input.getBooleanOr("Consumed", false);
		this.maxRange = Math.max(5, Math.min(20, input.getIntOr(TAG_MAX_RANGE, 12)));
		this.setMaxRange(this.maxRange);
		this.setThrowSpeed(this.throwSpeed);
		this.throwSlot = input.getIntOr(TAG_THROW_SLOT, -1);
		// traveledDistance 旧存档兼容读取，不再使用
		this.peakDistance = input.getDoubleOr(TAG_PEAK_DISTANCE, 0.0);
		this.setClockwise(input.getBooleanOr("Clockwise", true));
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
		this.lifetimeTicks++;
		if (this.level().isClientSide()) {
			// 客户端预测：本地跑物理算位置，避免依赖服务端低频同步包造成卡顿跳跃。
			// 命中/接住/拾取等权威判定仅服务端执行，客户端只算运动。
			this.tickMovement(false);
			return;
		}
		this.tickMovement(true);
	}

	/**
	 * 每帧运动计算（两端共用）：precess → converge → modulateSpeed → springVertical → step。
	 * {@code server} 为 true 时额外执行命中/拾取/接住等权威判定与拖尾粒子。
	 */
	private void tickMovement(boolean server) {
		Level level = this.level();
		Entity owner = this.getOwner();
		if (server) {
			ServerLevel serverLevel = (ServerLevel) level;
			// 兜底：10 秒上限 / 坠入虚空（不掉落）/ 投掷者死亡·离线·跨维度（就地掉落）
			if (this.lifetimeTicks > MAX_LIFETIME_TICKS || this.getY() < level.getMinY() - 32.0) {
				this.dropAllAndDiscard(serverLevel);
				return;
			}
			if (owner == null || !owner.isAlive() || owner.level() != level) {
				this.dropAllAndDiscard(serverLevel);
				return;
			}
		}

		Vec3 pos = this.position();
		Vec3 vel = this.getDeltaMovement();
		if (vel.lengthSqr() < 1e-6) {
			vel = this.getLookAngle().scale(this.getThrowSpeed());
		}

		// 首帧记录投掷垂直分量 + 投掷点(home)：spawn 包必带 velocity/位置，两端首帧快照一致（客户端预测用真实仰角与 home，不平飞不丢 sync）。
		if (this.lifetimeTicks == 1) {
			this.initialVelY = vel.y;
			this.homePos = pos;
		}

		// 连续进动弧线：出程(precess 弧线 + converge 水平 + modulateSpeed + 保留仰角) / 返程(precess 弧线 + converge3D 同步收敛 + returnSpeed) → AIR_DRAG(空气阻力衰减)
		Vec3 ownerPos = owner != null ? owner.position().add(0.0, owner.getEyeHeight() * 0.5, 0.0) : pos;
		// 3D 距离(非水平)：竖直/大仰角上抛水平位移≈0，水平距离永不触发返程；用 3D 距离让任意投掷方向都能到阈返程
		double dist = pos.subtract(ownerPos).length();

		// 放弃判定：玩家跑出牵引半径且回旋镖已飞回投掷点附近 → 掉落物放弃，不无限追移动的玩家——仅服务端
		if (server && this.returning && owner != null && this.homePos != null
			&& ownerPos.distanceTo(this.homePos) > LEASH_RADIUS && pos.distanceTo(this.homePos) < 2.5) {
			this.dropAllAndDiscard((ServerLevel) level);
			return;
		}

		// 回手判定：仅返程触发（出程飞向远方不应接住，否则轻蓄力飞不出接住圈就被回收）；水平 ≤2.0 且 垂直差 ≤1.5——仅服务端
		if (server && this.returning && this.lifetimeTicks > 3 && pos.subtract(ownerPos).horizontalDistanceSqr() <= CATCH_DISTANCE_SQ
			&& Math.abs(pos.y - ownerPos.y) <= CATCH_VERTICAL_TOLERANCE) {
			this.retrieveFor((ServerLevel) level, owner);
			return;
		}

		// 距离触发 + 返程状态：出程 smoothstep(dist/maxRange) 近直线可命中；返程 returnRamp 从 0 平滑升到 1（垂直过渡用）。
		// 注意：returnRamp 从 0 开始而非触发时的 smoothstep——否则返程首帧弹簧大权重导致 vel.y 突变。水平 trigger 仍用 max(smoothstep, returnRamp)。
		if (!this.returning && (dist >= this.getMaxRange() * 0.7 || (this.lifetimeTicks > 5 && dist < this.peakDistance - 0.3))) {
			this.returning = true;
		}
		this.peakDistance = Math.max(this.peakDistance, dist);
		if (this.returning) {
			this.returnRamp = Math.min(1.0, this.returnRamp + RETURN_RAMP_RATE);
		}
		double trigger = BoomerangPhysics.smoothstep(this.getMaxRange() > 0 ? dist / this.getMaxRange() : 1.0);
		// 出程/返程分支：出程保留仰角(initialVelY)，返程用 3D 同步收敛(垂直水平同向量，防「先垂直下再水平回」)
		if (!this.returning) {
			// 出程：弧线偏转 + 水平收敛(保持 vel.y) + 近快远慢 + 保留投掷仰角
			vel = BoomerangPhysics.precess(vel, PRECESSION_RATE * trigger, this.isClockwise());
			if (owner != null) {
				vel = BoomerangPhysics.converge(vel, pos, ownerPos, CONVERGE_STRENGTH * trigger);
			}
			vel = BoomerangPhysics.modulateSpeed(vel, this.getThrowSpeed(), dist, this.getMaxRange(), MIN_SPEED_SCALE);
			vel = new Vec3(vel.x, BoomerangPhysics.springVertical(vel.y, 0.0, this.initialVelY, ownerPos.y - pos.y, 1.0), vel.z);
		} else {
			// 返程：牵引锚点——玩家在投掷点 LEASH_RADIUS 内则追踪接住；跑远则飞回投掷点(不无限追)
			// returnRamp 从 0 平滑升到 1：首帧 strength=0 不改方向(无突变)，逐渐接管为朝目标的 3D 弧线
			Vec3 returnAim = ownerPos;
			if (owner != null && this.homePos != null && ownerPos.distanceTo(this.homePos) > LEASH_RADIUS) {
				returnAim = this.homePos;
			}
			double returnDist = pos.distanceTo(returnAim);
			vel = BoomerangPhysics.precess(vel, PRECESSION_RATE * this.returnRamp, this.isClockwise());
			if (owner != null) {
				vel = BoomerangPhysics.converge3D(vel, pos, returnAim, CONVERGE_STRENGTH * this.returnRamp);
			}
			vel = BoomerangPhysics.returnSpeed(vel, this.getThrowSpeed(), returnDist, this.getMaxRange(), MIN_SPEED_SCALE);
		}

		// 空气阻力：每帧速度衰减（越飞越慢，模拟回旋镖空气阻力；出程返程都衰减）
		vel = vel.scale(AIR_DRAG);

		Vec3 newPos = BoomerangPhysics.step(pos, vel, 1.0);

		if (server) {
			ServerLevel serverLevel = (ServerLevel) level;
			// 实体命中（比方块优先，贴合原版弹射物流程）
			EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
				serverLevel, this, pos, newPos, this.getBoundingBox().expandTowards(vel).inflate(HIT_INFLATE), this::canHitEntity
			);
			if (entityHit != null && entityHit.getEntity() instanceof LivingEntity target) {
				// 穿透：命中生物不截断位置，回旋镝继续飞行可打多个敌人（每生物每次飞行只判一次，由 hitEntities 保证）
				this.hitLiving(target);
			} else {
				BlockHitResult blockHit = serverLevel.clipIncludingBorder(new ClipContext(pos, newPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
				if (blockHit.getType() != HitResult.Type.MISS) {
					newPos = blockHit.getLocation();
					this.handleBlockHit(serverLevel, blockHit);
					// 撞墙掉落(discard)后不再继续本帧 setPos/拾取等操作
					if (this.isRemoved()) {
						return;
					}
					// 打碎穿透：保持原速继续飞（handleBlockHit 内已处理），以实体最新速度为继续飞行的速度
					vel = this.getDeltaMovement();
				}
			}

			// 拾取地面物品（0.5 格内吸附）
			for (ItemEntity itemEntity : serverLevel.getEntities(
				EntityTypeTest.forClass(ItemEntity.class), this.getBoundingBox().expandTowards(vel).inflate(0.5), Entity::isAlive
			)) {
				this.absorb(itemEntity);
			}
		}

		this.setPos(newPos);
		// 飞行拖尾粒子：每 2 tick 在当前位置发 1 个端粒，稀疏运动痕迹（服务端 sendParticles；Level.addParticle 是空实现）
		if (server && (this.lifetimeTicks & 1) == 0) {
			((ServerLevel) level).sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY(), this.getZ(), 1, 0.08, 0.08, 0.08, 0.0);
		}
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

		// 未碎 → 在撞击点化为掉落物（不再反弹乱飞，根治绕圈刨方块）
		Vec3 hitPos = hit.getLocation().add(hit.getDirection().getUnitVec3().scale(0.2)); // 沿法线外移 0.2 格防卡方块
		level.playSound(null, hitPos.x, hitPos.y, hitPos.z, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.8F, 1.0F);
		// 投掷时已消耗（生存）→ 掉落本体可捡回；未消耗（创造投掷快照）→ 不生成防复制
		if (this.consumed) {
			level.addFreshEntity(new ItemEntity(level, hitPos.x, hitPos.y, hitPos.z, this.getItem()));
		}
		for (ItemStack stack : this.collected) {
			level.addFreshEntity(new ItemEntity(level, hitPos.x, hitPos.y, hitPos.z, stack));
		}
		this.discard();
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
		// 投掷时已消耗（生存）→ 方块掉落正常；创造投掷快照 → 方块不掉物
		level.destroyBlock(pos, this.consumed, this, 3);
		level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.8F, 1.0F);
		return true;
	}

	private void absorb(ItemEntity itemEntity) {
		this.collected.add(itemEntity.getItem());
		itemEntity.discard();
		this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 1.0F);
	}

	/** 回到投掷者：消耗 1 点耐久（归零则损坏消失），归还数量与拾取物。
	 * 耐久扣在玩家背包里的真实堆叠上（可堆叠物品共享耐久），数量用 grow(1) 归还——
	 * 不归还实体副本，避免"原堆叠(满耐久)+副本(扣过耐久)"两个不同耐久的物品无法堆叠占两格。
	 * 归还/损坏判定一律用投掷时的 consumed 快照（防飞行中切换游戏模式导致删除/复制）。 */
	private void retrieveFor(ServerLevel level, Entity owner) {
		if (owner instanceof Player player) {
			// 耐久与归还：投掷时已消耗（生存）→ 正常归还流程；未消耗（创造）→ 不归还不扣耐久
			boolean returned = this.returnStackToPlayer(player);
			if (returned) {
				player.playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.5F, 1.0F);
			}
			// 拾取物：放入背包，部分放入的剩余就地掉落（不吞物品）
			for (ItemStack stack : this.collected) {
				player.getInventory().add(stack);
				if (!stack.isEmpty()) {
					level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY(), player.getZ(), stack));
				}
			}
		} else {
			// 投掷者非玩家（理论不发生）：按 consumed 快照决定是否掉落本体，拾取物照常
			if (this.consumed) {
				level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(), this.getZ(), this.getItem()));
			}
			for (ItemStack stack : this.collected) {
				level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(), this.getZ(), stack));
			}
		}
		this.discard();
	}

	/**
	 * 归还物品：耐久扣在玩家背包里的真实堆叠（可堆叠共享耐久），数量 grow(1) 归还，
	 * 不归还实体副本（避免"原堆叠满耐久 + 副本扣过耐久"无法堆叠占两格）。
	 * 创造投掷（consumed=false）：不 grow 不扣耐久（数量从未变，防复制）。
	 * 单飞镖（背包无堆叠可扣）：扣副本耐久后放回手中（损坏则消失）。
	 * 返回 true = 成功回手；false = 损坏消失 / 背包满兜底掉落（不播回手音）。
	 */
	private boolean returnStackToPlayer(Player player) {
		Inventory inventory = player.getInventory();
		ItemStack flying = this.getItem();
		if (!this.consumed) {
			return true; // 创造投掷：物品数量从未减少，直接视为回手
		}
		// 找同种可堆叠堆叠（优先原手持槽位）
		ItemStack target = null;
		if (this.throwSlot >= 0 && this.throwSlot < inventory.getContainerSize()) {
			ItemStack slot = inventory.getItem(this.throwSlot);
			if (ItemStack.isSameItem(slot, flying) && slot.getCount() < slot.getMaxStackSize()) {
				target = slot;
			}
		}
		if (target == null) {
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				ItemStack slot = inventory.getItem(i);
				if (ItemStack.isSameItem(slot, flying) && slot.getCount() < slot.getMaxStackSize()) {
					target = slot;
					break;
				}
			}
		}
		if (target != null) {
			// 扣真实堆叠耐久；归零损坏 shrink(1) 移除一个，此时不 grow（数量守恒）
			target.hurtAndBreak(1, (ServerLevel) this.level(), player instanceof ServerPlayer sp ? sp : null, broken -> {
				this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8F, 1.0F);
			});
			if (!target.isEmpty()) {
				target.grow(1);
			}
			return true;
		}
		// 单飞镖：背包无堆叠 → 扣副本耐久后放回手中（损坏则消失）
		if (flying.isDamageableItem() && !flying.isBroken()) {
			flying.hurtAndBreak(1, (ServerLevel) this.level(), player instanceof ServerPlayer sp ? sp : null, broken -> {
				this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8F, 1.0F);
			});
		}
		if (flying.isEmpty()) {
			return false; // 损坏消失
		}
		// 优先放回原手持槽（清空引用防复制），其次背包空位；放不下则就地掉落
		if (this.throwSlot >= 0 && this.throwSlot < inventory.getContainerSize() && inventory.getItem(this.throwSlot).isEmpty()) {
			inventory.setItem(this.throwSlot, flying);
			flying = ItemStack.EMPTY;
		} else {
			inventory.add(flying); // add 失败余量留在 flying
		}
		if (!flying.isEmpty()) {
			this.level().addFreshEntity(new ItemEntity((ServerLevel) this.level(), player.getX(), player.getY(), player.getZ(), flying));
			return false;
		}
		return true;
	}

	/** 兜底掉落（10 秒上限/投掷者不可用）：坠入虚空不掉落，其余就地掉落。 */
	private void dropAllAndDiscard(ServerLevel level) {
		if (this.getY() >= level.getMinY() - 32.0) {
			// 投掷时已消耗（生存）→ 掉落本体可捡回；未消耗（创造投掷快照）→ 不生成防复制；拾取物照常
			if (this.consumed) {
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
