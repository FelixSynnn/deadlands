package com.deadlands.network;

import com.deadlands.Deadlands;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: the lore entries this survivor has read, and the words in any trail notes among them. */
@SuppressWarnings("unused")
public record ArchivePayload(List<String> ids, List<List<String>> args) implements CustomPacketPayload {
	public static final Type<ArchivePayload> TYPE = new Type<>(Deadlands.id("archive"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ArchivePayload> CODEC = StreamCodec.ofMember(ArchivePayload::write, ArchivePayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(ids.size());
		for (int i = 0; i < ids.size(); i++) {
			buf.writeUtf(ids.get(i), 64);
			List<String> words = i < args.size() ? args.get(i) : List.of();
			buf.writeVarInt(words.size());
			for (String word : words) {
				buf.writeUtf(word, 128);
			}
		}
	}

	private static ArchivePayload read(RegistryFriendlyByteBuf buf) {
		int count = Math.min(buf.readVarInt(), 1024);
		List<String> ids = new ArrayList<>(count);
		List<List<String>> args = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			ids.add(buf.readUtf(64));
			int n = Math.min(buf.readVarInt(), 8);
			List<String> words = new ArrayList<>(n);
			for (int k = 0; k < n; k++) {
				words.add(buf.readUtf(128));
			}
			args.add(words);
		}
		return new ArchivePayload(ids, args);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
