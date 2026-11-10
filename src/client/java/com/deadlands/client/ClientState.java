package com.deadlands.client;

import com.deadlands.network.WorldStatusPayload;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Client-side copy of the state the server last sent. Only touched on the client thread. */
public final class ClientState {
	private static @Nullable WorldStatusPayload status;

	private ClientState() {
	}

	public static Optional<WorldStatusPayload> status() {
		return Optional.ofNullable(status);
	}

	public static void setStatus(WorldStatusPayload newStatus) {
		status = newStatus;
	}

	private static volatile com.deadlands.network.SkillsPayload skills = new com.deadlands.network.SkillsPayload(new int[0]);

	public static com.deadlands.network.SkillsPayload skills() {
		return skills;
	}

	public static void setSkills(com.deadlands.network.SkillsPayload payload) {
		skills = payload;
	}

	private static volatile com.deadlands.network.JournalPayload journal = new com.deadlands.network.JournalPayload(java.util.List.of(), java.util.List.of());

	/** The journal's TASKS page as the server last sent it. */
	public static com.deadlands.network.JournalPayload journal() {
		return journal;
	}

	public static void setJournal(com.deadlands.network.JournalPayload payload) {
		journal = payload;
	}

	private static volatile com.deadlands.network.ArchivePayload archive = new com.deadlands.network.ArchivePayload(java.util.List.of(), java.util.List.of());

	/** The lore entries this survivor has read. */
	public static com.deadlands.network.ArchivePayload archive() {
		return archive;
	}

	public static void setArchive(com.deadlands.network.ArchivePayload payload) {
		archive = payload;
	}

	public static void clear() {
		archive = new com.deadlands.network.ArchivePayload(java.util.List.of(), java.util.List.of());
		skills = new com.deadlands.network.SkillsPayload(new int[0]);
		journal = new com.deadlands.network.JournalPayload(java.util.List.of(), java.util.List.of());
		status = null;
	}
}
