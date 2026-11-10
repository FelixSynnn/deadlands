package com.deadlands.network;

import com.deadlands.Deadlands;
import com.deadlands.persistence.ChronicleEntry;
import com.deadlands.progression.ScheduleSettings;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: everything the journal and HUD need about the world and this survivor.
 * Days are 1-based; 0 means "never" for {@code lastDeathDay}.
 */
public record WorldStatusPayload(
	long day,
	ScheduleSettings schedule,
	long firstJoinDay,
	long lastDeathDay,
	int deaths,
	long daysSurvived,
	int infectedKilled,
	List<ChronicleEntry> chronicle
) implements CustomPacketPayload {
	public static final int MAX_CHRONICLE = 32;
	public static final Type<WorldStatusPayload> TYPE = new Type<>(Deadlands.id("world_status"));
	public static final StreamCodec<RegistryFriendlyByteBuf, WorldStatusPayload> CODEC = StreamCodec.ofMember(WorldStatusPayload::write, WorldStatusPayload::read);

	public WorldStatusPayload {
		chronicle = List.copyOf(chronicle.size() > MAX_CHRONICLE ? chronicle.subList(chronicle.size() - MAX_CHRONICLE, chronicle.size()) : chronicle);
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarLong(day);
		schedule.write(buf);
		buf.writeVarLong(firstJoinDay);
		buf.writeVarLong(lastDeathDay);
		buf.writeVarInt(deaths);
		buf.writeVarLong(daysSurvived);
		buf.writeVarInt(infectedKilled);
		buf.writeVarInt(chronicle.size());
		for (ChronicleEntry entry : chronicle) {
			entry.write(buf);
		}
	}

	private static WorldStatusPayload read(RegistryFriendlyByteBuf buf) {
		long day = buf.readVarLong();
		ScheduleSettings schedule = ScheduleSettings.read(buf);
		long firstJoin = buf.readVarLong();
		long lastDeath = buf.readVarLong();
		int deaths = buf.readVarInt();
		long survived = buf.readVarLong();
		int killed = buf.readVarInt();
		int count = Math.min(buf.readVarInt(), MAX_CHRONICLE);
		List<ChronicleEntry> chronicle = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			chronicle.add(ChronicleEntry.read(buf));
		}
		return new WorldStatusPayload(day, schedule, firstJoin, lastDeath, deaths, survived, killed, chronicle);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
