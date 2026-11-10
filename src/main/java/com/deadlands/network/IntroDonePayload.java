package com.deadlands.network;

import com.deadlands.Deadlands;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the opening has finished or been skipped. */
@SuppressWarnings("unused")
public record IntroDonePayload(boolean skipped) implements CustomPacketPayload {
	public static final Type<IntroDonePayload> TYPE = new Type<>(Deadlands.id("intro_done"));
	public static final StreamCodec<RegistryFriendlyByteBuf, IntroDonePayload> CODEC = StreamCodec.ofMember(IntroDonePayload::write, IntroDonePayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeBoolean(skipped);
	}

	private static IntroDonePayload read(RegistryFriendlyByteBuf buf) {
		return new IntroDonePayload(buf.readBoolean());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
