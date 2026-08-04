package dev.quirky.seedpouch;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;

/**
 * 种子白名单：只有作物方块类对应的 BlockItem 才算种子，可入播种袋。
 *
 * <p>排除灯笼/火把/脚手架/睡莲等可放置但非作物的 BlockItem（v1 灯笼 bug 根因）。
 * 模组作物多继承 {@link CropBlock}，自动兼容。
 */
public final class SeedFilter {
	private SeedFilter() {
	}

	/** 该物品是否是可入袋的种子：BlockItem 且其方块是作物方块类之一。 */
	public static boolean isSeed(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem && isSeed(blockItem.getBlock());
	}

	/** 该方块是否是作物方块（白名单 8 类，26.2 mcsrc 已验证均存在）。 */
	public static boolean isSeed(Block block) {
		return block instanceof CropBlock
			|| block instanceof NetherWartBlock
			|| block instanceof SugarCaneBlock
			|| block instanceof CactusBlock
			|| block instanceof StemBlock
			|| block instanceof SweetBerryBushBlock
			|| block instanceof PitcherCropBlock
			// BambooStalkBlock 是 26.2 的 Blocks.BAMBOO 类（Blocks.java:4105 register BambooStalkBlock::new）
			|| isBambooStalk(block);
	}

	private static boolean isBambooStalk(Block block) {
		// BambooStalkBlock 可能在不同 mapping 版本类名变化，用 Blocks.BAMBOO 等价判定更稳
		return block == net.minecraft.world.level.block.Blocks.BAMBOO;
	}
}
