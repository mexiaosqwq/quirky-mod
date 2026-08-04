package dev.quirky.mixin;

import dev.quirky.particle.DyedCampfireSmokeOption;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 统一灭火清零：注入 {@link BlockEntity#setBlockState}（基类方法，方块状态变化时被调），
 * 检测营火 LIT 从 true→false（任何灭火路径：水桶 placeLiquid / 铲子扑灭 / 喷溅水瓶
 * 投射物 / 活塞推等）即清除染色烟与夜光及其倒计时，重新点燃后是无色烟。
 *
 * <p>方法选择器只匹配本类成员——{@code setBlockState} 声明在 {@link BlockEntity} 本类，
 * @Mixin(BlockEntity) + {@code instanceof CampfireBlockEntity} 过滤，与项目
 * {@code LocalPlayerAIStepMixin}（@Mixin(Player) + instanceof LocalPlayer）同模式。</p>
 *
 * <p>HEAD 注入：此时 {@code this.blockState} 仍是旧值（原版 setBlockState 第一行才赋值），
 * 可拿到旧 LIT 与参数 newState 的 LIT 比较，判定 true→false 转换。</p>
 */
@Mixin(BlockEntity.class)
public abstract class CampfireBlockEntityStateMixin {

	@Inject(method = "setBlockState(Lnet/minecraft/world/level/block/state/BlockState;)V", at = @At("HEAD"))
	private void quirky$clearSmokeStateOnExtinguish(BlockState newState, CallbackInfo ci) {
		BlockEntity self = (BlockEntity) (Object) this;
		if (!(self instanceof CampfireBlockEntity campfire)) {
			return;
		}
		BlockState oldState = self.getBlockState();
		// 仅处理营火 LIT 从 true→false 的灭火转换
		if (!(oldState.getBlock() instanceof CampfireBlock)) {
			return;
		}
		if (!oldState.getValue(CampfireBlock.LIT) || newState.getValue(CampfireBlock.LIT)) {
			return; // 旧未点燃 或 新仍点燃：非灭火，跳过
		}
		CampfireBlockEntityAccessor accessor = (CampfireBlockEntityAccessor) campfire;
		if (accessor.quirky$getSmokeColor() != -1 || accessor.quirky$getGlow()
			|| accessor.quirky$getSmokeColorTicks() != 0 || accessor.quirky$getGlowTicks() != 0) {
			accessor.quirky$setSmokeColor(-1);
			accessor.quirky$setSmokeColorTicks(0);
			accessor.quirky$setGlow(false);
			accessor.quirky$setGlowTicks(0);
			campfire.setChanged();
		}
	}
}
