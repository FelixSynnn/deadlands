package com.deadlands.network;

import com.deadlands.Deadlands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: how loud the receiving survivor is right now, 0 (silent) to 1 (a gunshot's worth), for the noise meter.
 * Sent when it changes visibly (see {@link com.deadlands.noise.NoiseMeter}).
 */
public record NoisePayload(float level) implements CustomPacketPayload {
	public static final Type<NoisePayload> TYPE = new Type<>(Deadlands.id("noise_level"));
	public static final StreamCodec<RegistryFriendlyByteBuf, NoisePayload> CODEC = StreamCodec.ofMember(NoisePayload::write, NoisePayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeFloat(level);
	}

	private static NoisePayload read(RegistryFriendlyByteBuf buf) {
		return new NoisePayload(buf.readFloat());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
