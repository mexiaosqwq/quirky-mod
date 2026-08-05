package dev.quirky.boomerang;

import net.minecraft.world.phys.Vec3;

/**
 * 回旋镖飞行物理的纯数学函数(单测覆盖)。
 *
 * <p>连续进动弧线模型:每帧依次
 * <pre>precess → converge → modulateSpeed → heightVelocity</pre>
 * 位置由 {@link #step} 线性积分。全程连续无硬切换——返程由进动(速度持续偏转,自然转过约 180° 指回投掷者)
 * 与收敛(朝投掷者当前位置 blend)共同产生,而非距离阈值触发。
 *
 * <ul>
 *   <li>{@link #precess} 速度水平分量绕 Y 轴持续偏转,方向由 {@code clockwise} 决定
 *       (true = 投掷者右手边,俯视顺时针)。</li>
 *   <li>{@link #converge} 水平方向朝投掷者 blend,保证弧线终点收敛到(可能移动的)投掷者;保持速度大小与垂直分量。</li>
 *   <li>{@link #modulateSpeed} 远端减速、近端全速,产生"出程减速→返程回升"的手感。</li>
 *   <li>{@link #heightVelocity} 远端抬升的高度起伏速度(正弦位置偏移的导数),通过 vel.y 实现自然积分。</li>
 * </ul>
 */
public final class BoomerangPhysics {
	private BoomerangPhysics() {
	}

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
	 * 高度起伏速度:位置偏移 {@code amplitude * sin(π * progress)} 对时间的导数。
	 * progress=0 向上(开始抬升去远端),0.5 为零(远端峰顶),1 向下(回落回手)。
	 *
	 * @param progress    飞行进度 [0,1](lifetime / 预期总时长)
	 * @param amplitude   远端抬升幅度(格)
	 * @param totalTicks  预期总飞行 tick(progress 归一化基准)
	 */
	public static double heightVelocity(double progress, double amplitude, int totalTicks) {
		if (totalTicks <= 0) {
			return 0.0;
		}
		return amplitude * Math.PI * Math.cos(Math.PI * progress) / totalTicks;
	}

	/**
	 * 垂直速度：出程(trigger→0)保留初始仰角的垂直分量 {@code initialVelY}，返程(trigger→1)过渡到高度起伏 {@link #heightVelocity}。
	 * 平视投掷 initialVelY≈0 → 弧高小；仰投 initialVelY>0 → 弧高大。blend 用 trigger 平滑过渡无硬切换。
	 *
	 * @param trigger      距离/返程触发值 [0,1]（出程 0、返程 1）
	 * @param initialVelY  投掷时的初始垂直速度分量（由仰角决定）
	 */
	public static double verticalVelocity(double trigger, double initialVelY, double progress, double amplitude, int totalTicks) {
		double height = heightVelocity(progress, amplitude, totalTicks);
		return (1.0 - trigger) * initialVelY + trigger * height;
	}
}
