package com.deadlands.network;

import com.deadlands.Deadlands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: show the character select screen (Phase 6). {@code afterIntro}: Harley speaks once it's done. */
public record OpenCharacterSelectPayload(boolean afterIntro) implements CustomPacketPayload {
	public static final Type<OpenCharacterSelectPayload> TYPE = new Type<>(Deadlands.id("character_select"));
	public static final StreamCodec<RegistryFriendlyByteBuf, OpenCharacterSelectPayload> CODEC = StreamCodec.ofMember(OpenCharacterSelectPayload::write, OpenCharacterSelectPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeBoolean(afterIntro);
	}

	private static OpenCharacterSelectPayload read(RegistryFriendlyByteBuf buf) {
		return new OpenCharacterSelectPayload(buf.readBoolean());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
