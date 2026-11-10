package com.deadlands.network;

import com.deadlands.Deadlands;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: the story has ended this way, with these characters aboard. */
public record EndingPayload(String ending, List<String> companions) implements CustomPacketPayload {
	public static final Type<EndingPayload> TYPE = new Type<>(Deadlands.id("ending"));
	public static final StreamCodec<RegistryFriendlyByteBuf, EndingPayload> CODEC = StreamCodec.ofMember(EndingPayload::write, EndingPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeUtf(ending, 32);
		buf.writeVarInt(companions.size());
		for (String s : companions) {
			buf.writeUtf(s, 32);
		}
	}

	private static EndingPayload read(RegistryFriendlyByteBuf buf) {
		String ending = buf.readUtf(32);
		int n = Math.min(buf.readVarInt(), 8);
		List<String> companions = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			companions.add(buf.readUtf(32));
		}
		return new EndingPayload(ending, companions);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
