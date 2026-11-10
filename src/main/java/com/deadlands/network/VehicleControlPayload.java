package com.deadlands.network;

import com.deadlands.Deadlands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the driver pressed a switch ({@code VehicleEntity.ACTION_HORN} or {@code ACTION_LIGHTS}). */
public record VehicleControlPayload(int action) implements CustomPacketPayload {
	public static final Type<VehicleControlPayload> TYPE = new Type<>(Deadlands.id("vehicle_control"));
	public static final StreamCodec<RegistryFriendlyByteBuf, VehicleControlPayload> CODEC = StreamCodec.ofMember(VehicleControlPayload::write,
		VehicleControlPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(action);
	}

	private static VehicleControlPayload read(RegistryFriendlyByteBuf buf) {
		return new VehicleControlPayload(buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
