package com.deadlands.client;

import com.deadlands.network.CharactersPayload;
import com.deadlands.npc.NpcCharacter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Which character each survivor online is playing, as last sent by the server (Phase 6). */
public final class ClientCharacters {
	private static volatile Map<UUID, String> chosen = Map.of();

	private ClientCharacters() {
	}

	public static void set(List<CharactersPayload.Entry> entries) {
		Map<UUID, String> map = new HashMap<>();
		for (CharactersPayload.Entry entry : entries) {
			map.put(entry.player(), entry.character());
		}
		chosen = map;
	}

	public static @Nullable NpcCharacter of(UUID player) {
		String id = chosen.get(player);
		return id == null ? null : NpcCharacter.byId(id);
	}

	public static void reset() {
		chosen = Map.of();
	}
}
