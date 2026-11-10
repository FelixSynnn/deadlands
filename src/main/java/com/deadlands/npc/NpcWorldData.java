package com.deadlands.npc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * World-level survivor state ({@code <world>/data/deadlands_npcs.dat}): which recurring characters
 * have been placed, and where, so the others can point the way to them.
 *
 * <p>Each character appears once per world, ever: once placed they are never placed again by the
 * world (encounters, the story). The record also keeps which entity is the character, so any other
 * copy (one placed by hand earlier, or from an older version) is removed when it first ticks, and
 * whether the character was lost ({@code /kill} or the void, the only things that can hurt them);
 * only then can {@code /deadlands npc <name>} put them back. Both fields are optional, so worlds
 * saved before them load as they are.
 */
public final class NpcWorldData extends SavedData {
	public static final Codec<NpcWorldData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.listOf().fieldOf("placed").forGetter(data -> List.copyOf(data.placed)),
		Codec.unboundedMap(Codec.STRING, BlockPos.CODEC).optionalFieldOf("positions", Map.of()).forGetter(data -> Map.copyOf(data.positions)),
		Codec.unboundedMap(Codec.STRING, UUIDUtil.STRING_CODEC).optionalFieldOf("uuids", Map.of()).forGetter(data -> Map.copyOf(data.uuids)),
		Codec.STRING.listOf().optionalFieldOf("lost", List.of()).forGetter(data -> List.copyOf(data.lost)),
		Codec.INT.optionalFieldOf("explored", 0).forGetter(data -> data.explored)
	).apply(instance, NpcWorldData::new));
	public static final SavedDataType<NpcWorldData> TYPE = new SavedDataType<>("deadlands_npcs", NpcWorldData::new, CODEC, null);

	private final Set<String> placed = new HashSet<>();
	private final Map<String, BlockPos> positions = new HashMap<>();
	/** The entity that is each placed character. */
	private final Map<String, UUID> uuids = new HashMap<>();
	/** Characters killed by {@code /kill} or the void. */
	private final Set<String> lost = new HashSet<>();
	/** Exploring checks since a character was last placed (the encounter chance rises with it). */
	private int explored;

	public NpcWorldData() {
	}

	private NpcWorldData(List<String> saved, Map<String, BlockPos> where, Map<String, UUID> entities, List<String> gone, int exploring) {
		placed.addAll(saved);
		positions.putAll(where);
		uuids.putAll(entities);
		lost.addAll(gone);
		explored = exploring;
	}

	public static NpcWorldData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public boolean isPlaced(NpcCharacter character) {
		return placed.contains(character.id());
	}

	/** Where the character was placed, or null if they have not been yet. */
	public @Nullable BlockPos position(NpcCharacter character) {
		return positions.get(character.id());
	}

	public void markPlaced(NpcCharacter character, BlockPos pos) {
		placed.add(character.id());
		positions.put(character.id(), pos.immutable());
		lost.remove(character.id());
		explored = 0;
		setDirty();
	}

	/** Count one more exploring check without meeting anyone; returns how many there have been. */
	public int countExploring() {
		explored++;
		setDirty();
		return explored;
	}

	/** Whether the character was killed ({@code /kill}, the void) and may be placed again by hand. */
	public boolean isLost(NpcCharacter character) {
		return lost.contains(character.id());
	}

	/** Record {@code uuid} as the entity that is this character (any other copy is then removed). */
	public void own(NpcCharacter character, UUID uuid) {
		uuids.put(character.id(), uuid);
		setDirty();
	}

	/**
	 * Whether this entity is the character: true for the recorded one, and for the first one seen in a
	 * world saved before entities were recorded; false for any other copy.
	 */
	public boolean adopt(NpcCharacter character, UUID uuid) {
		UUID known = uuids.get(character.id());
		if (known == null) {
			own(character, uuid);
			return true;
		}
		return known.equals(uuid);
	}

	/** The character died: forget their entity and allow them to be placed again by hand. */
	public void markLost(NpcCharacter character, UUID uuid) {
		if (uuid.equals(uuids.get(character.id()))) {
			uuids.remove(character.id());
			lost.add(character.id());
			setDirty();
		}
	}

	/** Decide where a character will be, without placing them yet (they appear when a survivor comes near). */
	public void plan(NpcCharacter character, BlockPos pos) {
		if (!placed.contains(character.id())) {
			positions.put(character.id(), pos.immutable());
			setDirty();
		}
	}

	/** A character nobody has placed yet, chosen at random; null if all have been placed. */
	public @Nullable NpcCharacter nextUnplaced(RandomSource random) {
		List<NpcCharacter> free = new ArrayList<>();
		for (NpcCharacter character : NpcCharacter.values()) {
			if (!isPlaced(character)) {
				free.add(character);
			}
		}
		return free.isEmpty() ? null : free.get(random.nextInt(free.size()));
	}
}
