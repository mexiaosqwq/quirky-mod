package dev.quirky.client.tooltips;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.quirky.ModItems;
import dev.quirky.TestBootstrap;
import dev.quirky.config.QuirkyConfig;
import dev.quirky.config.QuirkyConfigHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 高级 tooltip 模式的受控物品判定：
 * <ul>
 *   <li>受控（藏描述性长文字）：箭袋用法行、播种袋、鱼饵球、绳。</li>
 *   <li>不受控（信息类始终显示）：时钟行、地图预览、箭袋内容网格、潜影盒网格、
 *       食物与属性提示、普通物品。</li>
 *   <li>shouldSuppress：无客户端实例（单测/服务端）恒 false。</li>
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
	void quiver_isGated() {
		// 箭袋：用法长文字受控；内容网格本身始终显示
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
	void clock_isNotGated() {
		// 时钟行是短信息，始终显示
		assertFalse(TooltipShiftHelper.hasGatedContent(new ItemStack(Items.CLOCK)));
	}

	@Test
	void filledMap_isNotGated() {
		// 地图预览是信息类组件，始终显示
		ItemStack map = new ItemStack(Items.FILLED_MAP);
		map.set(DataComponents.MAP_ID, new MapId(1));
		assertFalse(TooltipShiftHelper.hasGatedContent(map));
	}

	@Test
	void shulkerBox_isNotGated() {
		// 潜影盒内容网格是信息类组件，始终显示（单测无 tag 数据恒 false，语义一致）
		assertFalse(TooltipShiftHelper.hasGatedContent(new ItemStack(Items.SHULKER_BOX)));
	}

	@Test
	void foodAndAttributeItems_areNotGated() {
		assertFalse(TooltipShiftHelper.hasGatedContent(new ItemStack(Items.APPLE)));
		assertFalse(TooltipShiftHelper.hasGatedContent(new ItemStack(Items.DIAMOND_SWORD)));
	}

	@Test
	void plainItem_isNotGated() {
		assertFalse(TooltipShiftHelper.hasGatedContent(new ItemStack(Items.DIRT)));
	}

	@Test
	void noClientInstance_neverSuppresses() {
		// 单测/服务端无 Minecraft 实例 → shouldSuppress 恒 false
		assertFalse(TooltipShiftHelper.shouldSuppress(new ItemStack(ModItems.ROPE)));
		assertFalse(TooltipShiftHelper.shouldSuppress(new ItemStack(Items.CLOCK)));
	}
}
