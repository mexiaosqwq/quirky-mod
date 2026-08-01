package dev.quirky.equip_swap;

import dev.quirky.QuirkyMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record EquipSwapPayload(int containerId, int slotIndex) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<EquipSwapPayload> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(QuirkyMod.MOD_ID, "equip_swap"));
	public static final StreamCodec<FriendlyByteBuf, EquipSwapPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT,
		EquipSwapPayload::containerId,
		ByteBufCodecs.VAR_INT,
		EquipSwapPayload::slotIndex,
		EquipSwapPayload::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
