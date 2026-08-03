package dev.quirky.parrotegg;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.parrot.Parrot;

/**
 * 鹦鹉蛋孵化的纯判定逻辑（随机源注入，可单测）。
 * 孵化率：基础 50%，落在丛林树叶/原木上 +25%；成功孵化时有 1/32 概率出双胞胎。
 * 鹦鹉无幼年形态（Parrot.canBeABaby() = false），孵出即成体，由 Parrot 构造器自动随机选色。
 */
public final class ParrotEggHatchLogic {

	private ParrotEggHatchLogic() {
	}

	/** 孵化结果：0 = 失败；1 = 一只；2 = 双胞胎。 */
	public static int hatchCount(RandomSource random, float hatchChance, float twinChance) {
		if (random.nextFloat() >= clamp01(hatchChance)) {
			return 0;
		}
		return random.nextFloat() < clamp01(twinChance) ? 2 : 1;
	}

	/** 落在丛林树叶/丛林原木上的孵化率加成（默认 50% → 75%）。 */
	public static float jungleBoost(float hatchChance) {
		return Math.min(1.0F, clamp01(hatchChance) + 0.25F);
	}

	/** 碎壳粒子颜色跟随鹦鹉羽色（近似色，RGB）。 */
	public static int shellColor(Parrot.Variant variant) {
		return switch (variant) {
			case RED_BLUE -> 0xC0392B;
			case BLUE -> 0x2E6DB4;
			case GREEN -> 0x4E9A3F;
			case YELLOW_BLUE -> 0xF2C40F;
			case GRAY -> 0x8F8F8F;
		};
	}

	/** 孵化失败时随机取一种羽色作为碎壳色。 */
	public static int randomShellColor(RandomSource random) {
		return shellColor(Parrot.Variant.byId(random.nextInt(5)));
	}

	private static float clamp01(float value) {
		return Math.clamp(value, 0.0F, 1.0F);
	}
}
