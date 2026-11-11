package com.deadlands.network;

import com.deadlands.Deadlands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: what the survivor is looking at, so the Horror Director never scares them in
 * the middle of it. {@link #FREE} playing, {@link #SCREEN} some other screen (inventory, map),
 * {@link #STORY} a conversation or cutscene (followed by two minutes of grace).
 */
public record HorrorBusyPayload(int state) implements CustomPacketPayload {
	public static final int FREE = 0;
	public static final int SCREEN = 1;
	public static final int STORY = 2;
	public static final Type<HorrorBusyPayload> TYPE = new Type<>(Deadlands.id("horror_busy"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HorrorBusyPayload> CODEC = StreamCodec.ofMember(HorrorBusyPayload::write, HorrorBusyPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(state);
	}

	private static HorrorBusyPayload read(RegistryFriendlyByteBuf buf) {
		return new HorrorBusyPayload(buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
