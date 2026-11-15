package com.deadlands.network;

import com.deadlands.Deadlands;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the ids of every hidden DEADLANDS achievement. Minecraft never sends a hidden
 * achievement before it is earned, so the Achievements screen would not know it exists; with this it
 * lists it as "???" (no name, no description, no spoiler).
 */
public record AchievementSecretsPayload(List<String> ids) implements CustomPacketPayload {
	public static final Type<AchievementSecretsPayload> TYPE = new Type<>(Deadlands.id("achievement_secrets"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AchievementSecretsPayload> CODEC =
		StreamCodec.ofMember(AchievementSecretsPayload::write, AchievementSecretsPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(ids.size());
		for (String id : ids) {
			buf.writeUtf(id);
		}
	}

	private static AchievementSecretsPayload read(RegistryFriendlyByteBuf buf) {
		int n = buf.readVarInt();
		List<String> ids = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			ids.add(buf.readUtf());
		}
		return new AchievementSecretsPayload(ids);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
