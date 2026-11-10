package com.deadlands.npc;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The four recurring characters. Each has a fixed name, a place in the story, and a skin that is
 * never guessed from a username: the mod ships one
 * ({@code textures/entity/survivor/character/<id>.png}, copied by the asset generator from
 * {@code character-skins/<Name>.png}), and a PNG at {@code config/deadlands/skins/<id>.png} (an
 * ordinary 64x64 Minecraft skin file) replaces it. Slim or classic arms are read from the skin.
 *
 * <p>They are survivors, not players: their name is on their nametag only and they never appear in
 * the player list, so a player with the same name is never confused with them, whoever arrives
 * first. Each appears once per world ({@link NpcWorldData}), and they fight side by side
 * ({@link NpcEntity}).
 */
public enum NpcCharacter {
	HARLEY("harley", "Harley", NpcRole.DOCTOR, Faction.RESEARCH, 5),
	ITZBREADLOAF("itzbreadloaf", "Itzbreadloaf", NpcRole.MECHANIC, Faction.SETTLERS, 4),
	IMLILYLOL("imlilylol", "Imlilylol", NpcRole.TRADER, Faction.DRIFTERS, 4),
	FELIXSYN("felixsyn", "FelixSyn", NpcRole.GUARD, Faction.SETTLERS, 4);

	private final String id;
	private final String displayName;
	private final NpcRole role;
	private final Faction faction;
	private final int storyLines;

	NpcCharacter(String id, String displayName, NpcRole role, Faction faction, int storyLines) {
		this.id = id;
		this.displayName = displayName;
		this.role = role;
		this.faction = faction;
		this.storyLines = storyLines;
	}

	public String id() {
		return id;
	}

	public String displayName() {
		return displayName;
	}

	public NpcRole role() {
		return role;
	}

	public Faction faction() {
		return faction;
	}

	/** How many parts their history is told in ({@code npc.deadlands.character.<id>.story.<n>}). */
	public int storyLines() {
		return storyLines;
	}

	public static @Nullable NpcCharacter byId(String id) {
		for (NpcCharacter character : values()) {
			if (character.id.equals(id.toLowerCase(Locale.ROOT))) {
				return character;
			}
		}
		return null;
	}
}
