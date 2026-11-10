package com.deadlands.network;

import com.deadlands.Deadlands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: a world event has begun ({@code WorldEvents.Kind} id) at x, z. */
public record WorldEventPayload(String kind, int x, int z) implements CustomPacketPayload {
	public static final Type<WorldEventPayload> TYPE = new Type<>(Deadlands.id("world_event"));
	public static final StreamCodec<RegistryFriendlyByteBuf, WorldEventPayload> CODEC = StreamCodec.ofMember(WorldEventPayload::write, WorldEventPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeUtf(kind, 32);
		buf.writeVarInt(x);
		buf.writeVarInt(z);
	}

	private static WorldEventPayload read(RegistryFriendlyByteBuf buf) {
		return new WorldEventPayload(buf.readUtf(32), buf.readVarInt(), buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
