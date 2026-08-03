package dev.quirky.boomerang;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 回旋镖飞行物理的纯数学函数（单测覆盖）。
 * 出程：无重力直线积分 + 轻微右偏弧线（约每格 0.03 弧度）；
 * 返程：速度向量向投掷者当前位置转向（每 tick 最多 turnRate 弧度，保持速度大小）。
 */
public final class BoomerangPhysics {
	private BoomerangPhysics() {
	}

	/** 线性步进：位置积分（无重力）。 */
	public static Vec3 step(Vec3 pos, Vec3 vel, double dt) {
		return pos.add(vel.scale(dt));
	}

	/**
	 * 出程右偏弧线：把速度水平分量绕 Y 轴旋转 arcRadians（右偏），垂直分量不变，速度大小不变。
	 */
	public static Vec3 applyArc(Vec3 vel, double arcRadians) {
		double cos = Math.cos(arcRadians);
		double sin = Math.sin(arcRadians);
		double x = vel.x * cos - vel.z * sin;
		double z = vel.x * sin + vel.z * cos;
		return new Vec3(x, vel.y, z);
	}

	/**
	 * 返程转向：把 vel 朝 target 方向旋转，单步最多 turnRate 弧度（不超调），保持速度大小。
	 */
	public static Vec3 returnVector(Vec3 pos, Vec3 target, Vec3 vel, double turnRate) {
		Vec3 toTarget = target.subtract(pos);
		if (toTarget.lengthSqr() < 1e-12) {
			return vel;
		}
		toTarget = toTarget.normalize();
		Vec3 current = vel.normalize();
		double dot = Mth.clamp(current.dot(toTarget), -1.0, 1.0);
		double angle = Math.acos(dot);
		if (angle < 1e-6) {
			return vel;
		}
		Vec3 axis = current.cross(toTarget);
		if (axis.lengthSqr() < 1e-12) {
			// 完全背向：叉积退化（0 向量），任取一个垂直于速度的轴
			axis = current.cross(Vec3.Y_AXIS);
			if (axis.lengthSqr() < 1e-12) {
				axis = current.cross(Vec3.Z_AXIS);
			}
		}
		return rotateAroundAxis(vel, axis.normalize(), Math.min(angle, turnRate));
	}

	/** Rodrigues 旋转：向量 v 绕单位轴 k 旋转 theta 弧度（保持长度）。 */
	private static Vec3 rotateAroundAxis(Vec3 v, Vec3 k, double theta) {
		double cos = Math.cos(theta);
		double sin = Math.sin(theta);
		return v.scale(cos).add(k.cross(v).scale(sin)).add(k.scale(k.dot(v) * (1.0 - cos)));
	}
}
