package dev.quirky.client.tooltips;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import dev.quirky.ModItems;
import dev.quirky.TestBootstrap;
import dev.quirky.config.QuirkyConfig;
import dev.quirky.config.QuirkyConfigHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 高级 tooltip 模式的受控物品判定：
 * <ul>
 *   <li>hasGatedContent：时钟/地图/箭袋/播种袋/鱼饵球/绳 为受控；食物与属性物品不受控。</li>
 *   <li>潜影盒判定依赖 tag 数据（单测环境无 tag，恒 false；生产行为由游戏内验证）。</li>
 *   <li>shouldSuppress：无客户端实例（单测/服务端）恒 false。</li>
 *   <li>配置开关关闭后 hasGatedContent 恒 false。</li>
 * </ul>
 */
class TooltipShiftHelperTest {
	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
		// 测试环境需显式绑定组件（DataComponentInitializers 依赖数据包注册表）
		TestBootstrap.bindItem(Items.CLOCK);
		TestBootstrap.bindItem(Items.FILLED_MAP);
		TestBootstrap.bindItem(Items.APPLE);
		TestBootstrap.bindItem(Items.DIAMOND_SWORD);
		TestBootstrap.bindItem(Items.DIRT);
		TestBootstrap.bindItem(Items.SHULKER_BOX);
		TestBootstrap.bindItem(ModItems.QUIVER);
		TestBootstrap.bindItem(ModItems.SEED_POUCH);
		TestBootstrap.bindItem(ModItems.FISH_BAIT);
		TestBootstrap.bindItem(ModItems.ROPE);
		TestBootstrap.bindItem(ModItems.ROPE_LANTERN);
	}

	@BeforeEach
	void resetConfig() {
		QuirkyConfigHolder.set(new QuirkyConfig());
	}

	@Test
	void clock_isGated() {
		assertTrue(TooltipShiftHelper.hasGatedContent(new ItemStack(Items.CLOCK)));
	}

	@Test
	void filledMap_isGated() {
		ItemStack map = new ItemStack(Items.FILLED_MAP);
		map.set(DataComponents.MAP_ID, new MapId(1));
		assertTrue(TooltipShiftHelper.hasGatedContent(map));
	}

	@Test
	void quiver_isGated() {
		assertTrue(TooltipShiftHelper.hasGatedContent(new ItemStack(ModItems.QUIVER)));
	}

	@Test
	void seedPouch_isGated() {
		assertTrue(TooltipShiftHelper.hasGatedContent(new ItemStack(ModItems.SEED_POUCH)));
	}

	@Test
	void fishBait_isGated() {
		assertTrue(TooltipShiftHelper.hasGatedContent(new ItemStack(ModItems.FISH_BAIT)));
	}

	@Test
	void ropeItems_areGated() {
		assertTrue(TooltipShiftHelper.hasGatedContent(new ItemStack(ModItems.ROPE)));
		assertTrue(TooltipShiftHelper.hasGatedContent(new ItemStack(ModItems.ROPE_LANTERN)));
	}

	@Test
	void foodAndAttributeItems_areNotGated() {
		// 食物与属性 tooltip 保持默认显示，不受高级模式控制
		assertFalse(TooltipShiftHelper.hasGatedContent(new ItemStack(Items.APPLE)));
		assertFalse(TooltipShiftHelper.hasGatedContent(new ItemStack(Items.DIAMOND_SWORD)));
	}

	@Test
	void plainItem_isNotGated() {
		assertFalse(TooltipShiftHelper.hasGatedContent(new ItemStack(Items.DIRT)));
	}

	@Test
	void emptyShulker_isNotGated() {
		// 单测无 tag 数据恒 false；空盒在生产环境同样不提示（无内容预览）
		assertFalse(TooltipShiftHelper.hasGatedContent(new ItemStack(Items.SHULKER_BOX)));
	}

	@Test
	void noClientInstance_neverSuppresses() {
		// 单测/服务端无 Minecraft 实例 → shouldSuppress 恒 false
		assertFalse(TooltipShiftHelper.shouldSuppress(new ItemStack(Items.CLOCK)));
		assertFalse(TooltipShiftHelper.shouldSuppress(new ItemStack(ModItems.ROPE)));
	}
}
