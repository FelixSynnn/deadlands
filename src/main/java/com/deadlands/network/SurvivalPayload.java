package com.deadlands.network;

import com.deadlands.Deadlands;
import com.deadlands.survival.SurvivalData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the receiving survivor's own body state, for the HUD and for the sprint check.
 * Sent when it changes visibly (at most twice a second).
 *
 * @param active false in creative and spectator mode (the HUD hides)
 * @param conditions bit set of {@link com.deadlands.survival.Condition}
 */
public record SurvivalPayload(boolean active, float thirst, float stamina, float maxStamina, float temperature, float fatigue, float infection,
	float load, float capacity, int conditions) implements CustomPacketPayload {
	public static final Type<SurvivalPayload> TYPE = new Type<>(Deadlands.id("survival"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SurvivalPayload> CODEC = StreamCodec.ofMember(SurvivalPayload::write, SurvivalPayload::read);
	public static final SurvivalPayload INACTIVE = new SurvivalPayload(false, SurvivalData.MAX, SurvivalData.MAX, SurvivalData.MAX, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0);

	public static SurvivalPayload of(SurvivalData data) {
		return new SurvivalPayload(true, data.thirst(), data.stamina(), data.maxStamina(), data.temperature(), data.fatigue(), data.infection(),
			data.load(), data.capacity(), data.conditions());
	}

	/** Coarse fingerprint: two payloads with the same one look the same on the HUD. */
	public int fingerprint() {
		int h = active ? 1 : 0;
		h = h * 31 + Math.round(thirst);
		h = h * 31 + Math.round(stamina / 2.0F);
		h = h * 31 + Math.round(maxStamina);
		h = h * 31 + Math.round(temperature / 2.0F);
		h = h * 31 + Math.round(fatigue);
		h = h * 31 + Math.round(infection);
		h = h * 31 + Math.round(load);
		h = h * 31 + Math.round(capacity);
		return h * 31 + conditions;
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeBoolean(active);
		buf.writeFloat(thirst);
		buf.writeFloat(stamina);
		buf.writeFloat(maxStamina);
		buf.writeFloat(temperature);
		buf.writeFloat(fatigue);
		buf.writeFloat(infection);
		buf.writeFloat(load);
		buf.writeFloat(capacity);
		buf.writeVarInt(conditions);
	}

	private static SurvivalPayload read(RegistryFriendlyByteBuf buf) {
		return new SurvivalPayload(buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
			buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
