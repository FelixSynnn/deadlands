package com.deadlands.story;

import com.deadlands.registry.DLStory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import org.jspecify.annotations.Nullable;

/**
 * The lore catalogue ({@link LoreEntries}, generated) and the rules for where entries turn up.
 *
 * <p>A container's loot table name says what kind of place it is (a clinic cabinet, a school
 * locker, a police locker...), and the site it stands in may add a story place (the Kessler
 * facility, Site Calder, an evacuation camp, Harley's expedition camp, a hospital). An entry can
 * turn up where any of its places match. Entries the survivor has not read are four times as
 * likely as ones they have.
 */
public final class Lore {
	private static final Map<String, LoreEntry> BY_ID = new HashMap<>();
	private static final Map<String, List<LoreEntry>> BY_SERIES = new HashMap<>();

	static {
		for (LoreEntry entry : LoreEntries.ALL) {
			BY_ID.put(entry.id(), entry);
			if (!entry.series().isEmpty()) {
				BY_SERIES.computeIfAbsent(entry.series(), s -> new ArrayList<>()).add(entry);
			}
		}
	}

	/** Words in a loot table's name and the kinds of place they mean. */
	private static final String[][] TABLE_PLACES = {
		{"house_", "house"}, {"kitchen", "house"}, {"fridge", "house"}, {"bedroom", "house"},
		{"school", "school"},
		{"clinic", "medical"}, {"pharmacy", "medical"}, {"medical", "medical"}, {"emergency", "medical"},
		{"police", "police"}, {"sheriff", "police"},
		{"store", "store"}, {"grocery", "store"}, {"diner", "store"}, {"gas_station", "store"}, {"gas_station", "gas_station"},
		{"office", "office"}, {"filing", "office"}, {"bank", "office"}, {"courthouse", "office"}, {"town_hall", "office"},
		{"military", "military"}, {"checkpoint", "military"}, {"bunker", "military"}, {"armory", "military"},
		{"research_lab", "lab"}, {"lab", "lab"},
		{"campsite", "camp"}, {"hunting", "camp"}, {"ranger", "camp"}, {"camp", "camp"},
		{"farm", "farm"}, {"barn", "farm"},
		{"utility", "utility"}, {"workshop", "utility"}, {"toolbox", "utility"}, {"radio", "utility"},
		{"subway", "subway"}, {"train", "subway"},
	};

	private Lore() {
	}

	public static @Nullable LoreEntry get(String id) {
		return BY_ID.get(id);
	}

	public static List<LoreEntry> series(String series) {
		return BY_SERIES.getOrDefault(series, List.of());
	}

	public static List<LoreEntry> all() {
		return LoreEntries.ALL;
	}

	public static String titleKey(String id) {
		return "lore.deadlands." + id + ".title";
	}

	public static String bodyKey(String id) {
		return "lore.deadlands." + id + ".body";
	}

	/** The item form of an entry, with the words a trail note needs. */
	public static ItemStack document(LoreEntry entry, List<String> args) {
		ItemStack stack = new ItemStack(DLStory.DOCUMENT);
		stack.set(DLStory.LORE, entry.id());
		if (!args.isEmpty()) {
			stack.set(DLStory.LORE_ARGS, List.copyOf(args));
		}
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(entry.form()), List.of()));
		return stack;
	}

	public static ItemStack document(String id) {
		LoreEntry entry = get(id);
		return entry == null ? ItemStack.EMPTY : document(entry, List.of());
	}

	/** The kinds of place a loot table belongs to. */
	public static Set<String> placesFor(String tablePath) {
		Set<String> places = new LinkedHashSet<>();
		for (String[] pair : TABLE_PLACES) {
			if (tablePath.contains(pair[0])) {
				places.add(pair[1]);
			}
		}
		places.add("any");
		return places;
	}

	/** Pick an entry for a container in these places, preferring what this survivor has not read; null for none. */
	public static @Nullable LoreEntry pick(Set<String> places, @Nullable ServerPlayer reader, RandomSource random) {
		Set<String> read = reader == null ? Set.of() : Archive.discovered(reader).keySet();
		List<LoreEntry> pool = new ArrayList<>();
		List<Integer> weights = new ArrayList<>();
		int total = 0;
		for (LoreEntry entry : LoreEntries.ALL) {
			boolean fits = false;
			for (String place : entry.places()) {
				if (places.contains(place)) {
					fits = true;
					break;
				}
			}
			if (!fits) {
				continue;
			}
			// Story places are rare; "any" entries only fill in where nothing more fitting exists.
			int weight = (read.contains(entry.id()) ? 1 : 4) * (entry.places().contains("any") ? 1 : 3);
			pool.add(entry);
			weights.add(weight);
			total += weight;
		}
		if (total == 0) {
			return null;
		}
		int roll = random.nextInt(total);
		for (int i = 0; i < pool.size(); i++) {
			roll -= weights.get(i);
			if (roll < 0) {
				return pool.get(i);
			}
		}
		return null;
	}
}
