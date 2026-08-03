package dev.quirky.boomerang;

import dev.quirky.QuirkyMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.TargetBlock;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.BigDripleafBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.SpeleothemBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 回旋镖打碎/远程激活判定的纯函数（单测覆盖）。
 * 可碎规则：不在免疫 tag 内 + 非冒险模式；破坏速度 == 0 必碎（树叶/高草/花/火把等），
 * 其余实心方块参与 5% 摇骰；破坏速度 &lt; 0（不可破坏）永不打碎。
 * 远程激活：钟/按钮/目标块/蜡烛/紫水晶/滴叶/营火/紫颂花/饰纹陶罐/钟乳石/TNT
 * 等原版可被弹射物触发的方块。
 */
public final class BoomerangBlockLogic {
	/** 免疫打碎的方块 tag（数据包可扩展）：基岩/黑曜石/哭泣黑曜石/远古残骸/下界合金块/重生锚。 */
	public static final TagKey<Block> UNBREAKABLE_TAG =
		TagKey.create(Registries.BLOCK, QuirkyMod.id("boomerang_unbreakable"));

	public enum BreakResult {
		/** 必碎（秒破类方块，不参与摇骰）。 */
		MUST_BREAK,
		/** 参与默认 5% 摇骰。 */
		ROLL,
		/** 不可碎（免疫/冒险/不可破坏）。 */
		CANNOT_BREAK
	}

	private BoomerangBlockLogic() {
	}

	/**
	 * 可碎判定纯函数。
	 *
	 * @param destroySpeed  方块破坏速度（BlockState.getDestroySpeed），0=秒破、负=不可破坏
	 * @param isImmune      是否在 #quirky:boomerang_unbreakable tag 内
	 * @param adventureMode 投掷者是否为冒险模式
	 */
	public static BreakResult decideBreak(float destroySpeed, boolean isImmune, boolean adventureMode) {
		if (isImmune || adventureMode || destroySpeed < 0.0F) {
			return BreakResult.CANNOT_BREAK;
		}
		return destroySpeed == 0.0F ? BreakResult.MUST_BREAK : BreakResult.ROLL;
	}

	/** 该方块是否可被弹射物远程激活（钟/按钮/目标块等）。 */
	public static boolean canActivate(BlockState state) {
		Block block = state.getBlock();
		return block instanceof BellBlock
			|| block instanceof ButtonBlock
			|| block instanceof TargetBlock
			|| block instanceof AbstractCandleBlock
			|| block instanceof AmethystBlock
			|| block instanceof BigDripleafBlock
			|| block instanceof CampfireBlock
			|| block instanceof ChorusFlowerBlock
			|| block instanceof DecoratedPotBlock
			|| block instanceof SpeleothemBlock
			|| block instanceof TntBlock;
	}
}
