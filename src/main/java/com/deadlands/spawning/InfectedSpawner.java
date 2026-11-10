package com.deadlands.spawning;

import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import com.deadlands.entity.InfectedEntity;
import com.deadlands.entity.InfectedKind;
import com.deadlands.world.power.PowerGrid;
import com.deadlands.world.town.TownLookup;
import com.deadlands.world.town.TownSite;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Natural infected population around survivors in the overworld (DEADLANDS replaces Minecraft's
 * monster spawning in its biomes; see the biome files).
 *
 * <p>Every two seconds, for each survivor: if fewer infected than the local cap are within 128
 * blocks (times {@code zombies.daytimePopulation} by day), one group spawns between
 * {@code spawnDistanceMin} and {@code spawnDistanceMax} blocks away. By day most groups appear
 * indoors (in buildings, under roofs) and only in the dark; at night anywhere dark. Nothing appears
 * in survivor-lit rooms, closer than the minimum distance to any survivor, or past the global cap.
 * Groups share a pack and wander together. Natural infected despawn far from everyone.
 */
public final class InfectedSpawner {
	private static final int INTERVAL_TICKS = 40;
	private static final int ATTEMPTS_PER_PLAYER = 3;
	private static final double TOWN_RADIUS = 140.0;

	private InfectedSpawner() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % INTERVAL_TICKS == 13) {
				tick(server);
			}
		});
	}

	private static void tick(MinecraftServer server) {
		DeadlandsConfig.Zombies config = ConfigManager.get().zombies;
		ServerLevel level = server.overworld();
		if (!config.naturalSpawning || config.populationMultiplier <= 0.0 || level.getDifficulty() == Difficulty.PEACEFUL
			|| !level.getGameRules().get(GameRules.SPAWN_MOBS) || !level.getGameRules().get(GameRules.SPAWN_MONSTERS)) {
			return;
		}
		List<ServerPlayer> players = level.players();
		for (ServerPlayer player : players) {
			if (!player.isSpectator() && player.isAlive()) {
				populateAround(level, player, players, config);
			}
		}
	}

	private static void populateAround(ServerLevel level, ServerPlayer player, List<ServerPlayer> players, DeadlandsConfig.Zombies config) {
		RandomSource random = level.getRandom();
		boolean night = level.isDarkOutside();
		int target = (int) Math.round(InfectedManager.localCap() * (night ? 1.0 : config.daytimePopulation));
		int near = InfectedManager.countNear(level, player.blockPosition());
		if (near >= target || InfectedManager.loadedCount(level.getServer()) >= config.globalCap) {
			return;
		}
		double min = Math.min(config.spawnDistanceMin, config.spawnDistanceMax - 4);
		double max = Math.max(config.spawnDistanceMax, min + 4);
		for (int attempt = 0; attempt < ATTEMPTS_PER_PLAYER; attempt++) {
			boolean wantIndoors = !night || random.nextFloat() < 0.3F;
			BlockPos pos = SpawnFinder.around(level, player.blockPosition(), min, max, random, wantIndoors);
			if (pos == null || !farFromEveryone(pos, players, min) || !SpawnFinder.isDark(level, pos)) {
				continue;
			}
			boolean open = level.canSeeSky(pos);
			if (!night && open && random.nextFloat() < 0.8F) {
				continue;
			}
			boolean underground = pos.getY() < level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - 10;
			TownSite town = TownLookup.nearest(level, pos, TOWN_RADIUS).orElse(null);
			if (town != null && night && PowerGrid.isPowered(level, town) && random.nextFloat() < 0.4F) {
				continue; // a town with its lights back on draws fewer of them out of the dark
			}
			InfectedKind kind = InfectedSpawnTable.pick(level, pos, night, town, underground, random);
			int group = InfectedSpawnTable.groupSize(kind, random) + (town != null && random.nextBoolean() ? 1 : 0);
			group = Math.min(group, Math.min(target - near, InfectedManager.roomNear(level, pos)));
			if (spawnGroup(level, kind, pos, group, random) > 0) {
				return;
			}
		}
	}

	private static boolean farFromEveryone(BlockPos pos, List<ServerPlayer> players, double min) {
		for (ServerPlayer player : players) {
			if (player.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) < min * min) {
				return false;
			}
		}
		return true;
	}

	/** Spawn up to {@code count} of a kind around {@code pos} as one pack. Returns how many appeared. */
	static int spawnGroup(ServerLevel level, InfectedKind kind, BlockPos pos, int count, RandomSource random) {
		int spawned = 0;
		InfectedEntity.GroupData pack = new InfectedEntity.GroupData(InfectedEntity.newPackId(random));
		for (int i = 0; i < count; i++) {
			BlockPos spot = i == 0 ? pos : SpawnFinder.column(level, pos.getX() + random.nextInt(7) - 3, pos.getZ() + random.nextInt(7) - 3,
				pos.getY(), !level.canSeeSky(pos), random);
			if (spot == null || Math.abs(spot.getY() - pos.getY()) > 4) {
				continue;
			}
			if (SpawnFinder.spawn(level, kind, spot, EntitySpawnReason.NATURAL, pack) != null) {
				spawned++;
			}
		}
		return spawned;
	}
}
