package com.deadlands.network;

import com.deadlands.Deadlands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: show the DAY card. {@code firstArrival} is true for a survivor's very first
 * day in this world, which uses the arrival subtitle instead of a random one.
 */
public record DayBeginPayload(long day, boolean firstArrival) implements CustomPacketPayload {
	public static final Type<DayBeginPayload> TYPE = new Type<>(Deadlands.id("day_begin"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DayBeginPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_LONG, DayBeginPayload::day,
		ByteBufCodecs.BOOL, DayBeginPayload::firstArrival,
		DayBeginPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
