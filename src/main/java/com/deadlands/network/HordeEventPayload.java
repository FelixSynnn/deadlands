package com.deadlands.network;

import com.deadlands.Deadlands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: what the horde is doing. {@code START}, {@code BROKEN} and {@code DAWN} show a
 * card with its sting; {@code STATUS} (once a second while a horde is on) updates the counter;
 * {@code CLEAR} hides it.
 *
 * @param remaining infected still to beat (alive members plus those not yet arrived)
 */
public record HordeEventPayload(byte kind, long day, int total, int remaining) implements CustomPacketPayload {
	public static final byte STATUS = 0;
	public static final byte START = 1;
	public static final byte BROKEN = 2;
	public static final byte DAWN = 3;
	public static final byte CLEAR = 4;

	public static final Type<HordeEventPayload> TYPE = new Type<>(Deadlands.id("horde"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HordeEventPayload> CODEC = StreamCodec.ofMember(HordeEventPayload::write, HordeEventPayload::read);

	public static HordeEventPayload clear() {
		return new HordeEventPayload(CLEAR, 0L, 0, 0);
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeByte(kind);
		buf.writeVarLong(day);
		buf.writeVarInt(total);
		buf.writeVarInt(remaining);
	}

	private static HordeEventPayload read(RegistryFriendlyByteBuf buf) {
		return new HordeEventPayload(buf.readByte(), buf.readVarLong(), buf.readVarInt(), buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
