package dev.quirky.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * 染色营火烟粒子：复制原版 CampfireSmokeParticle 行为（scale 3、寿命 80-130/信号火 280-330、
 * gravity 3e-6、上升漂移、末段淡出），构造时按 RGB 染色。原版类构造器为 private（已验），故自建。
 */
public class DyedCampfireSmokeParticle extends SingleQuadParticle {

	DyedCampfireSmokeParticle(
		ClientLevel level,
		double x,
		double y,
		double z,
		double xd,
		double yd,
		double zd,
		boolean isSignalFire,
		int rgb,
		TextureAtlasSprite sprite
	) {
		super(level, x, y, z, sprite);
		this.scale(3.0F);
		this.setSize(0.25F, 0.25F);
		if (isSignalFire) {
			this.lifetime = this.random.nextInt(50) + 280;
			this.alpha = 0.95F;
		} else {
			this.lifetime = this.random.nextInt(50) + 80;
			this.alpha = 0.9F;
		}
		this.gravity = 3.0E-6F;
		this.xd = xd;
		this.yd = yd + this.random.nextFloat() / 500.0F;
		this.zd = zd;
		this.setColor((rgb >> 16 & 0xFF) / 255.0F, (rgb >> 8 & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F);
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		if (this.age++ < this.lifetime && !(this.alpha <= 0.0F)) {
			this.xd = this.xd + this.random.nextFloat() / 5000.0F * (this.random.nextBoolean() ? 1 : -1);
			this.zd = this.zd + this.random.nextFloat() / 5000.0F * (this.random.nextBoolean() ? 1 : -1);
			this.yd = this.yd - this.gravity;
			this.move(this.xd, this.yd, this.zd);
			if (this.age >= this.lifetime - 60 && this.alpha > 0.01F) {
				this.alpha -= 0.015F;
			}
		} else {
			this.remove();
		}
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}
}
