package com.deadlands.network;

import com.deadlands.Deadlands;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: perform a scare the Horror Director chose. The steps travel with it (the client
 * has no copy of the data files): when each starts, what it is, which sound, how loud, where it is
 * heard from, and its distance or strength.
 */
public record HorrorScarePayload(String id, List<Step> steps) implements CustomPacketPayload {
	public static final Type<HorrorScarePayload> TYPE = new Type<>(Deadlands.id("horror_scare"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HorrorScarePayload> CODEC = StreamCodec.ofMember(HorrorScarePayload::write, HorrorScarePayload::read);

	public record Step(int at, String type, String sound, float volume, float pitch, String where, float distance, float amount) {
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeUtf(id);
		buf.writeVarInt(steps.size());
		for (Step step : steps) {
			buf.writeVarInt(step.at());
			buf.writeUtf(step.type());
			buf.writeUtf(step.sound());
			buf.writeFloat(step.volume());
			buf.writeFloat(step.pitch());
			buf.writeUtf(step.where());
			buf.writeFloat(step.distance());
			buf.writeFloat(step.amount());
		}
	}

	private static HorrorScarePayload read(RegistryFriendlyByteBuf buf) {
		String id = buf.readUtf();
		int count = buf.readVarInt();
		List<Step> steps = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			steps.add(new Step(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readFloat(), buf.readFloat(), buf.readUtf(), buf.readFloat(), buf.readFloat()));
		}
		return new HorrorScarePayload(id, steps);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
