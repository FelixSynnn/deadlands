package com.deadlands.network;

import com.deadlands.Deadlands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: a boss has arrived ({@link #START}), been defeated, or retreated at dawn. */
public record BossEventPayload(byte event, String kind, int tier) implements CustomPacketPayload {
	public static final byte START = 0;
	public static final byte DEFEATED = 1;
	public static final byte RETREATED = 2;
	public static final Type<BossEventPayload> TYPE = new Type<>(Deadlands.id("boss_event"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BossEventPayload> CODEC = StreamCodec.ofMember(BossEventPayload::write, BossEventPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeByte(event);
		buf.writeUtf(kind, 32);
		buf.writeVarInt(tier);
	}

	private static BossEventPayload read(RegistryFriendlyByteBuf buf) {
		return new BossEventPayload(buf.readByte(), buf.readUtf(32), buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
