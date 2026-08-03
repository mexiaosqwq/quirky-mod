package dev.quirky.seedpouch;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 播种袋的纯逻辑：区域扫描 + 逐格选种 + 消耗清单。
 *
 * <p>只读输入并产出计划，不修改世界、不修改背包；执行由 {@code SeedPouchItem}
 * 在服务端完成。选种判定泛化到 canSurvive：甘蔗（沙+水旁）、仙人掌、竹子、
 * 地狱疣（灵魂沙）等按各自种子规则自动兼容。
 */
public final class SeedPouchPlanter {
	private SeedPouchPlanter() {
	}

	/**
	 * 单个候选格（纯数据）：地面位置 + 上方是否空气可替换 + 基地方块状态。
	 */
	public record BlockSnapshot(BlockPos pos, boolean replaceableAbove, BlockState baseState) {
	}

	/**
	 * 一次计划播种：在 {@code pos.above()} 种下 {@code cropState}，
	 * 从 {@code inventorySlot} 槽位消耗 1 个。
	 */
	public record PlanEntry(BlockPos pos, BlockState cropState, int inventorySlot) {
	}

	/** 整批计划结果。 */
	public record PlanResult(List<PlanEntry> entries) {
		public boolean isEmpty() {
			return this.entries.isEmpty();
		}
	}

	/**
	 * 以 {@code center} 为中心扫描 {@code (2*radius+1)²} 方格。
	 * {@code radius=0} → 仅中心 1 格（潜行精准模式）。候选条件：上方为空气
	 * （保守起见只认空气，不认可替换的植物/雪）。
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
	 * 逐格按背包顺序找第一种能在此存活的种子：{@link BlockItem} 且其
	 * {@code defaultBlockState} 在目标格上方通过 {@code canSurvive}
	 * （含光照 + 基质检查）。
	 *
	 * <p>模拟消耗：每格占用对应槽位 1 个，槽位耗尽后跳过；{@code infiniteSeeds}
	 * （创造模式）时槽位视为无限，只按种子类型判存活。
	 */
	public static PlanResult plan(LevelReader level, List<BlockSnapshot> area, List<ItemStack> inventory, boolean infiniteSeeds) {
		List<PlanEntry> entries = new ArrayList<>();
		int[] remaining = new int[inventory.size()];
		for (int i = 0; i < inventory.size(); i++) {
			remaining[i] = inventory.get(i).getCount();
		}
		for (BlockSnapshot cell : area) {
			if (!cell.replaceableAbove()) {
				continue;
			}
			BlockPos cropPos = cell.pos().above();
			for (int slot = 0; slot < inventory.size(); slot++) {
				if (!infiniteSeeds && remaining[slot] <= 0) {
					continue;
				}
				ItemStack stack = inventory.get(slot);
				if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
					continue;
				}
				BlockState cropState = blockItem.getBlock().defaultBlockState();
				if (!cropState.canSurvive(level, cropPos)) {
					continue;
				}
				entries.add(new PlanEntry(cell.pos(), cropState, slot));
				remaining[slot]--;
				break;
			}
		}
		return new PlanResult(entries);
	}
}
