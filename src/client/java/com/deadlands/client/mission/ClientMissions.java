package com.deadlands.client.mission;

import com.deadlands.network.MissionSyncPayload;
import java.util.List;

/** The missions as the server last described them to this survivor. */
public final class ClientMissions {
	private static MissionSyncPayload state = new MissionSyncPayload(false, List.of());

	private ClientMissions() {
	}

	public static void update(MissionSyncPayload payload) {
		state = payload;
		MissionTrackerHud.onUpdate(payload);
	}

	/** Whether the survivor has received a mission (the Missions tab stays hidden until then). */
	public static boolean received() {
		return state.received();
	}

	public static List<MissionSyncPayload.Entry> all() {
		return state.missions();
	}

	public static void reset() {
		state = new MissionSyncPayload(false, List.of());
		MissionTrackerHud.reset();
	}
}
