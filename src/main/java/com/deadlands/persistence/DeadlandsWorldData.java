package com.deadlands.persistence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * World-wide DEADLANDS state, saved in {@code <world>/data/deadlands_world.dat}.
 *
 * <p>Holds the world clock's progress, a record for every survivor who has joined, the world
 * chronicle, the towns survivors have found, the current horde night and the migrating groups of
 * infected. Later milestones add fields here with {@code optionalFieldOf} defaults so older saves
 * keep loading. Always access it through {@link #get(MinecraftServer)} on the server thread.
 */
public final class DeadlandsWorldData extends SavedData {
	public static final int SCHEMA_VERSION = 1;

	public static final Codec<DeadlandsWorldData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.INT.optionalFieldOf("schema", SCHEMA_VERSION).forGetter(d -> SCHEMA_VERSION),
		Codec.LONG.optionalFieldOf("last_processed_day", -1L).forGetter(d -> d.lastProcessedDay),
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, PlayerRecord.CODEC).optionalFieldOf("players", Map.of()).forGetter(d -> d.players),
		ChronicleEntry.CODEC.listOf().optionalFieldOf("chronicle", List.of()).forGetter(d -> d.chronicle),
		Codec.unboundedMap(Codec.STRING, TownRecord.CODEC).optionalFieldOf("towns", Map.of()).forGetter(d -> d.towns),
		HordeRecord.CODEC.optionalFieldOf("horde").forGetter(d -> Optional.ofNullable(d.horde)),
		MigrationRecord.CODEC.listOf().optionalFieldOf("migrations", List.of()).forGetter(d -> d.migrations),
		Codec.INT.optionalFieldOf("next_migration_id", 1).forGetter(d -> d.nextMigrationId),
		Codec.LONG.listOf().optionalFieldOf("powered_towns", List.of()).forGetter(d -> List.copyOf(d.poweredTowns))
	).apply(instance, DeadlandsWorldData::new));

	/** Fabric API makes a null data-fixer type safe: our data is versioned by {@link #SCHEMA_VERSION}. */
	public static final SavedDataType<DeadlandsWorldData> TYPE = new SavedDataType<>("deadlands_world", DeadlandsWorldData::new, CODEC, null);

	private long lastProcessedDay;
	private final Map<UUID, PlayerRecord> players;
	private final List<ChronicleEntry> chronicle;
	/** Discovered towns by key (the town's start chunk, {@code ChunkPos.asLong}, as a decimal string). */
	private final Map<String, TownRecord> towns;
	/** The current or most recent horde night (Milestone 4). */
	private @Nullable HordeRecord horde;
	/** Migrating groups of infected (Milestone 4). */
	private final List<MigrationRecord> migrations;
	private int nextMigrationId;
	/** Towns whose power was restored (town keys, Milestone 3). */
	private final java.util.Set<Long> poweredTowns;

	public DeadlandsWorldData() {
		this(SCHEMA_VERSION, -1L, Map.of(), List.of(), Map.of(), Optional.empty(), List.of(), 1, List.of());
	}

	private DeadlandsWorldData(int schema, long lastProcessedDay, Map<UUID, PlayerRecord> players, List<ChronicleEntry> chronicle,
		Map<String, TownRecord> towns, Optional<HordeRecord> horde, List<MigrationRecord> migrations, int nextMigrationId,
		List<Long> poweredTowns) {
		this.lastProcessedDay = lastProcessedDay;
		this.players = new HashMap<>(players);
		this.chronicle = new ArrayList<>(chronicle);
		this.towns = new HashMap<>(towns);
		this.horde = horde.orElse(null);
		this.migrations = new ArrayList<>(migrations);
		this.nextMigrationId = Math.max(1, nextMigrationId);
		this.poweredTowns = new java.util.LinkedHashSet<>(poweredTowns);
	}

	public static DeadlandsWorldData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	// ---------------------------------------------------------------------------- world clock

	/** 0-based day index the world clock last processed, or -1 before the first tick. */
	public long lastProcessedDay() {
		return lastProcessedDay;
	}

	public void setLastProcessedDay(long dayIndex) {
		if (this.lastProcessedDay != dayIndex) {
			this.lastProcessedDay = dayIndex;
			setDirty();
		}
	}

	// ---------------------------------------------------------------------------- survivors

	public boolean hasPlayer(UUID id) {
		return players.containsKey(id);
	}

	public Optional<PlayerRecord> player(UUID id) {
		return Optional.ofNullable(players.get(id));
	}

	public PlayerRecord getOrCreatePlayer(UUID id, String name, long dayIndex) {
		PlayerRecord record = players.computeIfAbsent(id, unused -> PlayerRecord.arriving(name, dayIndex));
		setDirty();
		return record;
	}

	public Map<UUID, PlayerRecord> players() {
		return Collections.unmodifiableMap(players);
	}

	// ---------------------------------------------------------------------------- chronicle

	public void addChronicle(ChronicleEntry entry, int limit) {
		chronicle.add(entry);
		while (chronicle.size() > Math.max(1, limit)) {
			chronicle.removeFirst();
		}
		setDirty();
	}

	/** Most recent entries last. */
	public List<ChronicleEntry> chronicle() {
		return Collections.unmodifiableList(chronicle);
	}

	public List<ChronicleEntry> recentChronicle(int count) {
		int from = Math.max(0, chronicle.size() - count);
		return List.copyOf(chronicle.subList(from, chronicle.size()));
	}

	// ---------------------------------------------------------------------------- towns

	public Optional<TownRecord> town(long key) {
		return Optional.ofNullable(towns.get(Long.toString(key)));
	}

	/** Records the first visit to a town. Returns false if it was already known. */
	public boolean discoverTown(long key, TownRecord record) {
		if (towns.putIfAbsent(Long.toString(key), record) != null) {
			return false;
		}
		setDirty();
		return true;
	}

	public Map<String, TownRecord> towns() {
		return Collections.unmodifiableMap(towns);
	}

	// ---------------------------------------------------------------------------- power

	public boolean isTownPowered(long townKey) {
		return poweredTowns.contains(townKey);
	}

	/** Marks a town as powered. Returns false if it already was. */
	public boolean powerTown(long townKey) {
		if (!poweredTowns.add(townKey)) {
			return false;
		}
		setDirty();
		return true;
	}

	/** A town loses its power (an outage). Returns false if it had none. */
	public boolean unpowerTown(long townKey) {
		if (!poweredTowns.remove(townKey)) {
			return false;
		}
		setDirty();
		return true;
	}

	public int poweredTownCount() {
		return poweredTowns.size();
	}

	// ---------------------------------------------------------------------------- hordes

	public Optional<HordeRecord> horde() {
		return Optional.ofNullable(horde);
	}

	public void setHorde(@Nullable HordeRecord record) {
		if (!java.util.Objects.equals(this.horde, record)) {
			this.horde = record;
			setDirty();
		}
	}

	// ---------------------------------------------------------------------------- migrations

	public List<MigrationRecord> migrations() {
		return Collections.unmodifiableList(migrations);
	}

	public Optional<MigrationRecord> migration(int id) {
		for (MigrationRecord record : migrations) {
			if (record.id() == id) {
				return Optional.of(record);
			}
		}
		return Optional.empty();
	}

	/** Adds the record, or replaces the one with the same id. */
	public void putMigration(MigrationRecord record) {
		for (int i = 0; i < migrations.size(); i++) {
			if (migrations.get(i).id() == record.id()) {
				if (!migrations.get(i).equals(record)) {
					migrations.set(i, record);
					setDirty();
				}
				return;
			}
		}
		migrations.add(record);
		setDirty();
	}

	public void removeMigration(int id) {
		if (migrations.removeIf(record -> record.id() == id)) {
			setDirty();
		}
	}

	public int nextMigrationId() {
		int id = nextMigrationId;
		nextMigrationId = id == Integer.MAX_VALUE ? 1 : id + 1;
		setDirty();
		return id;
	}

	// ---------------------------------------------------------------------------- testing

	@Override
	public boolean equals(Object o) {
		return o instanceof DeadlandsWorldData d && d.lastProcessedDay == lastProcessedDay && d.players.equals(players) && d.chronicle.equals(chronicle)
			&& d.towns.equals(towns) && java.util.Objects.equals(d.horde, horde) && d.migrations.equals(migrations) && d.nextMigrationId == nextMigrationId
			&& d.poweredTowns.equals(poweredTowns);
	}

	@Override
	public int hashCode() {
		return java.util.Objects.hash(lastProcessedDay, players, chronicle, towns, horde, migrations, nextMigrationId, poweredTowns);
	}
}
