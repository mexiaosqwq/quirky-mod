package dev.quirky.client.particle;

import dev.quirky.particle.DyedCampfireSmokeOption;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.RandomSource;

/**
 * 染色营火烟粒子工厂：用粒子图集的营火烟 sprite 建粒子，透明度与原版一致（cosy 0.9 / signal 0.95）。
 */
public class DyedCampfireSmokeProvider implements ParticleProvider<DyedCampfireSmokeOption> {
	private final SpriteSet sprites;

	public DyedCampfireSmokeProvider(SpriteSet sprites) {
		this.sprites = sprites;
	}

	@Override
	public Particle createParticle(
		DyedCampfireSmokeOption options,
		ClientLevel level,
		double x,
		double y,
		double z,
		double xd,
		double yd,
		double zd,
		RandomSource random
	) {
		DyedCampfireSmokeParticle particle = new DyedCampfireSmokeParticle(
			level, x, y, z, xd, yd, zd, options.signalFire(), options.color(), this.sprites.get(random)
		);
		return particle;
	}
}
