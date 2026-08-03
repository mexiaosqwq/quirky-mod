package dev.quirky.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.quirky.ModParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;

/**
 * 染色营火烟粒子参数：RGB 颜色 + 信号火标记（决定粒子寿命/透明度，与原版一致）。
 * 注册进 ModParticles.DYED_CAMPFIRE_SMOKE，codec/streamCodec 供数据包与存档路径使用。
 */
public class DyedCampfireSmokeOption implements ParticleOptions {
	private final int color;
	private final boolean signalFire;

	public static final MapCodec<DyedCampfireSmokeOption> CODEC = RecordCodecBuilder.mapCodec(
		i -> i.group(
			ExtraCodecs.RGB_COLOR_CODEC.fieldOf("color").forGetter(o -> o.color),
			Codec.BOOL.fieldOf("signal_fire").forGetter(o -> o.signalFire)
		).apply(i, DyedCampfireSmokeOption::new)
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, DyedCampfireSmokeOption> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.INT,
		o -> o.color,
		ByteBufCodecs.BOOL,
		o -> o.signalFire,
		DyedCampfireSmokeOption::new
	);

	public DyedCampfireSmokeOption(int color, boolean signalFire) {
		this.color = color;
		this.signalFire = signalFire;
	}

	@Override
	public ParticleType<DyedCampfireSmokeOption> getType() {
		return ModParticles.DYED_CAMPFIRE_SMOKE;
	}

	public int color() {
		return this.color;
	}

	public boolean signalFire() {
		return this.signalFire;
	}
}
