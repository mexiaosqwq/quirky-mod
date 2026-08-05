package dev.quirky.boomerang;

import net.minecraft.world.phys.Vec3;

/**
 * 回旋镖飞行物理的纯数学函数(单测覆盖)。
 *
 * <p>运动模型分两支，位置由 {@link #step} 线性积分：
 * <ul>
 *   <li><b>出程</b>：precess(弧线) → converge(水平收敛) → modulateSpeed(近快远慢) → springVertical(returnRamp=0，保留投掷仰角 initialVelY，不锁高度)。近处近似直线可命中敌人。</li>
 *   <li><b>返程</b>：precess(弧线) → converge3D(3D 同步收敛) → returnSpeed(近处慢漂、远处快赶，防高速穿过漏检接住)。</li>
 * </ul>
 *
 * <ul>
 *   <li>{@link #precess} 速度水平分量绕 Y 轴持续偏转，方向由 {@code clockwise} 决定
 *       (true = 投掷者右手边,俯视顺时针)。</li>
 *   <li>{@link #converge} 水平方向朝投掷者 blend(保持垂直分量)——出程用。</li>
 *   <li>{@link #converge3D} 整个速度方向(含垂直)朝投掷者 blend，垂直水平按几何比例同步收敛——返程用，
 *       根治旧独立垂直弹簧「先垂直下再水平回」的收敛不同步。</li>
 *   <li>{@link #modulateSpeed}（出程）近快远慢；{@link #returnSpeed}（返程）近慢远快。</li>
 *   <li>{@link #springVertical} 出程保留投掷仰角(returnRamp=0 → initialVelY)；临界阻尼弹簧逻辑保留供出程/单测。</li>
 * </ul>
 */
public final class BoomerangPhysics {
	private BoomerangPhysics() {
	}

	/** 临界阻尼弹簧刚度 K（追踪目标 y）。ω=√K=0.5，收敛时间 ~6-8 tick；C=2√K=1.0 临界阻尼无超调无振荡。 */
	public static final double SPRING_K = 0.25;

	/**
	 * 平滑阶跃：x≤0→0，x≥1→1，中间 S 形过渡（3x²−2x³）。用于按距离平滑触发返程偏转/收敛。
	 */
	public static double smoothstep(double x) {
		if (x <= 0.0) {
			return 0.0;
		}
		if (x >= 1.0) {
			return 1.0;
		}
		return x * x * (3.0 - 2.0 * x);
	}

	/** 线性步进:位置积分(无重力)。 */
	public static Vec3 step(Vec3 pos, Vec3 vel, double dt) {
		return pos.add(vel.scale(dt));
	}

	/**
	 * 进动偏转:速度水平分量绕 Y 轴旋转 {@code rate} 弧度,垂直分量与速度大小不变。
	 *
	 * @param clockwise true = 俯视顺时针(投掷者右手边);false = 俯视逆时针(左手边)
	 */
	public static Vec3 precess(Vec3 vel, double rate, boolean clockwise) {
		if (rate == 0.0) {
			return vel;
		}
		double theta = clockwise ? rate : -rate;
		double cos = Math.cos(theta);
		double sin = Math.sin(theta);
		double x = vel.x * cos - vel.z * sin;
		double z = vel.x * sin + vel.z * cos;
		return new Vec3(x, vel.y, z);
	}

	/**
	 * 朝投掷者收敛:水平方向朝 {@code ownerPos} blend {@code strength}(0=不收敛,1=完全指向),
	 * 保持速度大小与垂直分量。用于让连续弧线终点收敛到(可能移动的)投掷者。
	 */
	public static Vec3 converge(Vec3 vel, Vec3 pos, Vec3 ownerPos, double strength) {
		double dx = ownerPos.x - pos.x;
		double dz = ownerPos.z - pos.z;
		double ownerDist = Math.sqrt(dx * dx + dz * dz);
		if (ownerDist < 1e-9) {
			return vel;
		}
		double velHorizLen = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
		if (velHorizLen < 1e-9) {
			return vel;
		}
		double toOwnerX = dx / ownerDist;
		double toOwnerZ = dz / ownerDist;
		double curDirX = vel.x / velHorizLen;
		double curDirZ = vel.z / velHorizLen;
		double blendX = curDirX * (1.0 - strength) + toOwnerX * strength;
		double blendZ = curDirZ * (1.0 - strength) + toOwnerZ * strength;
		double blendLen = Math.sqrt(blendX * blendX + blendZ * blendZ);
		if (blendLen < 1e-9) {
			return vel;
		}
		return new Vec3(blendX / blendLen * velHorizLen, vel.y, blendZ / blendLen * velHorizLen);
	}

	/**
	 * 3D 朝投掷者收敛:整个速度方向(含垂直)朝 {@code ownerPos} blend {@code strength},
	 * 保持速度大小。返程用——垂直水平按几何比例同步收敛,避免「先垂直下来再转回」(垂直弹簧独立于水平导致的时间尺度不匹配)。
	 */
	public static Vec3 converge3D(Vec3 vel, Vec3 pos, Vec3 ownerPos, double strength) {
		double dx = ownerPos.x - pos.x;
		double dy = ownerPos.y - pos.y;
		double dz = ownerPos.z - pos.z;
		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (dist < 1e-9) {
			return vel;
		}
		double speed = vel.length();
		if (speed < 1e-9) {
			return vel;
		}
		double toOwnerX = dx / dist;
		double toOwnerY = dy / dist;
		double toOwnerZ = dz / dist;
		double curDirX = vel.x / speed;
		double curDirY = vel.y / speed;
		double curDirZ = vel.z / speed;
		double blendX = curDirX * (1.0 - strength) + toOwnerX * strength;
		double blendY = curDirY * (1.0 - strength) + toOwnerY * strength;
		double blendZ = curDirZ * (1.0 - strength) + toOwnerZ * strength;
		double blendLen = Math.sqrt(blendX * blendX + blendY * blendY + blendZ * blendZ);
		if (blendLen < 1e-9) {
			return vel;
		}
		double newDirX = blendX / blendLen;
		double newDirY = blendY / blendLen;
		double newDirZ = blendZ / blendLen;
		// 防 180° 反向掉头失效：线性 blend 在速度强背向投掷者(dot<-0.5，如竖直上抛到顶)时掉不了头，
		// 此时直接朝投掷者方向，保证返程能回来(不飞走/不打转)。正常弧线返程切线方向 dot≈0 不触发。
		double dot = newDirX * toOwnerX + newDirY * toOwnerY + newDirZ * toOwnerZ;
		if (dot < -0.5) {
			newDirX = toOwnerX;
			newDirY = toOwnerY;
			newDirZ = toOwnerZ;
		}
		return new Vec3(newDirX * speed, newDirY * speed, newDirZ * speed);
	}

	/**
	 * 速度调制（出程）：近处全速、远端减速。基于 {@code baseSpeed} 不累积衰减。
	 */
	public static Vec3 modulateSpeed(Vec3 vel, double baseSpeed, double dist, double maxRange, double minScale) {
		double t = maxRange < 1e-9 ? 0.0 : Math.max(0.0, Math.min(1.0, dist / maxRange));
		double scale = minScale + (1.0 - minScale) * (1.0 - t);
		double targetSpeed = baseSpeed * scale;
		double curLen = vel.length();
		if (curLen < 1e-9) {
			return vel;
		}
		return vel.scale(targetSpeed / curLen);
	}

	/**
	 * 速度调制（返程）：近处减速、远处全速——与出程相反。接近投掷者时低速漂移，避免高速穿过漏检接住判定；
	 * 远处全速赶紧往回赶。基于 {@code baseSpeed} 不累积衰减。
	 */
	public static Vec3 returnSpeed(Vec3 vel, double baseSpeed, double dist, double maxRange, double minScale) {
		double t = maxRange < 1e-9 ? 0.0 : Math.max(0.0, Math.min(1.0, dist / maxRange));
		double scale = minScale + (1.0 - minScale) * t;
		double targetSpeed = baseSpeed * scale;
		double curLen = vel.length();
		if (curLen < 1e-9) {
			return vel;
		}
		return vel.scale(targetSpeed / curLen);
	}

	/**
	 * 垂直速度：出程完全保留 {@code initialVelY}（投掷方向，仰投爬高/俯投下探，不锁高度）。
	 *
	 * <p><b>当前用法：Entity 仅出程调用(returnRamp=0)，返回 initialVelY。</b>返程已改用 {@link #converge3D}
	 * (垂直水平同步收敛)，不再经此函数的弹簧分支。returnRamp>0 的弹簧 blend 保留供单测验证临界阻尼行为。
	 *
	 * <p>动力学:accel = K*yDiff - C*velY，C = 2√K 临界阻尼。半隐式 Euler:returnVel = currentVelY + accel*dt。
	 * dt=1 时 C·dt=1 抵消 currentVelY，returnVel = K*yDiff（一阶指数逼近，单调收敛无振荡）。
	 *
	 * @param currentVelY 当前垂直速度（dt=1 时不生效）
	 * @param returnRamp  过渡值 [0,1]；Entity 出程传 0(保留 initialVelY)
	 * @param initialVelY 投掷时的初始垂直速度分量（出程保留投掷方向）
	 * @param yDiff       投掷者 y - 回旋镖 y（弹簧分支追踪方向）
	 * @param dt          步长（tick）；生产恒为 1.0
	 * @return 本帧垂直速度
	 */
	public static double springVertical(double currentVelY, double returnRamp, double initialVelY, double yDiff, double dt) {
		double k = SPRING_K;
		double c = 2.0 * Math.sqrt(k);
		double accel = k * yDiff - c * currentVelY;
		double returnVel = currentVelY + accel * dt;
		return (1.0 - returnRamp) * initialVelY + returnRamp * returnVel;
	}
}
