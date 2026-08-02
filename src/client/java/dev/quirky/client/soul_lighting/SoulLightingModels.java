package dev.quirky.client.soul_lighting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;

/**
 * 灵魂火焰粒子 sprite 解析（依赖 Minecraft 客户端运行时，不可单测）。
 *
 * <p>26.2 模型贴图替换（火把→灵魂火把等）因区块编译缓存机制无法按邻居方块动态判定，
 * 已放弃（见 spec §5.6）；灵魂光源功能保留火焰粒子替换。
 */
public final class SoulLightingModels {
	private SoulLightingModels() {
	}

	/** 灵魂火焰粒子 sprite（粒子图集中的 soul_fire_flame）。 */
	public static TextureAtlasSprite soulFlameSprite() {
		return Minecraft.getInstance().getAtlasManager()
			.get(new SpriteId(TextureAtlas.LOCATION_PARTICLES, Identifier.withDefaultNamespace("soul_fire_flame")));
	}
}
