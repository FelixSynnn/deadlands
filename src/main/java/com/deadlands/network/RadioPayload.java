package com.deadlands.network;

import com.deadlands.Deadlands;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: what a radio station is carrying for this survivor, and how strong the signal is (5 to 100). */
@SuppressWarnings("unused")
public record RadioPayload(int station, List<Component> lines, int signal) implements CustomPacketPayload {
	public static final Type<RadioPayload> TYPE = new Type<>(Deadlands.id("radio"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RadioPayload> CODEC = StreamCodec.ofMember(RadioPayload::write, RadioPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(station);
		buf.writeVarInt(signal);
		buf.writeVarInt(lines.size());
		for (Component line : lines) {
			ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, line);
		}
	}

	private static RadioPayload read(RegistryFriendlyByteBuf buf) {
		int station = buf.readVarInt();
		int signal = buf.readVarInt();
		int count = Math.min(buf.readVarInt(), 16);
		List<Component> lines = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			lines.add(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf));
		}
		return new RadioPayload(station, lines, signal);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
