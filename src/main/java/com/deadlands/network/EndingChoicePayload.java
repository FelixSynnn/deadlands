package com.deadlands.network;

import com.deadlands.Deadlands;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: the helicopter is ready; offer these choices (see {@code story.Endings}). */
public record EndingChoicePayload(int entityId, boolean cureDone, List<String> options) implements CustomPacketPayload {
	public static final Type<EndingChoicePayload> TYPE = new Type<>(Deadlands.id("ending_choice"));
	public static final StreamCodec<RegistryFriendlyByteBuf, EndingChoicePayload> CODEC = StreamCodec.ofMember(EndingChoicePayload::write, EndingChoicePayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(entityId);
		buf.writeBoolean(cureDone);
		buf.writeVarInt(options.size());
		for (String s : options) {
			buf.writeUtf(s, 32);
		}
	}

	private static EndingChoicePayload read(RegistryFriendlyByteBuf buf) {
		int entityId = buf.readVarInt();
		boolean cureDone = buf.readBoolean();
		int n = Math.min(buf.readVarInt(), 8);
		List<String> options = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			options.add(buf.readUtf(32));
		}
		return new EndingChoicePayload(entityId, cureDone, options);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
