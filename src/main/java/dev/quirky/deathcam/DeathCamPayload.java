package dev.quirky.deathcam;

import dev.quirky.QuirkyMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * 服务端 → 客户端：玩家死亡时的镜头锚点信息。
 * pos 为死亡位置（镜头环绕中心），yaw/pitch 为玩家死亡时的朝向（镜头环绕起始角）。
 * 纯视觉数据，客户端自用，无服务端处理逻辑。
 */
public record DeathCamPayload(Vec3 pos, float yaw, float pitch) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<DeathCamPayload> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(QuirkyMod.MOD_ID, "death_cam"));
	public static final StreamCodec<FriendlyByteBuf, DeathCamPayload> STREAM_CODEC = StreamCodec.composite(
		Vec3.STREAM_CODEC,
		DeathCamPayload::pos,
		ByteBufCodecs.FLOAT,
		DeathCamPayload::yaw,
		ByteBufCodecs.FLOAT,
		DeathCamPayload::pitch,
		DeathCamPayload::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
