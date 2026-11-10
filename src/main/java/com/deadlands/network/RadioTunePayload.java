package com.deadlands.network;

import com.deadlands.Deadlands;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the radio is tuned to station {@code station} (0 to 4) and wants to hear it. */
@SuppressWarnings("unused")
public record RadioTunePayload(int station) implements CustomPacketPayload {
	public static final Type<RadioTunePayload> TYPE = new Type<>(Deadlands.id("radio_tune"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RadioTunePayload> CODEC = StreamCodec.ofMember(RadioTunePayload::write, RadioTunePayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(station);
	}

	private static RadioTunePayload read(RegistryFriendlyByteBuf buf) {
		return new RadioTunePayload(buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
