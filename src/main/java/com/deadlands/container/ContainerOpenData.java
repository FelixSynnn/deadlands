package com.deadlands.container;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Sent with the open-menu packet so the client builds a menu of the right size and style. */
public record ContainerOpenData(int rows, int style) {
	public static final StreamCodec<RegistryFriendlyByteBuf, ContainerOpenData> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, ContainerOpenData::rows,
		ByteBufCodecs.VAR_INT, ContainerOpenData::style,
		ContainerOpenData::new
	);

	public ContainerOpenData {
		rows = Math.max(1, Math.min(6, rows));
	}
}
