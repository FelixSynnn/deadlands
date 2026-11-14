package com.deadlands.network;

import com.deadlands.Deadlands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the journal was opened; send the story so far ({@link StoryPayload}). */
public record StoryRequestPayload() implements CustomPacketPayload {
	public static final StoryRequestPayload INSTANCE = new StoryRequestPayload();
	public static final Type<StoryRequestPayload> TYPE = new Type<>(Deadlands.id("story_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, StoryRequestPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
