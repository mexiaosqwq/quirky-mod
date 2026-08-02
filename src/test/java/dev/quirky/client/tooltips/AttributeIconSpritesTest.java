package dev.quirky.client.tooltips;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 属性 tooltip 图标的 GUI sprite 资源存在性校验。
 *
 * <p>26.2 的 GUI atlas（atlases/gui.json）只扫描 {@code textures/gui/sprites/} 目录，
 * sprite id 路径（不含命名空间）必须与文件相对路径一一对应，否则渲染紫黑棋盘格。
 * 该测试防止「移动了纹理文件但忘记改 id」或反之的回归。
 */
class AttributeIconSpritesTest {

	/** 与 AttributeLineCollector 中 6 个图标 id 对齐：sprite id = quirky:attribute/xxx → textures/gui/sprites/attribute/xxx.png */
	private static final List<String> SPRITE_PATHS = List.of(
		"attribute/attack_damage",
		"attribute/attack_speed",
		"attribute/armor",
		"attribute/toughness",
		"attribute/knockback",
		"attribute/movement"
	);

	@Test
	void allAttributeIconSpritesExist() {
		for (String path : SPRITE_PATHS) {
			String resource = "/assets/quirky/textures/gui/sprites/" + path + ".png";
			try (InputStream in = AttributeIconSpritesTest.class.getResourceAsStream(resource)) {
				assertNotNull(in, "missing GUI sprite: " + resource);
			} catch (Exception e) {
				throw new AssertionError("failed to read GUI sprite: " + resource, e);
			}
		}
	}
}
