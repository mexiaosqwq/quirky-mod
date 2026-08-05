package dev.quirky.parrotegg;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.parrot.Parrot;

/**
 * 鹦鹉蛋孵化的纯判定逻辑（随机源注入，可单测）。
 * 孵化率：基础 1/8（12.5%，对齐原版鸡鸡蛋）；落在丛林树叶/原木上 +1/8 → 25%；
 * 成功孵化时有 1/32 概率出双胞胎（对齐原版鸡一次出 4 只的概率）。
 * 鹦鹉无幼年形态（Parrot.canBeABaby() = false），孵出即成体，由 Parrot 构造器自动随机选色。
 */
public final class ParrotEggHatchLogic {

	/** 基础孵化率：1/8（对齐原版鸡蛋）。 */
	public static final float BASE_HATCH_CHANCE = 1.0F / 8.0F;
	/** 丛林树叶/原木加成（1/8，使总概率翻倍到 25%）。 */
	public static final float JUNGLE_BOOST = 1.0F / 8.0F;
	/** 双胞胎概率：1/32（对齐原版鸡一次出 4 只）。 */
	public static final float TWIN_CHANCE = 1.0F / 32.0F;

	private ParrotEggHatchLogic() {
	}

	/** 孵化结果：0 = 失败；1 = 一只；2 = 双胞胎。
	 * @param hatchChance 基础/丛林加成后的孵化率（实体侧按落点判定传入） */
	public static int hatchCount(RandomSource random, float hatchChance) {
		if (random.nextFloat() >= hatchChance) {
			return 0;
		}
		return random.nextFloat() < TWIN_CHANCE ? 2 : 1;
	}

	/** 落在丛林树叶/丛林原木上的孵化率加成（基础 12.5% → 25%）。 */
	public static float jungleBoost() {
		return BASE_HATCH_CHANCE + JUNGLE_BOOST;
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
}
