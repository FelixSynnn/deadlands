package com.deadlands.spawning;

import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import com.deadlands.entity.InfectedEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Knows every loaded infected (through Fabric's entity load and unload events), enforces the
 * population caps and hands out AI time.
 *
 * <p><b>Caps.</b> {@code zombies.globalCap} limits loaded infected in the whole world and
 * {@code zombies.maxPerPlayer} (times {@code populationMultiplier}) those within 128 blocks of a
 * survivor. Hordes get a separate allowance on top so a horde night is not starved by wanderers.
 *
 * <p><b>AI budget.</b> Once a second the infected of each level are ranked by distance to the
 * nearest survivor (anything chasing or hunting first). The first {@code performance.maxActiveAi}
 * run their AI every tick; the rest every {@code performance.distantAiInterval} ticks.
 */
public final class InfectedManager {
	public static final double LOCAL_RADIUS = 128.0;
	/** Extra room for horde members beyond the global cap. */
	public static final int HORDE_ALLOWANCE = 120;

	private static final Map<ResourceKey<Level>, Set<InfectedEntity>> LOADED = new HashMap<>();

	private InfectedManager() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof InfectedEntity infected) {
				LOADED.computeIfAbsent(level.dimension(), unused -> Collections.newSetFromMap(new java.util.IdentityHashMap<>())).add(infected);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof InfectedEntity infected) {
				Set<InfectedEntity> set = LOADED.get(level.dimension());
				if (set != null) {
					set.remove(infected);
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 7) {
				assignAiBudget(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LOADED.clear());
	}

	// ------------------------------------------------------------------------------------ counting

	public static int loadedCount(MinecraftServer server) {
		int total = 0;
		for (Set<InfectedEntity> set : LOADED.values()) {
			total += set.size();
		}
		return total;
	}

	public static List<InfectedEntity> loaded(ServerLevel level) {
		Set<InfectedEntity> set = LOADED.get(level.dimension());
		return set == null ? List.of() : List.copyOf(set);
	}

	/** Infected within {@link #LOCAL_RADIUS} of {@code pos}. */
	public static int countNear(ServerLevel level, BlockPos pos) {
		AABB area = new AABB(pos).inflate(LOCAL_RADIUS, 64.0, LOCAL_RADIUS);
		return level.getEntitiesOfClass(InfectedEntity.class, area, InfectedEntity::isAlive).size();
	}

	public static int localCap() {
		DeadlandsConfig.Zombies z = ConfigManager.get().zombies;
		return (int) Math.round(z.maxPerPlayer * z.populationMultiplier);
	}

	/** How many more infected may appear around {@code pos} right now (natural caps). */
	public static int roomNear(ServerLevel level, BlockPos pos) {
		int global = ConfigManager.get().zombies.globalCap - loadedCount(level.getServer());
		int local = localCap() - countNear(level, pos);
		return Math.max(0, Math.min(global, local));
	}

	/** True when {@code count} more infected fit around {@code pos} under the natural caps. */
	public static boolean canAddNear(ServerLevel level, BlockPos pos, int count) {
		return roomNear(level, pos) >= Math.max(1, count);
	}

	/** Horde members may exceed the global cap by {@link #HORDE_ALLOWANCE}. */
	public static boolean hordeHasRoom(MinecraftServer server) {
		return loadedCount(server) < ConfigManager.get().zombies.globalCap + HORDE_ALLOWANCE;
	}

	// ------------------------------------------------------------------------------------ AI budget

	private static void assignAiBudget(MinecraftServer server) {
		DeadlandsConfig.Performance perf = ConfigManager.get().performance;
		int budget = perf.maxActiveAi;
		int slow = Math.max(1, perf.distantAiInterval);
		for (ServerLevel level : server.getAllLevels()) {
			Set<InfectedEntity> set = LOADED.get(level.dimension());
			if (set == null || set.isEmpty()) {
				continue;
			}
			List<ServerPlayer> players = level.players();
			List<InfectedEntity> ranked = new ArrayList<>(set);
			Map<InfectedEntity, Double> distance = new HashMap<>(ranked.size() * 2);
			for (InfectedEntity infected : ranked) {
				double nearest = Double.MAX_VALUE;
				for (ServerPlayer player : players) {
					nearest = Math.min(nearest, player.distanceToSqr(infected));
				}
				boolean busy = infected.getTarget() != null || infected.isHordeMember();
				distance.put(infected, busy ? -1.0 : nearest);
			}
			ranked.sort(Comparator.comparingDouble(distance::get));
			for (int i = 0; i < ranked.size(); i++) {
				ranked.get(i).setAiInterval(i < budget ? 1 : slow);
			}
			budget = Math.max(0, budget - ranked.size());
		}
	}
}
