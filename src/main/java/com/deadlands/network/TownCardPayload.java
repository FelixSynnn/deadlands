package com.deadlands.network;

import com.deadlands.Deadlands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: a survivor walked into a town; show its card. Apart from the name and the
 * population, every field is a translation key (or a key's argument), so the card reads in the
 * client's language.
 *
 * @param size           town size id (settlement, small_town, large_town, city)
 * @param condition      town condition id (evacuated, overrun, military, survivor, bandit, abandoned)
 * @param landmarkKey    the town's most notable building, or empty if none was built
 * @param firstDiscovery true if this survivor is the first to reach the town
 * @param powered        true once a generator has restored the town's power
 */
public record TownCardPayload(String name, String size, String condition, int population, String factionKey, String factionArg,
	String historyKey, String landmarkKey, String lootKey, String storyKey, boolean firstDiscovery, boolean powered) implements CustomPacketPayload {
	public static final Type<TownCardPayload> TYPE = new Type<>(Deadlands.id("town_card"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TownCardPayload> CODEC = StreamCodec.ofMember(TownCardPayload::write, TownCardPayload::read);

	private static final int MAX_NAME = 48;
	private static final int MAX_KEY = 96;

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeUtf(name, MAX_NAME);
		buf.writeUtf(size, MAX_KEY);
		buf.writeUtf(condition, MAX_KEY);
		buf.writeVarInt(population);
		buf.writeUtf(factionKey, MAX_KEY);
		buf.writeUtf(factionArg, MAX_NAME);
		buf.writeUtf(historyKey, MAX_KEY);
		buf.writeUtf(landmarkKey, MAX_KEY);
		buf.writeUtf(lootKey, MAX_KEY);
		buf.writeUtf(storyKey, MAX_KEY);
		buf.writeBoolean(firstDiscovery);
		buf.writeBoolean(powered);
	}

	private static TownCardPayload read(RegistryFriendlyByteBuf buf) {
		return new TownCardPayload(buf.readUtf(MAX_NAME), buf.readUtf(MAX_KEY), buf.readUtf(MAX_KEY), buf.readVarInt(), buf.readUtf(MAX_KEY),
			buf.readUtf(MAX_NAME), buf.readUtf(MAX_KEY), buf.readUtf(MAX_KEY), buf.readUtf(MAX_KEY), buf.readUtf(MAX_KEY), buf.readBoolean(),
			buf.readBoolean());
	}
}
