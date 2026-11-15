package com.deadlands.network;

import com.deadlands.Deadlands;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the story so far for the journal's Story page ({@code story.StoryArchive}). Chapters are
 * {@code key=status} (done, active, locked) in story order; cinematics are {@code id=seen|unseen}; the ending reached
 * (empty for none).
 */
public record StoryPayload(List<String> chapters, List<String> cinematics, String ending) implements CustomPacketPayload {
	public static final Type<StoryPayload> TYPE = new Type<>(Deadlands.id("story"));
	public static final StreamCodec<RegistryFriendlyByteBuf, StoryPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(32)), StoryPayload::chapters,
		ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(32)), StoryPayload::cinematics,
		ByteBufCodecs.STRING_UTF8, StoryPayload::ending,
		StoryPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
