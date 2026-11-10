package com.deadlands.network;

import com.deadlands.Deadlands;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: what a survivor says and what can be said back. Sent when a conversation
 * opens and again after each choice.
 *
 * @param entityId the survivor
 * @param title their name
 * @param subtitle their role, faction and how the faction regards the player
 * @param text what they say
 * @param options ids of the replies on offer (see {@code NpcDialogue.Option})
 */
public record DialoguePayload(int entityId, Component title, Component subtitle, Component text, List<String> options) implements CustomPacketPayload {
	public static final Type<DialoguePayload> TYPE = new Type<>(Deadlands.id("dialogue"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DialoguePayload> CODEC = StreamCodec.ofMember(DialoguePayload::write, DialoguePayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(entityId);
		ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, title);
		ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, subtitle);
		ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, text);
		buf.writeVarInt(options.size());
		for (String option : options) {
			buf.writeUtf(option, 32);
		}
	}

	private static DialoguePayload read(RegistryFriendlyByteBuf buf) {
		int entityId = buf.readVarInt();
		Component title = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
		Component subtitle = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
		Component text = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
		int count = Math.min(buf.readVarInt(), 12);
		List<String> options = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			options.add(buf.readUtf(32));
		}
		return new DialoguePayload(entityId, title, subtitle, text, options);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
