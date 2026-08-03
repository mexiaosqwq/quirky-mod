package dev.quirky.client.mixin;

import dev.quirky.client.pick_range.PickRangeHelper;
import dev.quirky.config.QuirkyConfigHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 远距中键拾取（26.2 客户端接管）。
 *
 * <p>26.2 把中键拾取改为「客户端发 {@code ServerboundPickItemFromBlockPacket(pos)} → 服务端
 * {@code isWithinBlockInteractionRange(pos)} 距离校验后执行」，原版距离 4.5/5 格。
 * 旧的「重定向 hitResult 让原版流程跑远距 pos」思路会被服务端距离校验拒绝（pos 超距 →
 * 服务端 {@code handlePickItemFromBlock} 整个 if 跳过，不拾取）。
 *
 * <p>现行策略：重定向 {@code pickBlockOrEntity} 内 getType() 检查（hitResult 第 1 次 GETFIELD，
 * ordinal 1）——
 * <ul>
 *   <li>原版范围内已有目标（非 MISS）：原样返回，原版近距拾取/实体拾取不受影响；
 *   <li>MISS + longPick + 创造模式：扩展距离 clip，命中方块则在客户端构造克隆物品经
 *       {@link MultiPlayerGameMode#handleCreativeModeItemAdd} 放入当前热栏槽（创造模式客户端权威，
 *       服务端 {@code handleSetCreativeModeSlot} 无距离校验），并返回 MISS 短路原版流程
 *       （不再发注定被拒的远距 pos 包）；
 *   <li>MISS + longPick + 生存模式：返回 MISS——生存本就不能凭空拿方块，远距不生效为合理边界；
 *   <li>MISS + longPick 关闭：返回 MISS，原版不拾取。
 * </ul>
 *
 * <p>仅重定向 ordinal 1（getType 检查）：返回 MISS 时原版 {@code if (hitResult!=null &&
 * getType()!=MISS)} 短路，不进入 switch、不调 {@code handlePickItemFromBlock}；
 * 返回非 MISS 时原版进 switch，ordinal 2（switch 引用）读原版字段——故无需重定向 ordinal 2
 * （创造性已在 ordinal 1 短路，生存性返回 MISS 也不进 switch）。
 *
 * <p>已知限制：克隆物品用 {@code BlockState#getCloneItemStack(level, pos, false)}，
 * <b>不带方块实体 NBT</b>（潜影盒内容物、告示牌文字等）——26.2 该 NBT 由服务端
 * {@code addBlockDataToItem} 附加，客户端接管暂未复刻；创造建筑拿方块的主场景不受影响。
 */
@Mixin(Minecraft.class)
public abstract class PickBlockMixin {
	@Redirect(
		method = "pickBlockOrEntity",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;hitResult:Lnet/minecraft/world/phys/HitResult;", ordinal = 1)
	)
	private HitResult quirky$extendedPickType(Minecraft minecraft) {
		HitResult original = minecraft.hitResult;
		// 原版范围内已有目标（实体拾取、近距离方块拾取）一律保持原版。
		if (original == null || original.getType() != HitResult.Type.MISS) {
			return original;
		}
		if (!QuirkyConfigHolder.get().longPick) {
			return original;
		}
		Player player = minecraft.player;
		Entity camera = minecraft.getCameraEntity();
		if (minecraft.level == null || player == null || camera == null) {
			return original;
		}
		// 生存模式：远距拾取不合理（不能凭空拿方块），维持原版 MISS（不拾取）。
		if (!player.hasInfiniteMaterials()) {
			return original;
		}
		boolean creative = true;
		if (!PickRangeHelper.isEnabled(creative)) {
			return original;
		}
		int range = PickRangeHelper.rangeFor(creative);
		Vec3 from = camera.getEyePosition(1.0F);
		Vec3 view = camera.getViewVector(1.0F);
		Vec3 to = from.add(view.x * range, view.y * range, view.z * range);
		BlockHitResult hit = minecraft.level.clip(
			new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, camera)
		);
		if (hit.getType() == HitResult.Type.MISS) {
			return original;
		}
		// 创造模式：客户端接管——构造克隆物品放入当前热栏槽，返回 MISS 短路原版（避免发远距 pos 包被服务端拒）。
		BlockPos pos = hit.getBlockPos();
		BlockState state = minecraft.level.getBlockState(pos);
		ItemStack stack = state.getCloneItemStack(minecraft.level, pos, false);
		if (!stack.isEmpty()) {
			int hotbarSlot = 36 + player.getInventory().getSelectedSlot();
			minecraft.gameMode.handleCreativeModeItemAdd(stack, hotbarSlot);
		}
		// 返回 MISS：原版 if 短路，不调 handlePickItemFromBlock（不再发超距 pos 包）。
		return original;
	}
}
