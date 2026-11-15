package com.deadlands.network;

import com.deadlands.Deadlands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the disaster under way ({@code world.disaster.Disaster} index, or -1 for none),
 * whether it is still only a warning, and its strength right now, 0 to 1 (the tremor's shake follows it).
 */
public record DisasterPayload(int disaster, boolean warning, float strength) implements CustomPacketPayload {
	public static final Type<DisasterPayload> TYPE = new Type<>(Deadlands.id("disaster"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DisasterPayload> CODEC = StreamCodec.ofMember(DisasterPayload::write, DisasterPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(disaster + 1);
		buf.writeBoolean(warning);
		buf.writeFloat(strength);
	}

	private static DisasterPayload read(RegistryFriendlyByteBuf buf) {
		return new DisasterPayload(buf.readVarInt() - 1, buf.readBoolean(), buf.readFloat());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
