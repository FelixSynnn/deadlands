package com.deadlands.network;

import com.deadlands.Deadlands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: the DEADLANDS weather ({@code WeatherDirector.Kind} ordinal) and its strength, 0 to 1. */
public record WeatherPayload(int kind, float strength) implements CustomPacketPayload {
	public static final Type<WeatherPayload> TYPE = new Type<>(Deadlands.id("weather"));
	public static final StreamCodec<RegistryFriendlyByteBuf, WeatherPayload> CODEC = StreamCodec.ofMember(WeatherPayload::write, WeatherPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(kind);
		buf.writeFloat(strength);
	}

	private static WeatherPayload read(RegistryFriendlyByteBuf buf) {
		return new WeatherPayload(buf.readVarInt(), buf.readFloat());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
