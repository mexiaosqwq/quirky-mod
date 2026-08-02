package dev.quirky.client.mixin;

import dev.quirky.client.soul_lighting.SoulLightingHelper;
import dev.quirky.client.soul_lighting.SoulLightingModels;
import dev.quirky.config.QuirkyConfigHolder;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 灵魂光源注入点（26.2 模型选择点）。
 *
 * <p>26.2 中 {@code BlockModelShaper} 已被无位置信息的 {@code BlockStateModelSet} 取代；
 * 唯一同时持有渲染区域与 BlockPos 的方块网格入口是 {@code SectionCompiler.compile} 调用的
 * {@link ModelBlockRenderer#tesselateBlock}。这里重定向该调用，在传入网格前按正下方方块
 * （y-1）动态替换为灵魂变体模型。区块网格重新编译时重评估，破坏/移动灵魂方块后自动恢复。
 */
@Mixin(SectionCompiler.class)
public abstract class SectionCompilerMixin {
	@Redirect(
		method = "compile",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V"
		)
	)
	private void quirky$soulLighting(
		ModelBlockRenderer renderer,
		BlockQuadOutput output,
		float x,
		float y,
		float z,
		BlockAndTintGetter level,
		BlockPos pos,
		BlockState blockState,
		BlockStateModel model,
		long seed
	) {
		// 先按方块类型过滤，避免对区块内每个方块都做一次下方方块（y-1）查询；
		// 只有光源方块（火把/灯笼/蜡烛）才需要动态判定下方是否为灵魂方块。
		if (QuirkyConfigHolder.get().soulLighting && SoulLightingHelper.isLightSource(blockState)) {
			model = SoulLightingModels.resolve(blockState, level.getBlockState(pos.below()), model);
		}
		renderer.tesselateBlock(output, x, y, z, level, pos, blockState, model, seed);
	}
}
