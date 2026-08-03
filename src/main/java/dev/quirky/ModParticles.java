package dev.quirky;

import com.mojang.serialization.MapCodec;
import dev.quirky.particle.DyedCampfireSmokeOption;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * 自定义粒子类型注册。仿 ParticleTypes.BLOCK 的 codec/streamCodec 模式。
 * 在 QuirkyMod.onInitialize 中调用 register()（服务端进程也需注册类型本身，客户端另注册渲染工厂）。
 */
public final class ModParticles {
	public static final ParticleType<DyedCampfireSmokeOption> DYED_CAMPFIRE_SMOKE = new ParticleType<>(false) {
		@Override
		public MapCodec<DyedCampfireSmokeOption> codec() {
			return DyedCampfireSmokeOption.CODEC;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, DyedCampfireSmokeOption> streamCodec() {
			return DyedCampfireSmokeOption.STREAM_CODEC;
		}
	};

	private ModParticles() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, QuirkyMod.id("dyed_campfire_smoke"), DYED_CAMPFIRE_SMOKE);
	}
}
