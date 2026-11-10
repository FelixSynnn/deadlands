package com.deadlands.network;

import com.deadlands.Deadlands;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the survivor's choice at the helicopter. */
public record EndingChosenPayload(int entityId, String option) implements CustomPacketPayload {
	public static final Type<EndingChosenPayload> TYPE = new Type<>(Deadlands.id("ending_chosen"));
	public static final StreamCodec<RegistryFriendlyByteBuf, EndingChosenPayload> CODEC = StreamCodec.ofMember(EndingChosenPayload::write, EndingChosenPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(entityId);
		buf.writeUtf(option, 32);
	}

	private static EndingChosenPayload read(RegistryFriendlyByteBuf buf) {
		return new EndingChosenPayload(buf.readVarInt(), buf.readUtf(32));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
