package dev.quirky.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.quirky.ModBlocks;
import dev.quirky.TestBootstrap;
import dev.quirky.config.QuirkyConfig;
import dev.quirky.config.QuirkyConfigHolder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ObsidianPressurePlateBlockTest {
	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	/**
	 * 实体列表收集限定 Player.class：桩对象只匹配 Player.class 查询，
	 * 若实现误用 Entity/LivingEntity 类查询则桩不命中返回 null，测试失败。
	 */
	@Test
	void playerOnPlateGivesFullSignal() {
		Level level = mock(Level.class);
		BlockPos pos = new BlockPos(1, 64, 1);
		when(level.getEntitiesOfClass(eq(Player.class), any(AABB.class), any()))
			.thenReturn(List.of(mock(Player.class)));

		assertEquals(15, ModBlocks.OBSIDIAN_PRESSURE_PLATE.getSignalStrength(level, pos));
	}

	/** 只有牛踩上（Player 查询为空）→ 无信号；掉落物/动物同理不触发。 */
	@Test
	void cowOnlyOnPlateGivesNoSignal() {
		Level level = mock(Level.class);
		BlockPos pos = new BlockPos(1, 64, 1);
		when(level.getEntitiesOfClass(eq(Player.class), any(AABB.class), any()))
			.thenReturn(List.of());

		assertEquals(0, ModBlocks.OBSIDIAN_PRESSURE_PLATE.getSignalStrength(level, pos));
	}

	/** 开关关闭时恒无信号，且不查询实体（review D2 热切换拦截）。 */
	@Test
	void plateDisabledByConfigGivesNoSignal() {
		QuirkyConfigHolder.set(new QuirkyConfig());
		try {
			QuirkyConfigHolder.get().obsidianPlate = false;
			Level level = mock(Level.class);
			BlockPos pos = new BlockPos(1, 64, 1);

			assertEquals(0, ModBlocks.OBSIDIAN_PRESSURE_PLATE.getSignalStrength(level, pos));
		} finally {
			QuirkyConfigHolder.set(new QuirkyConfig());
		}
	}
}
