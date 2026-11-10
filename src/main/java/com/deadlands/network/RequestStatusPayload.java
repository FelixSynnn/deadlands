package com.deadlands.network;

import com.deadlands.Deadlands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: "send me a fresh world status" (sent when the journal opens). */
public record RequestStatusPayload() implements CustomPacketPayload {
	public static final RequestStatusPayload INSTANCE = new RequestStatusPayload();
	public static final Type<RequestStatusPayload> TYPE = new Type<>(Deadlands.id("request_status"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RequestStatusPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
