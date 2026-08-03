package dev.quirky.client.tooltips;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.quirky.TestBootstrap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 横条与原版属性文本段的可见性互斥矩阵：未按 Shift 且横条有内容 → 原版段隐藏；
 * 按 Shift → 原版段显示；config 关闭 → 原版段恒显示；横条无替代内容（仅非 6 类修饰符
 * 或无修饰符）→ 原版段不隐藏（信息不丢失）。
 */
class AttributeTooltipVisibilityTest {

	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
		TestBootstrap.bindItem(Items.DIAMOND_SWORD);
		TestBootstrap.bindItem(Items.DIRT);
	}

	@Test
	void noClientInstanceTreatsAsNoShift() {
		assertFalse(AttributeTooltipVisibility.shiftHidesCompactRow(null));
		assertTrue(AttributeTooltipVisibility.vanillaTextHidden(true, null, new ItemStack(Items.DIAMOND_SWORD)));
	}

	@Test
	void shiftShowsVanillaTextAndHidesCompactRow() {
		Minecraft minecraft = mock(Minecraft.class);
		when(minecraft.hasShiftDown()).thenReturn(true);

		assertTrue(AttributeTooltipVisibility.shiftHidesCompactRow(minecraft));
		assertFalse(AttributeTooltipVisibility.vanillaTextHidden(true, minecraft, new ItemStack(Items.DIAMOND_SWORD)));
	}

	@Test
	void noShiftHidesVanillaTextForCoveredItems() {
		Minecraft minecraft = mock(Minecraft.class);
		when(minecraft.hasShiftDown()).thenReturn(false);

		assertFalse(AttributeTooltipVisibility.shiftHidesCompactRow(minecraft));
		// 钻石剑：横条有 6 类属性替代内容 → 原版段隐藏
		assertTrue(AttributeTooltipVisibility.vanillaTextHidden(true, minecraft, new ItemStack(Items.DIAMOND_SWORD)));
	}

	@Test
	void vanillaTextNotHiddenWhenNoReplacement() {
		Minecraft minecraft = mock(Minecraft.class);
		when(minecraft.hasShiftDown()).thenReturn(false);

		// 无任何修饰符：原版段本来无内容，也不隐藏（横条无替代）
		assertFalse(AttributeTooltipVisibility.vanillaTextHidden(true, minecraft, new ItemStack(Items.DIRT)));
	}

	@Test
	void configOffKeepsVanillaText() {
		Minecraft minecraft = mock(Minecraft.class);
		when(minecraft.hasShiftDown()).thenReturn(false);

		assertFalse(AttributeTooltipVisibility.vanillaTextHidden(false, minecraft, new ItemStack(Items.DIAMOND_SWORD)));
	}
}
