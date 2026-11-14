package com.deadlands.mission;

import com.deadlands.Deadlands;
import com.deadlands.content.DLContent;
import com.deadlands.flags.StoryFlags;
import com.deadlands.horror.HorrorDirector;
import com.deadlands.network.MissionSyncPayload;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Missions (Phase 2): data-driven ({@link MissionDef}, {@link GiverDef}), shared by everyone in the
 * world and controlled by the host (the story is co-operative), saved in
 * {@code <world>/data/deadlands_missions.dat} so progress survives restarts and disconnects.
 *
 * <ul>
 *   <li>Objectives are checked once a second; a mission completes when all of them hold.</li>
 *   <li>Rewards go to each survivor exactly once: those offline at the time get theirs when they
 *       next join, and nobody gets them twice.</li>
 *   <li>A giver's next offer waits a random 10 to 15 minutes (their file says) of active play after
 *       their last mission is completed, declined or abandoned; one active mission per giver.</li>
 *   <li>A survivor's Missions tab appears once they have received a mission.</li>
 * </ul>
 */
public final class MissionManager {
	public static final AttachmentType<Boolean> RECEIVED = AttachmentRegistry.create(Deadlands.id("missions_received"),
		builder -> builder.persistent(Codec.BOOL).initializer(() -> false).copyOnDeath());
	private static final RandomSource RANDOM = RandomSource.create();

	private MissionManager() {
	}

	/** The world's missions: progress of active ones, finished ones, givers' cooldowns, who was rewarded. */
	public static final class State extends SavedData {
		public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.INT)).optionalFieldOf("active", Map.of()).forGetter(s -> Map.copyOf(s.active)),
			Codec.STRING.listOf().optionalFieldOf("completed", List.of()).forGetter(s -> List.copyOf(s.completed)),
			Codec.STRING.listOf().optionalFieldOf("failed", List.of()).forGetter(s -> List.copyOf(s.failed)),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("cooldowns", Map.of()).forGetter(s -> Map.copyOf(s.cooldowns)),
			Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()).optionalFieldOf("rewarded", Map.of()).forGetter(s -> Map.copyOf(s.rewarded))
		).apply(instance, State::new));
		public static final SavedDataType<State> TYPE = new SavedDataType<>("deadlands_missions", State::new, CODEC, null);

		final Map<String, Map<String, Integer>> active = new LinkedHashMap<>();
		final Set<String> completed = new LinkedHashSet<>();
		final Set<String> failed = new LinkedHashSet<>();
		final Map<String, Integer> cooldowns = new HashMap<>();
		final Map<String, List<String>> rewarded = new HashMap<>();

		public State() {
		}

		private State(Map<String, Map<String, Integer>> active, List<String> completed, List<String> failed, Map<String, Integer> cooldowns,
			Map<String, List<String>> rewarded) {
			active.forEach((id, progress) -> this.active.put(id, new HashMap<>(progress)));
			this.completed.addAll(completed);
			this.failed.addAll(failed);
			this.cooldowns.putAll(cooldowns);
			rewarded.forEach((id, players) -> this.rewarded.put(id, new ArrayList<>(players)));
		}
	}

	public static State state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(State.TYPE);
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 13) {
				tick(server);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			rewardPending(server, handler.getPlayer());
			sync(server, handler.getPlayer());
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer killer) {
				counted(killer.level().getServer(), "kill", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
			}
		});
	}

	// ------------------------------------------------------------------------------ the host's controls

	/** Start a mission for everyone; false if unknown, already active or done, or its requirements do not hold. */
	public static boolean start(MinecraftServer server, Identifier id, ServerPlayer by) {
		MissionDef def = DLContent.MISSIONS.get(id).orElse(null);
		State state = state(server);
		String key = id.toString();
		if (def == null || state.active.containsKey(key) || state.completed.contains(key) || !StoryFlags.holds(server, by, def.requires())) {
			return false;
		}
		state.active.put(key, new HashMap<>());
		state.failed.remove(key);
		state.setDirty();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.setAttached(RECEIVED, true);
			player.displayClientMessage(Component.translatable("mission.deadlands.received", Component.translatable(def.title())), false);
		}
		syncAll(server);
		return true;
	}

	/** Whether this mission is under way. */
	/** Whether an active mission's objective has been met (its saved progress reached its count). */
	public static boolean objectiveDone(MinecraftServer server, Identifier id, String objectiveId) {
		Map<String, Integer> progress = state(server).active.get(id.toString());
		MissionDef def = DLContent.MISSIONS.get(id).orElse(null);
		if (progress == null || def == null) {
			return false;
		}
		for (MissionDef.Objective objective : def.objectives()) {
			if (objective.id().equals(objectiveId)) {
				return progress.getOrDefault(objectiveId, 0) >= Math.max(1, objective.count());
			}
		}
		return false;
	}

	public static boolean isActive(MinecraftServer server, Identifier id) {
		return state(server).active.containsKey(id.toString());
	}

	public static boolean complete(MinecraftServer server, Identifier id) {
		State state = state(server);
		String key = id.toString();
		MissionDef def = DLContent.MISSIONS.get(id).orElse(null);
		if (def == null || state.active.remove(key) == null) {
			return false;
		}
		state.completed.add(key);
		StoryFlags flags = StoryFlags.get(server);
		def.rewardFlags().forEach(flags::set);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			reward(server, player, key, def);
			player.displayClientMessage(Component.translatable("mission.deadlands.completed", Component.translatable(def.title())), false);
		}
		def.giver().ifPresent(giver -> startCooldown(state, giver));
		state.setDirty();
		syncAll(server);
		return true;
	}

	/** Abandon or fail an active mission; its giver's cooldown starts. */
	public static boolean fail(MinecraftServer server, Identifier id) {
		State state = state(server);
		String key = id.toString();
		if (state.active.remove(key) == null) {
			return false;
		}
		state.failed.add(key);
		DLContent.MISSIONS.get(id).flatMap(MissionDef::giver).ifPresent(giver -> startCooldown(state, giver));
		state.setDirty();
		syncAll(server);
		return true;
	}

	/** A survivor turned down this giver's offer: their next one waits the cooldown. */
	public static void decline(MinecraftServer server, String giver) {
		State state = state(server);
		startCooldown(state, giver);
		state.setDirty();
	}

	/** What this giver offers now, if anything (cooldown over, none of theirs active, not stopped by the story). */
	public static Optional<Identifier> offer(MinecraftServer server, String giver, ServerPlayer to) {
		GiverDef def = DLContent.GIVERS.get(Deadlands.id(giver)).orElse(null);
		State state = state(server);
		if (def == null || state.cooldowns.getOrDefault(giver, 0) > 0 || def.stopFlag().map(StoryFlags.get(server)::has).orElse(false)) {
			return Optional.empty();
		}
		for (String active : state.active.keySet()) {
			if (DLContent.MISSIONS.get(Identifier.parse(active)).flatMap(MissionDef::giver).filter(giver::equals).isPresent()) {
				return Optional.empty();
			}
		}
		for (Identifier id : def.missions()) {
			MissionDef mission = DLContent.MISSIONS.get(id).orElse(null);
			if (mission != null && !state.completed.contains(id.toString()) && StoryFlags.holds(server, to, mission.requires())) {
				return Optional.of(id);
			}
		}
		return Optional.empty();
	}

	private static void startCooldown(State state, String giver) {
		GiverDef def = DLContent.GIVERS.get(Deadlands.id(giver)).orElse(null);
		int min = def == null ? 10 : def.cooldownMin();
		int max = def == null ? 15 : Math.max(def.cooldownMin(), def.cooldownMax());
		state.cooldowns.put(giver, (min + RANDOM.nextInt(max - min + 1)) * 1200);
	}

	// ------------------------------------------------------------------------------ progress

	private static void tick(MinecraftServer server) {
		State state = state(server);
		boolean anyoneActive = false;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			anyoneActive |= HorrorDirector.active(player);
		}
		if (anyoneActive && !state.cooldowns.isEmpty()) {
			state.cooldowns.replaceAll((giver, ticks) -> Math.max(0, ticks - 20));
			state.cooldowns.values().removeIf(ticks -> ticks == 0);
			state.setDirty();
		}
		boolean changed = false;
		for (String key : List.copyOf(state.active.keySet())) {
			MissionDef def = DLContent.MISSIONS.get(Identifier.parse(key)).orElse(null);
			if (def == null) {
				continue;
			}
			Map<String, Integer> progress = state.active.get(key);
			boolean all = true;
			for (MissionDef.Objective objective : def.objectives()) {
				int value = measure(server, objective, progress);
				if (progress.getOrDefault(objective.id(), -1) != value) {
					progress.put(objective.id(), value);
					changed = true;
				}
				all &= value >= objective.count();
			}
			if (all) {
				complete(server, Identifier.parse(key));
				changed = false; // complete() synced
			}
		}
		if (changed) {
			state.setDirty();
			syncAll(server);
		}
	}

	/** How far an objective has come (counts are capped at its target). */
	private static int measure(MinecraftServer server, MissionDef.Objective objective, Map<String, Integer> progress) {
		int count = Math.max(1, objective.count());
		switch (objective.type()) {
			case "have_item" -> {
				Item item = objective.target().map(BuiltInRegistries.ITEM::getValue).orElse(null);
				int best = 0;
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					best = Math.max(best, carried(player.getInventory(), item));
				}
				return Math.min(count, best);
			}
			case "flag" -> {
				return objective.flag().map(StoryFlags.get(server)::has).orElse(false) ? count : 0;
			}
			case "visit" -> {
				if (objective.target().isEmpty()) {
					return 0;
				}
				ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, objective.target().get());
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					if (player.level().structureManager().getStructureWithPieceAt(player.blockPosition(), holder -> holder.is(key)).isValid()) {
						return count;
					}
				}
				return Math.min(count, progress.getOrDefault(objective.id(), 0));
			}
			default -> {
				return Math.min(count, progress.getOrDefault(objective.id(), 0)); // kill: counted as it happens
			}
		}
	}

	private static int carried(Inventory inventory, Item item) {
		if (item == null) {
			return 0;
		}
		int total = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/** A kill (or any counted event) toward active missions' objectives of that type and target. */
	private static void counted(MinecraftServer server, String type, Identifier target) {
		State state = state(server);
		for (Map.Entry<String, Map<String, Integer>> entry : state.active.entrySet()) {
			MissionDef def = DLContent.MISSIONS.get(Identifier.parse(entry.getKey())).orElse(null);
			if (def == null) {
				continue;
			}
			for (MissionDef.Objective objective : def.objectives()) {
				if (objective.type().equals(type) && objective.target().filter(target::equals).isPresent()) {
					entry.getValue().merge(objective.id(), 1, Integer::sum);
					state.setDirty();
				}
			}
		}
	}

	private static void reward(MinecraftServer server, ServerPlayer player, String key, MissionDef def) {
		State state = state(server);
		List<String> given = state.rewarded.computeIfAbsent(key, k -> new ArrayList<>());
		String uuid = player.getUUID().toString();
		if (given.contains(uuid)) {
			return;
		}
		given.add(uuid);
		for (MissionDef.RewardItem reward : def.rewardItems()) {
			Item item = BuiltInRegistries.ITEM.getValue(reward.item());
			ItemStack stack = new ItemStack(item, Math.max(1, reward.count()));
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
		}
		state.setDirty();
	}

	private static void rewardPending(MinecraftServer server, ServerPlayer player) {
		State state = state(server);
		for (String key : state.completed) {
			DLContent.MISSIONS.get(Identifier.parse(key)).ifPresent(def -> reward(server, player, key, def));
		}
	}

	// ------------------------------------------------------------------------------ what clients see

	public static void syncAll(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			sync(server, player);
		}
	}

	public static void sync(MinecraftServer server, ServerPlayer player) {
		State state = state(server);
		List<MissionSyncPayload.Entry> entries = new ArrayList<>();
		addEntries(entries, state, state.active.keySet(), MissionSyncPayload.ACTIVE);
		addEntries(entries, state, state.completed, MissionSyncPayload.COMPLETED);
		addEntries(entries, state, state.failed, MissionSyncPayload.FAILED);
		ServerPlayNetworking.send(player, new MissionSyncPayload(player.getAttachedOrCreate(RECEIVED), entries));
	}

	private static void addEntries(List<MissionSyncPayload.Entry> entries, State state, Set<String> keys, int status) {
		for (String key : keys) {
			MissionDef def = DLContent.MISSIONS.get(Identifier.parse(key)).orElse(null);
			if (def == null) {
				continue;
			}
			Map<String, Integer> progress = state.active.getOrDefault(key, Map.of());
			List<MissionSyncPayload.Objective> objectives = new ArrayList<>();
			for (MissionDef.Objective objective : def.objectives()) {
				int count = Math.max(1, objective.count());
				int value = status == MissionSyncPayload.COMPLETED ? count : progress.getOrDefault(objective.id(), 0);
				objectives.add(new MissionSyncPayload.Objective(objective.text(), value, count, value >= count));
			}
			entries.add(new MissionSyncPayload.Entry(key, def.title(), def.description(), def.giver().orElse(""), status, objectives, def.lore()));
		}
	}

	/** For {@code /deadlands mission list}. */
	public static String describe(MinecraftServer server) {
		State state = state(server);
		return "active " + state.active.keySet() + ", completed " + state.completed + ", failed " + state.failed + ", cooldowns "
			+ state.cooldowns.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue() / 20 + "s").toList()
			+ ", loaded " + DLContent.MISSIONS.all().size() + " missions and " + DLContent.GIVERS.all().size() + " givers";
	}
}
