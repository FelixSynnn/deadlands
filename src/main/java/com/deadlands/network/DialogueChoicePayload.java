package com.deadlands.network;

import com.deadlands.Deadlands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the reply chosen in a conversation with the survivor {@code entityId}. */
public record DialogueChoicePayload(int entityId, String option) implements CustomPacketPayload {
	public static final Type<DialogueChoicePayload> TYPE = new Type<>(Deadlands.id("dialogue_choice"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DialogueChoicePayload> CODEC = StreamCodec.ofMember(DialogueChoicePayload::write,
		DialogueChoicePayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(entityId);
		buf.writeUtf(option, 32);
	}

	private static DialogueChoicePayload read(RegistryFriendlyByteBuf buf) {
		return new DialogueChoicePayload(buf.readVarInt(), buf.readUtf(32));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
