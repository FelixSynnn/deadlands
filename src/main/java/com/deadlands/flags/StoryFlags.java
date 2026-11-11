package com.deadlands.flags;

import com.deadlands.Deadlands;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Story flags and counters (Phase 2): the persistent facts missions, dialogue, scares and endings
 * test. World flags ({@code <world>/data/deadlands_flags.dat}) are shared by everyone (the story is
 * co-operative, host-controlled); player flags (an attachment, kept through death) are one
 * survivor's own, such as which achievements' counters they have reached.
 *
 * <p>A flag is a plain string such as {@code harley_met} or {@code family_house_seen}; a counter is
 * a named number such as {@code scares_survived}. Conditions in data files list flags; a flag
 * prefixed with {@code player:} is looked up on the survivor instead of the world.
 */
public final class StoryFlags extends SavedData {
	public static final Codec<StoryFlags> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.listOf().optionalFieldOf("flags", List.of()).forGetter(data -> List.copyOf(new TreeSet<>(data.flags))),
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("counters", Map.of()).forGetter(data -> Map.copyOf(data.counters))
	).apply(instance, StoryFlags::new));
	public static final SavedDataType<StoryFlags> TYPE = new SavedDataType<>("deadlands_flags", StoryFlags::new, CODEC, null);

	/** A survivor's own flags and counters. */
	public record PlayerData(List<String> flags, Map<String, Integer> counters) {
		public static final Codec<PlayerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.listOf().optionalFieldOf("flags", List.of()).forGetter(PlayerData::flags),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("counters", Map.of()).forGetter(PlayerData::counters)
		).apply(instance, PlayerData::new));
		public static final PlayerData EMPTY = new PlayerData(List.of(), Map.of());
	}

	public static final AttachmentType<PlayerData> PLAYER = AttachmentRegistry.create(Deadlands.id("player_flags"),
		builder -> builder.persistent(PlayerData.CODEC).initializer(() -> PlayerData.EMPTY).copyOnDeath());

	private final Set<String> flags = new HashSet<>();
	private final Map<String, Integer> counters = new HashMap<>();

	public StoryFlags() {
	}

	private StoryFlags(List<String> saved, Map<String, Integer> numbers) {
		flags.addAll(saved);
		counters.putAll(numbers);
	}

	public static StoryFlags get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public boolean has(String flag) {
		return flags.contains(flag);
	}

	/** Set a flag; true if it was not set before. */
	public boolean set(String flag) {
		boolean added = flags.add(flag);
		if (added) {
			setDirty();
		}
		return added;
	}

	public boolean clear(String flag) {
		boolean removed = flags.remove(flag);
		if (removed) {
			setDirty();
		}
		return removed;
	}

	public Set<String> all() {
		return Set.copyOf(flags);
	}

	public int counter(String name) {
		return counters.getOrDefault(name, 0);
	}

	public int add(String name, int amount) {
		int value = counter(name) + amount;
		counters.put(name, value);
		setDirty();
		return value;
	}

	// ------------------------------------------------------------------------------ player flags

	public static boolean has(ServerPlayer player, String flag) {
		return player.getAttachedOrCreate(PLAYER).flags().contains(flag);
	}

	public static boolean set(ServerPlayer player, String flag) {
		PlayerData data = player.getAttachedOrCreate(PLAYER);
		if (data.flags().contains(flag)) {
			return false;
		}
		Set<String> next = new TreeSet<>(data.flags());
		next.add(flag);
		player.setAttached(PLAYER, new PlayerData(List.copyOf(next), data.counters()));
		return true;
	}

	public static int counter(ServerPlayer player, String name) {
		return player.getAttachedOrCreate(PLAYER).counters().getOrDefault(name, 0);
	}

	public static int add(ServerPlayer player, String name, int amount) {
		PlayerData data = player.getAttachedOrCreate(PLAYER);
		Map<String, Integer> next = new HashMap<>(data.counters());
		int value = next.getOrDefault(name, 0) + amount;
		next.put(name, value);
		player.setAttached(PLAYER, new PlayerData(data.flags(), Map.copyOf(next)));
		return value;
	}

	/** True if every flag in the list holds ({@code player:} flags are checked on the survivor; {@code !} negates). */
	public static boolean holds(MinecraftServer server, ServerPlayer player, List<String> conditions) {
		StoryFlags world = get(server);
		for (String condition : conditions) {
			boolean negate = condition.startsWith("!");
			String flag = negate ? condition.substring(1) : condition;
			boolean value = flag.startsWith("player:") ? player != null && has(player, flag.substring(7)) : world.has(flag);
			if (value == negate) {
				return false;
			}
		}
		return true;
	}
}
