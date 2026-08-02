package dev.quirky.client.mixin;

import dev.quirky.client.ladder_snap.LadderSnapHelper;
import dev.quirky.config.QuirkyConfigHolder;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 爬梯吸附（spec 5.9）：LocalPlayer.aiStep 末尾（本 tick 输入已 tick、移动已执行）——
 * 开关开启、在梯子/藤蔓上爬行、未按左右键时，朝所在梯子方块中心线叠加修正速度，
 * 玩家松手后身体平滑吸回梯子中心；按住左右键以手动控制优先，不干预。
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerAIStepMixin {
	@Inject(method = "aiStep", at = @At("TAIL"))
	private void quirky$snapToLadderCenter(CallbackInfo ci) {
		LocalPlayer player = (LocalPlayer) (Object) this;
		if (!QuirkyConfigHolder.get().ladderSnap || !player.onClimbable()) {
			return;
		}
		// 手动左右控制优先：按住 A/D 时不做吸附干预
		if (player.input.keyPresses.left() || player.input.keyPresses.right()) {
			return;
		}
		BlockPos ladderPos = findClimbableBlock(player);
		if (ladderPos == null) {
			return;
		}
		Vec2 correction = LadderSnapHelper.correction(
			player.getX(), player.getZ(),
			ladderPos.getX() + 0.5, ladderPos.getZ() + 0.5,
			QuirkyConfigHolder.get().ladderSnapStrength
		);
		player.setDeltaMovement(player.getDeltaMovement().add(correction.x, 0.0, correction.y));
	}

	/**
	 * 找玩家当前所在的梯子/藤蔓方块：优先玩家自身所在格（与 {@code onClimbable} 的判定一致），
	 * 否则查水平四邻（藤蔓/梯子边缘悬空时玩家身体可能略偏出所在格）。
	 */
	private static BlockPos findClimbableBlock(LocalPlayer player) {
		BlockPos pos = player.blockPosition();
		if (isClimbable(pos, player)) {
			return pos;
		}
		for (BlockPos neighbor : new BlockPos[] { pos.north(), pos.south(), pos.east(), pos.west() }) {
			if (isClimbable(neighbor, player)) {
				return neighbor;
			}
		}
		return null;
	}

	private static boolean isClimbable(BlockPos pos, LocalPlayer player) {
		BlockState state = player.level().getBlockState(pos);
		return LadderSnapHelper.isClimbableTarget(state);
	}
}
