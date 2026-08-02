package dev.quirky.harvest;

import java.util.Iterator;
import java.util.List;

import dev.quirky.config.QuirkyConfigHolder;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

public final class HarvestHandler {
	private HarvestHandler() {
	}

	public static void init() {
		UseBlockCallback.EVENT.register(HarvestHandler::onUseBlock);
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.PASS;
		}
		if (!QuirkyConfigHolder.get().harvestReplant) {
			return InteractionResult.PASS;
		}

		ServerLevel serverLevel = (ServerLevel) level;
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);

		if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
			return harvestSeedCrop(serverLevel, pos, state, crop, player, hand);
		}
		if (state.is(Blocks.NETHER_WART) && state.getValue(NetherWartBlock.AGE) == NetherWartBlock.MAX_AGE) {
			return harvestNetherWart(serverLevel, pos, state, player, hand);
		}
		if (state.is(Blocks.ATTACHED_MELON_STEM) || state.is(Blocks.ATTACHED_PUMPKIN_STEM)) {
			return harvestGourd(serverLevel, pos, state, player, hand);
		}
		if (state.getBlock() instanceof CocoaBlock && state.getValue(CocoaBlock.AGE) == CocoaBlock.MAX_AGE) {
			return harvestCocoa(serverLevel, pos, state, player, hand);
		}
		return InteractionResult.PASS;
	}

	private static InteractionResult harvestSeedCrop(
		ServerLevel level, BlockPos pos, BlockState state, CropBlock crop, Player player, InteractionHand hand
	) {
		List<ItemStack> drops = Block.getDrops(state, level, pos, null, player, ItemStack.EMPTY);
		Item seed = seedFor(state.getBlock());
		boolean replant = player.hasInfiniteMaterials();
		if (!replant && seed != Items.AIR) {
			replant = removeOneFromInventory(player.getInventory(), seed)
				|| removeOneFromDrops(drops, seed);
		}
		HarvestFx.playBreak(level, player, hand, pos, state);
		spawnDrops(level, pos, drops, player);
		level.setBlock(pos, replant ? crop.getStateForAge(0) : Blocks.AIR.defaultBlockState(), 3);
		if (replant) {
			HarvestFx.playReplant(level, pos, crop.getStateForAge(0));
		}
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult harvestNetherWart(
		ServerLevel level, BlockPos pos, BlockState state, Player player, InteractionHand hand
	) {
		List<ItemStack> drops = Block.getDrops(state, level, pos, null, player, ItemStack.EMPTY);
		boolean replant = player.hasInfiniteMaterials();
		if (!replant) {
			replant = removeOneFromInventory(player.getInventory(), Items.NETHER_WART)
				|| removeOneFromDrops(drops, Items.NETHER_WART);
		}
		HarvestFx.playBreak(level, player, hand, pos, state);
		spawnDrops(level, pos, drops, player);
		level.setBlock(pos, replant ? state.setValue(NetherWartBlock.AGE, 0) : Blocks.AIR.defaultBlockState(), 3);
		if (replant) {
			HarvestFx.playReplant(level, pos, state.setValue(NetherWartBlock.AGE, 0));
		}
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult harvestGourd(
		ServerLevel level, BlockPos stemPos, BlockState stemState, Player player, InteractionHand hand
	) {
		Direction facing = stemState.getValue(BlockStateProperties.HORIZONTAL_FACING);
		BlockPos fruitPos = stemPos.relative(facing);
		BlockState fruitState = level.getBlockState(fruitPos);
		Block fruit = stemState.is(Blocks.ATTACHED_MELON_STEM) ? Blocks.MELON : Blocks.PUMPKIN;
		if (!fruitState.is(fruit)) {
			return InteractionResult.PASS;
		}
		HarvestFx.playBreak(level, player, hand, fruitPos, fruitState);
		spawnDrops(level, fruitPos, Block.getDrops(fruitState, level, fruitPos, null, player, ItemStack.EMPTY), player);
		level.setBlock(fruitPos, Blocks.AIR.defaultBlockState(), 3);
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult harvestCocoa(
		ServerLevel level, BlockPos pos, BlockState state, Player player, InteractionHand hand
	) {
		HarvestFx.playBreak(level, player, hand, pos, state);
		spawnDrops(level, pos, Block.getDrops(state, level, pos, null, player, ItemStack.EMPTY), player);
		level.setBlock(pos, state.setValue(CocoaBlock.AGE, 0), 3);
		return InteractionResult.SUCCESS;
	}

	private static void spawnDrops(ServerLevel level, BlockPos pos, List<ItemStack> drops, Player player) {
		if (player.hasInfiniteMaterials()) {
			return;
		}
		for (ItemStack drop : drops) {
			Block.popResource(level, pos, drop);
		}
	}

	private static Item seedFor(Block block) {
		if (block == Blocks.WHEAT) {
			return Items.WHEAT_SEEDS;
		}
		if (block == Blocks.CARROTS) {
			return Items.CARROT;
		}
		if (block == Blocks.POTATOES) {
			return Items.POTATO;
		}
		if (block == Blocks.BEETROOTS) {
			return Items.BEETROOT_SEEDS;
		}
		if (block == Blocks.NETHER_WART) {
			return Items.NETHER_WART;
		}
		return Items.AIR;
	}

	private static boolean removeOneFromInventory(Inventory inventory, Item item) {
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				stack.shrink(1);
				return true;
			}
		}
		return false;
	}

	private static boolean removeOneFromDrops(List<ItemStack> drops, Item item) {
		Iterator<ItemStack> iterator = drops.iterator();
		while (iterator.hasNext()) {
			ItemStack drop = iterator.next();
			if (drop.is(item)) {
				drop.shrink(1);
				if (drop.isEmpty()) {
					iterator.remove();
				}
				return true;
			}
		}
		return false;
	}
}
