package com.deadlands.network;

import com.deadlands.Deadlands;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: the journal's QUESTS page, as lines with a style each (0 heading, 1 to do, 2 done, 3 note). */
@SuppressWarnings("unused")
public record JournalPayload(List<Component> lines, List<Integer> styles) implements CustomPacketPayload {
	public static final Type<JournalPayload> TYPE = new Type<>(Deadlands.id("journal"));
	public static final StreamCodec<RegistryFriendlyByteBuf, JournalPayload> CODEC = StreamCodec.ofMember(JournalPayload::write, JournalPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		int count = Math.min(lines.size(), MAX_LINES);
		buf.writeVarInt(count);
		for (int i = 0; i < count; i++) {
			ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, lines.get(i));
			buf.writeByte(i < styles.size() ? styles.get(i) : 1);
		}
	}

	private static JournalPayload read(RegistryFriendlyByteBuf buf) {
		int count = Math.min(buf.readVarInt(), MAX_LINES);
		List<Component> lines = new ArrayList<>(count);
		List<Integer> styles = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			lines.add(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf));
			styles.add((int) buf.readByte());
		}
		return new JournalPayload(lines, styles);
	}

	public static final int MAX_LINES = 60;

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
