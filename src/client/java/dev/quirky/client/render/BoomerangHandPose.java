package dev.quirky.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 回旋镖第一人称蓄力握持姿势（替代原版 TRIDENT 长杆姿势，贴实回旋镖投掷手感）。
 *
 * <p>在 {@code ItemInHandRenderer.submitArmWithItem} 的 {@code renderItem} 调用前叠加：
 * 回旋镖竖向握持（V 片竖起、开口朝前）+ 蓄力后摆（往后下方拉）+ 满蓄微抖动。
 * 所有数值为初值，标注 {@code @可调}，需桌面实测微调手感。
 */
public final class BoomerangHandPose {
	/** 满蓄 tick（与 BoomerangItem.FULL_CHARGE_TICKS 一致）。 */
	private static final int FULL_CHARGE_TICKS = 20;
	/** 抖动起算力度（>70% 蓄力开始抖）。 */
	private static final float SHAKE_THRESHOLD = 0.7F;

	private BoomerangHandPose() {
	}

	/**
	 * @param invert  +1 = 右手（主手），-1 = 左手（副手）
	 * @param player  投掷者（读取蓄力进度）
	 * @param using   正在使用的物品堆叠
	 */
	public static void applyChargePose(PoseStack pose, Player player, ItemStack using, int invert) {
		int duration = using.getUseDuration(player);
		int remaining = player.getUseItemRemainingTicks();
		float timeHeld = duration - remaining;
		float power = Math.min(1.0F, timeHeld / FULL_CHARGE_TICKS);

		// —— 静态握持：回旋镖从平铺手心旋转到竖向（V 片竖起、开口朝前）—— @可调
		pose.mulPose(Axis.XP.rotationDegrees(-80.0F));
		pose.mulPose(Axis.YP.rotationDegrees(invert * 45.0F));
		// 移到肩侧前方 —— @可调
		pose.translate(invert * 0.25F, -0.15F, 0.4F);

		// —— 蓄力后摆：往后下方拉（蓄力越满后摆越大）—— @可调
		pose.translate(invert * -0.1F * power, 0.15F * power, -0.3F * power);
		pose.mulPose(Axis.XP.rotationDegrees(25.0F * power));

		// —— 满蓄抖动：力度 >70% 时轻微震颤，给“绷紧”反馈 —— @可调
		if (power > SHAKE_THRESHOLD) {
			float shake = Mth.sin(timeHeld * 3.0F) * (power - SHAKE_THRESHOLD) * 0.04F;
			pose.translate(0.0F, shake, 0.0F);
		}
	}
}
