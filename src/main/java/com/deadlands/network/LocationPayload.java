package com.deadlands.network;

import com.deadlands.Deadlands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the kind of place the survivor is in ({@code LocationTracker.Place} ordinal),
 * whether its town has power, and whether it is a settlement that tolerates them (safe-zone music).
 */
public record LocationPayload(int place, boolean powered, boolean safe) implements CustomPacketPayload {
	public static final Type<LocationPayload> TYPE = new Type<>(Deadlands.id("location"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LocationPayload> CODEC = StreamCodec.ofMember(LocationPayload::write, LocationPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(place);
		buf.writeBoolean(powered);
		buf.writeBoolean(safe);
	}

	private static LocationPayload read(RegistryFriendlyByteBuf buf) {
		return new LocationPayload(buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
