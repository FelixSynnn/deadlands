package com.deadlands.network;

import com.deadlands.Deadlands;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: which character each survivor online is playing (see {@link com.deadlands.player.SurvivorCharacter}). */
public record CharactersPayload(List<Entry> entries) implements CustomPacketPayload {
	public static final Type<CharactersPayload> TYPE = new Type<>(Deadlands.id("characters"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CharactersPayload> CODEC = StreamCodec.ofMember(CharactersPayload::write, CharactersPayload::read);

	/** One survivor and the id of the {@link com.deadlands.npc.NpcCharacter} they are. */
	public record Entry(UUID player, String character) {
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(entries.size());
		for (Entry entry : entries) {
			buf.writeUUID(entry.player());
			buf.writeUtf(entry.character(), 32);
		}
	}

	private static CharactersPayload read(RegistryFriendlyByteBuf buf) {
		int n = Math.min(buf.readVarInt(), 64);
		List<Entry> entries = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			entries.add(new Entry(buf.readUUID(), buf.readUtf(32)));
		}
		return new CharactersPayload(entries);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
