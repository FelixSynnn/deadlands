package com.deadlands.network;

import com.deadlands.Deadlands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the character picked on the select screen (an empty id: decide later). */
public record CharacterPickPayload(String character, boolean afterIntro) implements CustomPacketPayload {
	public static final Type<CharacterPickPayload> TYPE = new Type<>(Deadlands.id("character_pick"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CharacterPickPayload> CODEC = StreamCodec.ofMember(CharacterPickPayload::write, CharacterPickPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeUtf(character, 32);
		buf.writeBoolean(afterIntro);
	}

	private static CharacterPickPayload read(RegistryFriendlyByteBuf buf) {
		return new CharacterPickPayload(buf.readUtf(32), buf.readBoolean());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
