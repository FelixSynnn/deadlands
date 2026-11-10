package com.deadlands.network;

import com.deadlands.Deadlands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: play a cutscene ({@code Cutscenes} id) around an anchor point, facing yaw. */
public record CutscenePayload(String id, double x, double y, double z, float yaw) implements CustomPacketPayload {
	public static final Type<CutscenePayload> TYPE = new Type<>(Deadlands.id("cutscene"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CutscenePayload> CODEC = StreamCodec.ofMember(CutscenePayload::write, CutscenePayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeUtf(id, 32);
		buf.writeDouble(x);
		buf.writeDouble(y);
		buf.writeDouble(z);
		buf.writeFloat(yaw);
	}

	private static CutscenePayload read(RegistryFriendlyByteBuf buf) {
		return new CutscenePayload(buf.readUtf(32), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
