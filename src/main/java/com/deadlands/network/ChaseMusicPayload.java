package com.deadlands.network;

import com.deadlands.Deadlands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: whether this survivor is being chased hard enough for the chase music. */
public record ChaseMusicPayload(boolean chased) implements CustomPacketPayload {
	public static final Type<ChaseMusicPayload> TYPE = new Type<>(Deadlands.id("chase_music"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ChaseMusicPayload> CODEC = StreamCodec.ofMember(ChaseMusicPayload::write, ChaseMusicPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeBoolean(chased);
	}

	private static ChaseMusicPayload read(RegistryFriendlyByteBuf buf) {
		return new ChaseMusicPayload(buf.readBoolean());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
