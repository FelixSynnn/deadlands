package com.deadlands.story;

import com.deadlands.npc.NpcCharacter;
import com.deadlands.npc.NpcWorldData;
import com.deadlands.registry.DLStory;
import com.deadlands.world.town.TownIdentity;
import com.deadlands.world.town.TownLookup;
import com.deadlands.world.town.TownSite;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What the story adds to a container the first time it is opened, on top of its loot:
 *
 * <ul>
 *   <li>sometimes a lore entry that fits the place ({@link Lore#pick});</li>
 *   <li>a trail note from a recurring character nobody has met yet, naming where they went;</li>
 *   <li>the quest items each story place holds while the story still needs them: samples and
 *       records in hospitals, research documents, the formula and Patient Zero's records at the
 *       Kessler facility, the military's research and the helicopter's parts at Site Calder, the
 *       evacuation orders at an evacuation camp, Harley's field case at her expedition camp;</li>
 *   <li>radio parts in workshops and utility buildings.</li>
 * </ul>
 */
public final class StoryLoot {
	private static final float LORE_CHANCE = 0.2F;
	private static final float TRAIL_CHANCE = 0.12F;

	private StoryLoot() {
	}

	public static List<ItemStack> extras(ServerLevel level, BlockPos pos, String tablePath, @Nullable ServerPlayer player, RandomSource random) {
		List<ItemStack> out = new ArrayList<>();
		StoryState story = StoryState.get(level.getServer());
		StoryPlaces.Place place = StoryPlaces.at(level, pos);
		Set<String> places = new java.util.HashSet<>(Lore.placesFor(tablePath));
		if (place != null) {
			places.add(switch (place) {
				case HOSPITAL -> "hospital";
				case RESEARCH_FACILITY -> "facility";
				case EVACUATION_SITE -> "evacuation";
				case EXPEDITION_CAMP -> "expedition";
				case MILITARY_FACILITY -> "milfacility";
			});
		}
		float chance = LORE_CHANCE * (place != null ? 2.5F : 1.0F) * (tablePath.contains("filing") || tablePath.contains("office") ? 1.5F : 1.0F);
		if (random.nextFloat() < chance) {
			LoreEntry entry = Lore.pick(places, player, random);
			if (entry != null) {
				out.add(Lore.document(entry, List.of()));
			}
		}
		ItemStack trail = trailNote(level, pos, story, random);
		if (!trail.isEmpty()) {
			out.add(trail);
		}
		if (place != null) {
			questItems(place, tablePath, story, player, random, out);
		}
		if ((places.contains("utility") || tablePath.contains("electr")) && random.nextFloat() < 0.25F) {
			out.add(new ItemStack(DLStory.RADIO_PARTS));
		}
		return out;
	}

	private static void questItems(StoryPlaces.Place place, String table, StoryState story, @Nullable ServerPlayer player, RandomSource random,
		List<ItemStack> out) {
		switch (place) {
			case HOSPITAL -> {
				need(DLStory.MEDICAL_SAMPLES, 0.45F, story, player, random, out);
				need(DLStory.HOSPITAL_RECORDS, 0.35F, story, player, random, out);
			}
			case RESEARCH_FACILITY -> {
				need(DLStory.RESEARCH_DOCUMENTS, 0.45F, story, player, random, out);
				need(DLStory.EXPERIMENTAL_FORMULA, table.contains("lab") ? 0.35F : 0.1F, story, player, random, out);
				need(DLStory.PATIENT_ZERO_RECORDS, table.contains("filing") || table.contains("lab") ? 0.25F : 0.05F, story, player, random, out);
			}
			case MILITARY_FACILITY -> {
				need(DLStory.MILITARY_RESEARCH, 0.4F, story, player, random, out);
				for (Item part : DLStory.helicopterParts()) {
					if (!story.heliParts().contains(DLStory.idOf(part))) {
						need(part, 0.3F, story, player, random, out);
					}
				}
			}
			case EVACUATION_SITE -> need(DLStory.EVACUATION_ORDERS, 0.5F, story, player, random, out);
			case EXPEDITION_CAMP -> need(DLStory.FIELD_CASE, 0.7F, story, player, random, out);
		}
	}

	/** Add a quest item if the story still needs it and the opener is not already carrying one. */
	private static void need(Item item, float chance, StoryState story, @Nullable ServerPlayer player, RandomSource random, List<ItemStack> out) {
		if (story.delivered(DLStory.idOf(item)) || random.nextFloat() >= chance) {
			return;
		}
		if (player != null && player.getInventory().contains(new ItemStack(item))) {
			return;
		}
		out.add(new ItemStack(item));
	}

	/** A note from a character who has been placed (or is about to be) but whom nobody has met, saying where they went. */
	private static ItemStack trailNote(ServerLevel level, BlockPos pos, StoryState story, RandomSource random) {
		if (random.nextFloat() >= TRAIL_CHANCE || !story.has(StoryState.MET + "harley")) {
			return ItemStack.EMPTY;
		}
		NpcWorldData characters = NpcWorldData.get(level.getServer());
		List<NpcCharacter> candidates = new ArrayList<>();
		for (NpcCharacter character : NpcCharacter.values()) {
			if (character != NpcCharacter.HARLEY && characters.position(character) != null && !story.has(StoryState.MET + character.id())) {
				candidates.add(character);
			}
		}
		if (candidates.isEmpty()) {
			return ItemStack.EMPTY;
		}
		NpcCharacter who = candidates.get(random.nextInt(candidates.size()));
		BlockPos target = characters.position(who);
		LoreEntry entry = Lore.get("trail_" + who.id());
		if (entry == null || target == null) {
			return ItemStack.EMPTY;
		}
		return Lore.document(entry, Directions.words(level, pos, target));
	}

	/** Town name, compass direction and rounded distance from one place to another, as plain words for a note. */
	public static final class Directions {
		private static final String[] COMPASS = {"south", "southwest", "west", "northwest", "north", "northeast", "east", "southeast"};

		private Directions() {
		}

		public static List<String> words(ServerLevel level, BlockPos from, BlockPos to) {
			TownSite town = TownLookup.nearest(level, to, 220.0).orElse(null);
			String name = town == null ? "the open country" : TownIdentity.of(level.getSeed(), town).name();
			return List.of(name, compass(from, to), Integer.toString(distance(from, to)));
		}

		public static String compass(BlockPos from, BlockPos to) {
			double angle = Math.atan2(-(to.getX() - from.getX()), to.getZ() - from.getZ());
			return COMPASS[Math.floorMod((int) Math.round(angle / (Math.PI / 4.0)), 8)];
		}

		public static int distance(BlockPos from, BlockPos to) {
			return Math.max(50, (int) Math.round(Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ()) / 50.0) * 50);
		}
	}
}
