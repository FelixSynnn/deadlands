package com.deadlands.story;

import com.deadlands.progression.BossKind;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * The story's progress in this world ({@code <world>/data/deadlands_story.dat}), shared by every
 * survivor: a co-operative story. Flags (who has been met, which places investigated, which quests
 * finished), everything anyone has read, what has been delivered to Harley, the radio towers
 * repaired, where the story places are, the bosses defeated, the helicopter's fitted parts, when
 * the cure will be ready and the ending reached.
 */
public final class StoryState extends SavedData {
	public static final String MET = "met_";
	public static final String INVESTIGATED = "investigated_";
	public static final String JOINED = "joined_";
	public static final String MISSING_DONE = "missing_research_done";
	public static final String CURE_STARTED = "cure_started";
	public static final String CURE_DONE = "cure_done";
	public static final String CALDER_KNOWN = "calder_known";
	public static final String MILITARY_KNEW = "military_knew";
	public static final String ITZ_DONE = "quest_itzbreadloaf_done";
	public static final String LILY_DONE = "quest_imlilylol_done";
	public static final String FELIX_DONE = "quest_felixsyn_done";
	public static final String BOSS = "boss_";

	private static final Codec<Set<String>> SET = Codec.STRING.listOf().xmap(HashSet::new, List::copyOf);
	public static final Codec<StoryState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		SET.optionalFieldOf("flags", Set.of()).forGetter(s -> s.flags),
		SET.optionalFieldOf("discovered", Set.of()).forGetter(s -> s.discovered),
		SET.optionalFieldOf("delivered", Set.of()).forGetter(s -> s.delivered),
		BlockPos.CODEC.listOf().optionalFieldOf("towers", List.of()).forGetter(s -> List.copyOf(s.towers)),
		Codec.unboundedMap(Codec.STRING, BlockPos.CODEC).optionalFieldOf("places", Map.of()).forGetter(s -> Map.copyOf(s.places)),
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("bosses", Map.of()).forGetter(s -> Map.copyOf(s.bosses)),
		Codec.LONG.optionalFieldOf("cure_ready_day", 0L).forGetter(s -> s.cureReadyDay),
		Codec.STRING.optionalFieldOf("ending", "").forGetter(s -> s.ending),
		SET.optionalFieldOf("heli_parts", Set.of()).forGetter(s -> s.heliParts),
		Codec.LONG.optionalFieldOf("harley_met_day", 0L).forGetter(s -> s.harleyMetDay)
	).apply(instance, StoryState::new));
	public static final SavedDataType<StoryState> TYPE = new SavedDataType<>("deadlands_story", StoryState::new, CODEC, null);

	/** Reading one of these tells the story something. */
	private static final Map<String, String> FLAG_ON_READ = Map.of(
		"mil_calder_manifest", CALDER_KNOWN, "lily_letter_4", CALDER_KNOWN, "radio_pilot", CALDER_KNOWN,
		"mil_knew", MILITARY_KNEW, "mil_gate_order", MILITARY_KNEW, "gov_governor", MILITARY_KNEW);

	private final Set<String> flags = new HashSet<>();
	private final Set<String> discovered = new HashSet<>();
	private final Set<String> delivered = new HashSet<>();
	private final List<BlockPos> towers = new ArrayList<>();
	private final Map<String, BlockPos> places = new HashMap<>();
	private final Map<String, Integer> bosses = new HashMap<>();
	private long cureReadyDay;
	private String ending = "";
	private final Set<String> heliParts = new HashSet<>();
	private long harleyMetDay;

	public StoryState() {
	}

	private StoryState(Set<String> flags, Set<String> discovered, Set<String> delivered, List<BlockPos> towers, Map<String, BlockPos> places,
		Map<String, Integer> bosses, long cureReadyDay, String ending, Set<String> heliParts, long harleyMetDay) {
		this.flags.addAll(flags);
		this.discovered.addAll(discovered);
		this.delivered.addAll(delivered);
		this.towers.addAll(towers);
		this.places.putAll(places);
		this.bosses.putAll(bosses);
		this.cureReadyDay = cureReadyDay;
		this.ending = ending;
		this.heliParts.addAll(heliParts);
		this.harleyMetDay = harleyMetDay;
	}

	public static StoryState get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	// ---------------------------------------------------------------------------------------- flags

	public boolean has(String flag) {
		return flags.contains(flag);
	}

	/** Set a flag; true if it was not set before. */
	public boolean set(String flag) {
		if (flags.add(flag)) {
			setDirty();
			return true;
		}
		return false;
	}

	public void clear(String flag) {
		if (flags.remove(flag)) {
			setDirty();
		}
	}

	public Set<String> flags() {
		return Set.copyOf(flags);
	}

	// ---------------------------------------------------------------------------------------- reading

	/** Someone read an entry: remember it for the world and act on what it reveals. */
	public void discover(ServerPlayer reader, String id) {
		if (!discovered.add(id)) {
			return;
		}
		setDirty();
		String flag = FLAG_ON_READ.get(id);
		if (flag != null && set(flag) && flag.equals(CALDER_KNOWN)) {
			reader.displayClientMessage(Component.translatable("message.deadlands.story.calder_known"), false);
		}
	}

	public boolean read(String id) {
		return discovered.contains(id);
	}

	/** How many parts of a series anyone in this world has read. */
	public int seriesRead(String series) {
		int count = 0;
		for (LoreEntry entry : Lore.series(series)) {
			if (discovered.contains(entry.id())) {
				count++;
			}
		}
		return count;
	}

	// ---------------------------------------------------------------------------------------- deliveries

	public boolean delivered(String itemId) {
		return delivered.contains(itemId);
	}

	public void deliver(String itemId) {
		if (delivered.add(itemId)) {
			setDirty();
		}
	}

	// ---------------------------------------------------------------------------------------- towers, places

	public List<BlockPos> towers() {
		return List.copyOf(towers);
	}

	/** A radio tower's transmitter repaired; true if it was not counted already. */
	public boolean addTower(BlockPos pos) {
		if (towers.contains(pos)) {
			return false;
		}
		towers.add(pos.immutable());
		setDirty();
		return true;
	}

	public @Nullable BlockPos place(StoryPlaces.Place place) {
		return places.get(place.id());
	}

	public boolean placeLooked(StoryPlaces.Place place) {
		return places.containsKey(place.id()) || flags.contains("notfound_" + place.id());
	}

	public void setPlace(StoryPlaces.Place place, @Nullable BlockPos pos) {
		if (pos == null) {
			set("notfound_" + place.id());
		} else {
			places.put(place.id(), pos.immutable());
			setDirty();
		}
	}

	// ---------------------------------------------------------------------------------------- bosses

	public int bossDefeats(BossKind kind) {
		return bosses.getOrDefault(kind.id(), 0);
	}

	public void bossDefeated(BossKind kind) {
		bosses.merge(kind.id(), 1, Integer::sum);
		flags.add(BOSS + kind.id());
		setDirty();
	}

	// ---------------------------------------------------------------------------------------- cure, helicopter, ending

	public long cureReadyDay() {
		return cureReadyDay;
	}

	public void setCureReadyDay(long day) {
		this.cureReadyDay = day;
		setDirty();
	}

	public Set<String> heliParts() {
		return Set.copyOf(heliParts);
	}

	public boolean fitHeliPart(String itemId) {
		if (heliParts.add(itemId)) {
			setDirty();
			return true;
		}
		return false;
	}

	public boolean helicopterReady() {
		return heliParts.size() >= 4;
	}

	public String ending() {
		return ending;
	}

	public void setEnding(String ending) {
		this.ending = ending;
		setDirty();
	}

	public long harleyMetDay() {
		return harleyMetDay;
	}

	public void setHarleyMetDay(long day) {
		this.harleyMetDay = day;
		setDirty();
	}
}
