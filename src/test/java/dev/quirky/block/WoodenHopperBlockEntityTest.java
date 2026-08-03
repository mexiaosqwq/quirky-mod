package dev.quirky.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.quirky.ModBlockEntityTypes;
import dev.quirky.ModBlocks;
import dev.quirky.ModItems;
import dev.quirky.QuirkyMod;
import dev.quirky.TestBootstrap;
import dev.quirky.block.be.WoodenHopperBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.flag.FeatureFlags;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

/**
 * 木漏斗行为单测：4 倍慢传输（32 tick 冷却）与红石锁定无效。
 * 通过真实的 WoodenHopperBlockEntity 互灌（level 为 mock，容器为真实 BE），
 * 与同场景下原版漏斗在 ENABLED=false 时的对照，证明行为差异来自木漏斗本身。
 */
class WoodenHopperBlockEntityTest {
	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
		TestBootstrap.bindItem(Items.STONE);
	}

	@Test
	void constructWithWoodenHopperStateSucceeds() {
		// 自定义 BlockEntityType 的 validBlocks 必须认木漏斗方块，否则构造即抛异常
		WoodenHopperBlockEntity hopper = new WoodenHopperBlockEntity(
			new BlockPos(1, 64, 1), ModBlocks.WOODEN_HOPPER.defaultBlockState()
		);
		assertEquals(5, hopper.getContainerSize());
		assertSame(ModBlockEntityTypes.WOODEN_HOPPER, hopper.getType());
		assertTrue(hopper.isEmpty());
	}

	@Test
	void woodenHopperIsRegistered() {
		assertSame(ModBlocks.WOODEN_HOPPER, BuiltInRegistries.BLOCK.getValue(QuirkyMod.id("wooden_hopper")));
		assertSame(ModBlockEntityTypes.WOODEN_HOPPER, BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(QuirkyMod.id("wooden_hopper")));
		assertSame(ModItems.WOODEN_HOPPER, BuiltInRegistries.ITEM.getValue(QuirkyMod.id("wooden_hopper")));
	}

	@Test
	void movesOneItemEvery32Ticks() throws Exception {
		BlockPos pos = new BlockPos(1, 64, 1);
		WoodenHopperBlockEntity hopper = hopperWithItems(pos, new ItemStack(Items.STONE, 2));
		WoodenHopperBlockEntity target = emptyTarget(pos.below());
		Level level = mockLevelWithTarget(pos, target);
		BlockState state = ModBlocks.WOODEN_HOPPER.defaultBlockState();

		// 第 1 tick：搬运 1 个，冷却置 32
		WoodenHopperBlockEntity.pushItemsTick(level, pos, state, hopper);
		assertEquals(1, target.getItem(0).getCount());
		assertEquals(32, cooldownOf(hopper));

		// 之后 31 tick 冷却倒数中，不再搬运
		for (int i = 0; i < 31; i++) {
			WoodenHopperBlockEntity.pushItemsTick(level, pos, state, hopper);
		}
		assertEquals(1, target.getItem(0).getCount());

		// 第 33 tick：再次搬运，两次搬运间隔恰为 32 tick
		WoodenHopperBlockEntity.pushItemsTick(level, pos, state, hopper);
		assertEquals(2, target.getItem(0).getCount());
	}

	@Test
	void transfersWhileRedstonePowered() throws Exception {
		// 红石锁定无效：ENABLED=false（被红石信号激活）时仍照常传输
		BlockPos pos = new BlockPos(1, 64, 1);
		WoodenHopperBlockEntity hopper = hopperWithItems(pos, new ItemStack(Items.STONE));
		WoodenHopperBlockEntity target = emptyTarget(pos.below());
		Level level = mockLevelWithTarget(pos, target);
		BlockState powered = ModBlocks.WOODEN_HOPPER.defaultBlockState().setValue(HopperBlock.ENABLED, false);

		WoodenHopperBlockEntity.pushItemsTick(level, pos, powered, hopper);

		assertEquals(1, target.getItem(0).getCount());
		assertEquals(32, cooldownOf(hopper));
	}

	@Test
	void vanillaHopperStopsWhenPowered() throws Exception {
		// 对照：同场景原版漏斗在 ENABLED=false 时不传输，证明木漏斗行为是刻意差异
		BlockPos pos = new BlockPos(1, 64, 1);
		HopperBlockEntity vanilla = new HopperBlockEntity(pos, Blocks.HOPPER.defaultBlockState());
		vanilla.setItem(0, new ItemStack(Items.STONE));
		WoodenHopperBlockEntity target = emptyTarget(pos.below());
		Level level = mockLevelWithTarget(pos, target);
		BlockState powered = Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.ENABLED, false);

		HopperBlockEntity.pushItemsTick(level, pos, powered, vanilla);

		assertEquals(1, vanilla.getItem(0).getCount());
		assertEquals(0, vanillaCooldownOf(vanilla));
	}

	@Test
	void ejectsItemsAsDropsWhenNoContainerBelow() throws Exception {
		// 下方无容器（空气）时，木漏斗把物品从漏斗口漏出为掉落物，而不是永远积在漏斗里
		BlockPos pos = new BlockPos(1, 64, 1);
		WoodenHopperBlockEntity hopper = hopperWithItems(pos, new ItemStack(Items.STONE, 2));
		ServerLevel level = mock(ServerLevel.class);
		when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
		when(level.getBlockEntity(any())).thenReturn(null);
		when(level.isEmptyBlock(any())).thenReturn(true);
		when(level.getRandom()).thenReturn(RandomSource.create());
		when(level.getGameRules()).thenReturn(new GameRules(FeatureFlags.DEFAULT_FLAGS));

		WoodenHopperBlockEntity.pushItemsTick(level, pos, ModBlocks.WOODEN_HOPPER.defaultBlockState(), hopper);

		// 移除 1 个物品弹出为掉落物，进入 32 tick 冷却
		assertEquals(1, hopper.getItem(0).getCount());
		assertEquals(32, cooldownOf(hopper));
		verify(level).addFreshEntity(any(ItemEntity.class));
	}

	@Test
	void doesNotEjectIntoSolidBlockBelow() throws Exception {
		// 下方是实心方块（非空气）时不应漏出：掉落物会卡进方块内部不可见
		BlockPos pos = new BlockPos(1, 64, 1);
		WoodenHopperBlockEntity hopper = hopperWithItems(pos, new ItemStack(Items.STONE, 2));
		ServerLevel level = mock(ServerLevel.class);
		when(level.getBlockState(any())).thenReturn(Blocks.STONE.defaultBlockState());
		when(level.getBlockEntity(any())).thenReturn(null);
		when(level.getRandom()).thenReturn(RandomSource.create());
		when(level.getGameRules()).thenReturn(new GameRules(FeatureFlags.DEFAULT_FLAGS));

		WoodenHopperBlockEntity.pushItemsTick(level, pos, ModBlocks.WOODEN_HOPPER.defaultBlockState(), hopper);

		assertEquals(2, hopper.getItem(0).getCount());
		verify(level, never()).addFreshEntity(any(ItemEntity.class));
	}

	@Test
	void doesNotEjectWhenFacingSideways() throws Exception {
		// 只有朝下（DOWN）漏出；侧面/上方无容器时保持原版行为（不排物品）
		BlockPos pos = new BlockPos(1, 64, 1);
		BlockState facingNorth = ModBlocks.WOODEN_HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.NORTH);
		WoodenHopperBlockEntity hopper = new WoodenHopperBlockEntity(pos, facingNorth);
		hopper.setItem(0, new ItemStack(Items.STONE));
		ServerLevel level = mock(ServerLevel.class);
		when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
		when(level.getBlockEntity(any())).thenReturn(null);
		when(level.isEmptyBlock(any())).thenReturn(true);
		when(level.getRandom()).thenReturn(RandomSource.create());
		when(level.getGameRules()).thenReturn(new GameRules(FeatureFlags.DEFAULT_FLAGS));

		WoodenHopperBlockEntity.pushItemsTick(level, pos, facingNorth, hopper);

		assertEquals(1, hopper.getItem(0).getCount());
		verify(level, never()).addFreshEntity(any(ItemEntity.class));
	}

	@Test
	void doesNotEjectWhenBlockDropsDisabled() throws Exception {
		// block_drops 游戏规则关闭时：popResource 不会生成掉落物，若先移除物品会造成永久吞物
		BlockPos pos = new BlockPos(1, 64, 1);
		WoodenHopperBlockEntity hopper = hopperWithItems(pos, new ItemStack(Items.STONE, 2));
		ServerLevel level = mock(ServerLevel.class);
		when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
		when(level.getBlockEntity(any())).thenReturn(null);
		when(level.isEmptyBlock(any())).thenReturn(true);
		when(level.getRandom()).thenReturn(RandomSource.create());
		GameRules rules = mock(GameRules.class);
		when(rules.get(GameRules.BLOCK_DROPS)).thenReturn(Boolean.FALSE);
		when(level.getGameRules()).thenReturn(rules);

		WoodenHopperBlockEntity.pushItemsTick(level, pos, ModBlocks.WOODEN_HOPPER.defaultBlockState(), hopper);

		assertEquals(2, hopper.getItem(0).getCount());
		verify(level, never()).addFreshEntity(any(ItemEntity.class));
	}

	@Test
	void doesNotEjectWhenContainerBelowIsFull() throws Exception {
		// 下方是容器但已满：走原版 isFullContainer 分支，不漏出
		BlockPos pos = new BlockPos(1, 64, 1);
		WoodenHopperBlockEntity hopper = hopperWithItems(pos, new ItemStack(Items.STONE));
		WoodenHopperBlockEntity target = emptyTarget(pos.below());
		for (int i = 0; i < target.getContainerSize(); i++) {
			target.setItem(i, new ItemStack(Items.STONE, 64));
		}
		ServerLevel level = mock(ServerLevel.class);
		when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
		BlockState targetState = mock(BlockState.class);
		when(targetState.hasBlockEntity()).thenReturn(true);
		when(targetState.getBlock()).thenReturn(ModBlocks.WOODEN_HOPPER);
		when(level.getBlockState(pos.below())).thenReturn(targetState);
		when(level.getBlockEntity(pos.below())).thenReturn(target);
		when(level.getRandom()).thenReturn(RandomSource.create());
		when(level.getGameRules()).thenReturn(new GameRules(FeatureFlags.DEFAULT_FLAGS));

		WoodenHopperBlockEntity.pushItemsTick(level, pos, ModBlocks.WOODEN_HOPPER.defaultBlockState(), hopper);

		assertEquals(1, hopper.getItem(0).getCount());
		verify(level, never()).addFreshEntity(any(ItemEntity.class));
	}

	private static WoodenHopperBlockEntity hopperWithItems(BlockPos pos, ItemStack... stacks) {
		WoodenHopperBlockEntity hopper = new WoodenHopperBlockEntity(pos, ModBlocks.WOODEN_HOPPER.defaultBlockState());
		for (int i = 0; i < stacks.length; i++) {
			hopper.setItem(i, stacks[i]);
		}
		return hopper;
	}

	private static WoodenHopperBlockEntity emptyTarget(BlockPos pos) {
		return new WoodenHopperBlockEntity(pos, ModBlocks.WOODEN_HOPPER.defaultBlockState());
	}

	/**
	 * 目标位置放一个真实木漏斗 BE 作为接收容器；level 其余位置返回空气。
	 * getContainerAt 走 state.hasBlockEntity() + level.getBlockEntity() 路径。
	 */
	private static Level mockLevelWithTarget(BlockPos hopperPos, WoodenHopperBlockEntity target) {
		BlockPos targetPos = hopperPos.below();
		Level level = mock(Level.class);
		when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
		BlockState targetState = mock(BlockState.class);
		when(targetState.hasBlockEntity()).thenReturn(true);
		when(targetState.getBlock()).thenReturn(ModBlocks.WOODEN_HOPPER);
		when(level.getBlockState(targetPos)).thenReturn(targetState);
		when(level.getBlockEntity(targetPos)).thenReturn(target);
		return level;
	}

	private static int cooldownOf(WoodenHopperBlockEntity hopper) throws Exception {
		Field field = WoodenHopperBlockEntity.class.getDeclaredField("cooldownTime");
		field.setAccessible(true);
		return field.getInt(hopper);
	}

	private static int vanillaCooldownOf(HopperBlockEntity hopper) throws Exception {
		Field field = HopperBlockEntity.class.getDeclaredField("cooldownTime");
		field.setAccessible(true);
		return field.getInt(hopper);
	}
}
