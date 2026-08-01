package dev.quirky.cloud;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class CloudPlacement {
	private static final double STEP = 0.25;

	private CloudPlacement() {
	}

	public static @Nullable BlockPos findNearestAir(Level level, Vec3 from, Vec3 look, double reach) {
		for (double t = STEP; t <= reach; t += STEP) {
			BlockPos pos = BlockPos.containing(from.add(look.scale(t)));
			if (level.getBlockState(pos).isAir()) {
				return pos;
			}
		}
		return null;
	}
}
