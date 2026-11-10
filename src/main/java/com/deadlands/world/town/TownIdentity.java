package com.deadlands.world.town;

import com.deadlands.world.Hashing;

/**
 * Who a town was: its name, how many people lived there, what happened to it, who held it last,
 * how much is left to scavenge, and one story. Everything is derived from the world seed and the
 * town's site, so every player and every session sees the same identity without saving anything.
 *
 * <p>Text other than the name is stored as translation keys ({@code town.deadlands.*}), so each
 * client reads it in its own language.
 *
 * @param population people living there before the outbreak
 * @param factionArg argument of the faction line (a group name), empty if the line has none
 */
public record TownIdentity(String name, int population, String factionKey, String factionArg, String historyKey, String lootKey, String storyKey) {
	public static final int HISTORIES_PER_CONDITION = 3;
	public static final int STORIES = 8;

	private static final String[] FIRST = {
		"Ash", "Birch", "Cedar", "Elm", "Grey", "Iron", "Mill", "Oak", "Pine", "Raven", "Red", "Salt", "Silver", "Stone",
		"Willow", "Wolf", "Black", "Clear", "Cold", "Fair", "Glen", "High", "Long", "Maple", "Copper", "Crow", "Deer", "Fox",
		"Hart", "Lark", "Marsh", "Moss", "Owl", "Rock", "Sand", "Thorn", "Wheat", "Brook", "Hazel", "Cotton"
	};
	private static final String[] JOINED = {
		"brook", "field", "ford", "haven", "hill", "ridge", "ton", "ville", "wood", "dale", "bury", "worth", "stead", "mere", "well"
	};
	private static final String[] SPACED = {
		"Falls", "Creek", "Crossing", "Hollow", "Springs", "Point", "Junction", "Flats", "Bend", "Grove", "Mills", "Valley", "Gap", "Station"
	};
	private static final String[] SURVIVOR_GROUPS = {"the Lantern Collective", "the Hearth", "the Last Light", "the Riverside Union", "the Long Watch"};
	private static final String[] BANDIT_GROUPS = {"the Red Hands", "the Scrap Kings", "the Hollow Men", "the Tollmen", "the Jackals", "the Rust Saints"};

	public static TownIdentity of(long seed, TownSite site) {
		long hash = Hashing.of(seed, site.chunkX(), site.chunkZ(), 0x1DE7L);
		String name = name(hash);
		int population = population(site.size(), Hashing.mix(hash ^ 0x9091L));
		TownCondition condition = site.condition();
		String factionKey = "town.deadlands.faction." + condition.id();
		String factionArg = switch (condition) {
			case SURVIVOR -> SURVIVOR_GROUPS[(int) Math.floorMod(Hashing.mix(hash ^ 0x5011L), (long) SURVIVOR_GROUPS.length)];
			case BANDIT -> BANDIT_GROUPS[(int) Math.floorMod(Hashing.mix(hash ^ 0xBA4DL), (long) BANDIT_GROUPS.length)];
			default -> "";
		};
		String historyKey = "town.deadlands.history." + condition.id() + "." + Math.floorMod(Hashing.mix(hash ^ 0x4157L), (long) HISTORIES_PER_CONDITION);
		String storyKey = "town.deadlands.story." + Math.floorMod(Hashing.mix(hash ^ 0x5702L), (long) STORIES);
		return new TownIdentity(name, population, factionKey, factionArg, historyKey, lootKey(condition), storyKey);
	}

	/** How much is left to find, matching what {@link TownBuildingPiece} leaves in the containers. */
	public static String lootKey(TownCondition condition) {
		double loot = condition.lootMultiplier();
		String tier = loot >= 1.2 ? "untouched" : loot >= 1.0 ? "plenty" : loot >= 0.85 ? "picked_over" : "stripped";
		return "town.deadlands.loot." + tier;
	}

	private static String name(long hash) {
		String first = pick(FIRST, hash);
		int form = (int) Math.floorMod(Hashing.mix(hash ^ 0xF0F0L), 20L);
		if (form < 9) {
			return first + pick(JOINED, Hashing.mix(hash ^ 0x0101L));
		}
		if (form < 17) {
			String second = pick(SPACED, Hashing.mix(hash ^ 0x0202L));
			return first.equals(second) ? first + " Crossing" : first + " " + second;
		}
		String prefix = form == 17 ? "Fort " : form == 18 ? "New " : "Lake ";
		return prefix + first + (form == 19 ? "" : pick(JOINED, Hashing.mix(hash ^ 0x0303L)));
	}

	private static int population(TownSize size, long hash) {
		double roll = Hashing.unit(hash);
		return switch (size) {
			case SETTLEMENT -> 60 + 10 * (int) (roll * 43);
			case SMALL_TOWN -> 700 + 50 * (int) (roll * 83);
			case LARGE_TOWN -> 5200 + 100 * (int) (roll * 189);
			case CITY -> 24000 + 500 * (int) (roll * 150);
		};
	}

	private static String pick(String[] options, long hash) {
		return options[(int) Math.floorMod(hash, (long) options.length)];
	}
}
