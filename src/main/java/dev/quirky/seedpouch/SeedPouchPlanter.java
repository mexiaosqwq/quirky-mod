package dev.quirky.seedpouch;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 播种袋的纯逻辑：区域扫描 + 逐格选种（来源：袋内 items 列表）+ 消耗清单。
 *
 * <p>只读输入并产出计划，不修改世界、不修改袋子；执行由 {@code SeedPouchItem}
 * 在服务端完成。选种判定泛化到 canSurvive：甘蔗、仙人掌、竹子、地狱疣等按各自
 * 种子规则自动兼容。袋内只装真种子（{@link SeedFilter} 保证），故无需重复白名单。
 */
public final class SeedPouchPlanter {
	private SeedPouchPlanter() {
	}

	/**
	 * 单个候选格（纯数据）：地面位置 + 上方是否空气可替换 + 基地方块状态。
	 */
	public record BlockSnapshot(BlockPos pos, boolean replaceableAbove, BlockState baseState) {
	}

	/** 一次计划播种：在 {@code pos.above()} 种下 {@code cropState}，从袋内 {@code pouchIndex} 取种 1 个。 */
	public record PlanEntry(BlockPos pos, BlockState cropState, int pouchIndex) {
	}

	/** 整批计划结果。 */
	public record PlanResult(List<PlanEntry> entries) {
		public boolean isEmpty() {
			return this.entries.isEmpty();
		}
	}

	/**
	 * 以 {@code center} 为中心扫描 {@code (2*radius+1)²} 方格。
	 * {@code radius=0} → 仅中心 1 格（潜行精准模式）。候选条件：上方为空气。
	 */
	public static List<BlockSnapshot> scan(LevelReader level, BlockPos center, int radius) {
		List<BlockSnapshot> cells = new ArrayList<>();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				BlockPos pos = new BlockPos(center.getX() + dx, center.getY(), center.getZ() + dz);
				cells.add(new BlockSnapshot(pos, level.getBlockState(pos.above()).isAir(), level.getBlockState(pos)));
			}
		}
		return cells;
	}

	/**
	 * 逐格按袋内 items 列表顺序找第一种能在此存活的种子（canSurvive 泛化）。
	 * 袋内 guaranteed 全是真种子（SeedFilter），无需在此重复白名单。
	 *
	 * @param pouchItems    袋内 items 列表（来自 BundleContents.items() 拷贝）
	 * @param infiniteSeeds 创造模式时槽位视为无限，只按种子类型判存活
	 */
	public static PlanResult plan(LevelReader level, List<BlockSnapshot> area, List<ItemStack> pouchItems, boolean infiniteSeeds) {
		List<PlanEntry> entries = new ArrayList<>();
		int[] remaining = new int[pouchItems.size()];
		for (int i = 0; i < pouchItems.size(); i++) {
			remaining[i] = pouchItems.get(i).getCount();
		}
		for (BlockSnapshot cell : area) {
			if (!cell.replaceableAbove()) {
				continue;
			}
			BlockPos cropPos = cell.pos().above();
			for (int i = 0; i < pouchItems.size(); i++) {
				if (!infiniteSeeds && remaining[i] <= 0) {
					continue;
				}
				ItemStack stack = pouchItems.get(i);
				if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
					continue;
				}
				BlockState cropState = blockItem.getBlock().defaultBlockState();
				if (!cropState.canSurvive(level, cropPos)) {
					continue;
				}
				entries.add(new PlanEntry(cell.pos(), cropState, i));
				remaining[i]--;
				break;
			}
		}
		return new PlanResult(entries);
	}

	/**
	 * 消耗袋内指定 index 1 个，返回新列表（归零则移除 entry）。
	 * 供 Item 在种地后更新 BundleContents（Mutable 无"减指定 index"API，需手动重建）。
	 */
	public static List<ItemStack> consumeOne(List<ItemStack> pouchItems, int index) {
		List<ItemStack> result = new ArrayList<>(pouchItems.size());
		for (int i = 0; i < pouchItems.size(); i++) {
			ItemStack stack = pouchItems.get(i);
			if (i == index) {
				int newCount = stack.getCount() - 1;
				if (newCount > 0) {
					result.add(stack.copyWithCount(newCount));
				}
				// 归零则不加入（移除 entry）
			} else {
				result.add(stack.copy());
			}
		}
		return result;
	}
}
