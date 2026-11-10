package com.deadlands.network;

import com.deadlands.Deadlands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: play the opening (first arrival). {@code harleyNear} says whether Harley's camp is in view, at x, y, z. */
public record IntroPayload(boolean harleyNear, double x, double y, double z) implements CustomPacketPayload {
	public static final Type<IntroPayload> TYPE = new Type<>(Deadlands.id("intro"));
	public static final StreamCodec<RegistryFriendlyByteBuf, IntroPayload> CODEC = StreamCodec.ofMember(IntroPayload::write, IntroPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeBoolean(harleyNear);
		buf.writeDouble(x);
		buf.writeDouble(y);
		buf.writeDouble(z);
	}

	private static IntroPayload read(RegistryFriendlyByteBuf buf) {
		return new IntroPayload(buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
