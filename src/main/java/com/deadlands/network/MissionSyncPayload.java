package com.deadlands.network;

import com.deadlands.Deadlands;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the missions as this survivor sees them, for the top-right tracker and the
 * pause screen's Missions tab. {@code received} is false until they have received a mission (the
 * tab stays hidden until then). Texts are translation keys.
 */
public record MissionSyncPayload(boolean received, List<Entry> missions) implements CustomPacketPayload {
	public static final int ACTIVE = 0;
	public static final int COMPLETED = 1;
	public static final int FAILED = 2;
	public static final Type<MissionSyncPayload> TYPE = new Type<>(Deadlands.id("missions"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MissionSyncPayload> CODEC = StreamCodec.ofMember(MissionSyncPayload::write, MissionSyncPayload::read);

	public record Objective(String text, int progress, int count, boolean done) {
	}

	public record Entry(String id, String title, String description, String giver, int state, List<Objective> objectives, List<String> lore) {
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeBoolean(received);
		buf.writeVarInt(missions.size());
		for (Entry entry : missions) {
			buf.writeUtf(entry.id());
			buf.writeUtf(entry.title());
			buf.writeUtf(entry.description());
			buf.writeUtf(entry.giver());
			buf.writeVarInt(entry.state());
			buf.writeVarInt(entry.objectives().size());
			for (Objective objective : entry.objectives()) {
				buf.writeUtf(objective.text());
				buf.writeVarInt(objective.progress());
				buf.writeVarInt(objective.count());
				buf.writeBoolean(objective.done());
			}
			buf.writeVarInt(entry.lore().size());
			for (String lore : entry.lore()) {
				buf.writeUtf(lore);
			}
		}
	}

	private static MissionSyncPayload read(RegistryFriendlyByteBuf buf) {
		boolean received = buf.readBoolean();
		int count = buf.readVarInt();
		List<Entry> missions = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			String id = buf.readUtf();
			String title = buf.readUtf();
			String description = buf.readUtf();
			String giver = buf.readUtf();
			int state = buf.readVarInt();
			int objectives = buf.readVarInt();
			List<Objective> list = new ArrayList<>(objectives);
			for (int j = 0; j < objectives; j++) {
				list.add(new Objective(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
			}
			int loreCount = buf.readVarInt();
			List<String> lore = new ArrayList<>(loreCount);
			for (int j = 0; j < loreCount; j++) {
				lore.add(buf.readUtf());
			}
			missions.add(new Entry(id, title, description, giver, state, list, lore));
		}
		return new MissionSyncPayload(received, missions);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
