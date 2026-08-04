package dev.quirky.demobeast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.quirky.ModEntities;
import dev.quirky.TestBootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * demo_beast 生成链路验证（注册表级）：EntityType 已注册、尺寸/分类/属性配置正确。
 * 注意：实体实例化在单测环境不可行——fabric mixin 运行时未加载，
 * LivingEntity 构造会命中 "mixin dummy" 断言（实测 DemoBeastEntityTest 早期版本）。
 * 实例化冒烟留给游戏内验收（fabric 全量环境下）。
 */
class DemoBeastEntityTest {
	@BeforeAll
	static void boot() {
		TestBootstrap.boot();
	}

	@Test
	void entityTypeRegisteredWithCorrectConfig() {
		assertNotNull(BuiltInRegistries.ENTITY_TYPE.getValue(dev.quirky.QuirkyMod.id("demo_beast")));
		assertEquals(MobCategory.CREATURE, ModEntities.DEMO_BEAST.getCategory());
		assertEquals(0.8F, ModEntities.DEMO_BEAST.getWidth(), 0.001F);
		assertEquals(0.8F, ModEntities.DEMO_BEAST.getHeight(), 0.001F);
	}

	@Test
	void attributesConfigured() {
		var attributes = DemoBeastEntity.createAttributes().build();
		assertEquals(10.0, attributes.getValue(Attributes.MAX_HEALTH), 0.001);
		assertTrue(attributes.getValue(Attributes.MOVEMENT_SPEED) > 0.0);
	}
}
