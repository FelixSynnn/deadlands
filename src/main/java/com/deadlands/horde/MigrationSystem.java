package com.deadlands.horde;

import com.deadlands.config.ConfigManager;
import com.deadlands.entity.InfectedEntity;
import com.deadlands.entity.InfectedKind;
import com.deadlands.persistence.DeadlandsWorldData;
import com.deadlands.persistence.MigrationRecord;
import com.deadlands.spawning.InfectedManager;
import com.deadlands.spawning.SpawnFinder;
import com.deadlands.world.town.TownLookup;
import com.deadlands.world.town.TownSite;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Groups of infected that roam from one town to another ({@code events.migratingHordes}).
 *
 * <p>Distance-based simulation: while no survivor is near, a migration is only a
 * {@link MigrationRecord} (position, destination, how many are left) that walks about a block a
 * second toward its town. When a survivor comes within {@link #MATERIALIZE_RANGE} blocks (and the
 * spot is in a ticking chunk), it becomes real infected that keep walking to the same town
 * ({@code MigrateGoal}) and react to survivors normally. When everyone has gone more than
 * {@link #RELEASE_RANGE} blocks away for half a minute, the living members are taken out again and
 * the record carries on. Deaths reduce the count; at its town the group disbands into the local
 * population. Members are never kept apart from their record: one saved with an unloading chunk
 * is removed when it loads again, since the record already counts it.
 *
 * <p>New groups start every couple of minutes while there are fewer than one per two survivors
 * (at least one, at most four), 220 to 320 blocks from a survivor and heading for the town nearest
 * that survivor, so they cross the survivor's area.
 */
public final class MigrationSystem {
	private static final double MATERIALIZE_RANGE = 88.0;
	private static final double RELEASE_RANGE = 150.0;
	private static final int RELEASE_AFTER_TICKS = 600;
	private static final double SPEED_PER_SECOND = 1.0;
	private static final double ARRIVED_DISTANCE = 24.0;
	private static final int CREATE_INTERVAL_TICKS = 2400;

	/** Members currently in the world, by migration id. */
	private static final Map<Integer, Set<UUID>> MEMBERS = new HashMap<>();
	/** Game time a survivor was last near a materialized migration. */
	private static final Map<Integer, Long> LAST_NEAR = new HashMap<>();

	private MigrationSystem() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 17) {
				tick(server);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof InfectedEntity infected && infected.migrationId() != 0) {
				Set<UUID> members = MEMBERS.get(infected.migrationId());
				if (members == null || !members.contains(infected.getUUID())) {
					infected.discard(); // its record already counts it and will bring it back when someone is near
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MEMBERS.clear();
			LAST_NEAR.clear();
		});
	}

	/** Where a migration is heading (for {@code MigrateGoal}), or null if it no longer exists. */
	public static @Nullable Vec3 destination(ServerLevel level, int id) {
		return DeadlandsWorldData.get(level.getServer()).migration(id)
			.map(r -> new Vec3(r.destX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) r.destX(), (int) r.destZ()), r.destZ()))
			.orElse(null);
	}

	/** A member died: one fewer in its group. */
	public static void onMemberDeath(MinecraftServer server, InfectedEntity infected) {
		DeadlandsWorldData data = DeadlandsWorldData.get(server);
		data.migration(infected.migrationId()).ifPresent(record -> data.putMigration(record.withCount(record.count() - 1)));
		Set<UUID> members = MEMBERS.get(infected.migrationId());
		if (members != null) {
			members.remove(infected.getUUID());
		}
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		DeadlandsWorldData data = DeadlandsWorldData.get(server);
		if (!ConfigManager.get().events.migratingHordes) {
			for (MigrationRecord record : List.copyOf(data.migrations())) {
				release(level, record, data, false);
				data.removeMigration(record.id());
			}
			return;
		}
		List<ServerPlayer> survivors = level.players().stream().filter(p -> p.isAlive() && !p.isSpectator()).toList();
		boolean spawning = level.getDifficulty() != Difficulty.PEACEFUL && level.getGameRules().get(GameRules.SPAWN_MONSTERS);
		if (server.getTickCount() % CREATE_INTERVAL_TICKS == 17 && spawning && !survivors.isEmpty()) {
			int wanted = Math.min(4, Math.max(1, survivors.size() / 2));
			if (data.migrations().size() < wanted && level.getRandom().nextBoolean()) {
				create(level, data, survivors.get(level.getRandom().nextInt(survivors.size())));
			}
		}
		for (MigrationRecord record : List.copyOf(data.migrations())) {
			if (record.count() <= 0) {
				release(level, record, data, false);
				data.removeMigration(record.id());
				continue;
			}
			boolean materialized = MEMBERS.containsKey(record.id());
			MigrationRecord current = materialized ? follow(level, record) : advance(record);
			if (current.distanceToDestination() < ARRIVED_DISTANCE) {
				release(level, current, data, true);
				data.removeMigration(current.id());
				continue;
			}
			data.putMigration(current);
			double nearest = nearestSurvivor(survivors, current.x(), current.z());
			if (!materialized && nearest < MATERIALIZE_RANGE && spawning) {
				materialize(level, current);
			} else if (materialized) {
				long now = level.getGameTime();
				if (nearest < RELEASE_RANGE) {
					LAST_NEAR.put(current.id(), now);
				} else if (now - LAST_NEAR.getOrDefault(current.id(), now) > RELEASE_AFTER_TICKS) {
					release(level, current, data, false);
				}
			}
		}
	}

	private static void create(ServerLevel level, DeadlandsWorldData data, ServerPlayer survivor) {
		RandomSource random = level.getRandom();
		BlockPos here = survivor.blockPosition();
		TownSite town = TownLookup.nearest(level, here, 400.0).orElse(null);
		double angle = random.nextDouble() * Math.PI * 2.0;
		double distance = 220.0 + random.nextDouble() * 100.0;
		double startX = here.getX() + Math.cos(angle) * distance;
		double startZ = here.getZ() + Math.sin(angle) * distance;
		double destX;
		double destZ;
		if (town != null) {
			destX = town.centerX();
			destZ = town.centerZ();
		} else {
			// No town near: walk right across the survivor's area and as far again beyond.
			destX = here.getX() - Math.cos(angle) * distance;
			destZ = here.getZ() - Math.sin(angle) * distance;
		}
		long day = level.getDayCount() + 1;
		int count = 8 + random.nextInt(8) + (int) Math.min(12, day / 5);
		data.putMigration(new MigrationRecord(data.nextMigrationId(), startX, startZ, destX, destZ, count, level.getGameTime()));
	}

	/** Abstract walking, about a block a second. */
	private static MigrationRecord advance(MigrationRecord record) {
		double dx = record.destX() - record.x();
		double dz = record.destZ() - record.z();
		double length = Math.max(1e-6, Math.sqrt(dx * dx + dz * dz));
		double step = Math.min(length, SPEED_PER_SECOND);
		return record.withPosition(record.x() + dx / length * step, record.z() + dz / length * step);
	}

	/** While materialized, the record follows the members' centre. */
	private static MigrationRecord follow(ServerLevel level, MigrationRecord record) {
		Set<UUID> members = MEMBERS.get(record.id());
		double sx = 0.0;
		double sz = 0.0;
		int n = 0;
		for (UUID id : new ArrayList<>(members)) {
			Entity entity = level.getEntity(id);
			if (entity instanceof InfectedEntity infected && infected.isAlive()) {
				sx += infected.getX();
				sz += infected.getZ();
				n++;
			}
		}
		if (n == 0) {
			// Every member is unloaded (the survivor left fast): back to the abstract walk.
			MEMBERS.remove(record.id());
			LAST_NEAR.remove(record.id());
			return record;
		}
		return record.withPosition(sx / n, sz / n);
	}

	private static double nearestSurvivor(List<ServerPlayer> survivors, double x, double z) {
		double best = Double.MAX_VALUE;
		for (ServerPlayer player : survivors) {
			best = Math.min(best, Math.hypot(player.getX() - x, player.getZ() - z));
		}
		return best;
	}

	private static void materialize(ServerLevel level, MigrationRecord record) {
		BlockPos center = BlockPos.containing(record.x(), 0, record.z());
		if (!level.isLoaded(center)) {
			return;
		}
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX(), center.getZ());
		BlockPos origin = new BlockPos(center.getX(), surface, center.getZ());
		if (!level.isPositionEntityTicking(origin) || !InfectedManager.hordeHasRoom(level.getServer())) {
			return;
		}
		RandomSource random = level.getRandom();
		InfectedEntity.GroupData pack = new InfectedEntity.GroupData(InfectedEntity.newPackId(random));
		Set<UUID> members = new HashSet<>();
		MEMBERS.put(record.id(), members);
		LAST_NEAR.put(record.id(), level.getGameTime());
		boolean night = level.isDarkOutside();
		for (int i = 0, tries = 0; i < record.count() && tries < record.count() * 4; tries++) {
			BlockPos pos = SpawnFinder.column(level, origin.getX() + random.nextInt(13) - 6, origin.getZ() + random.nextInt(13) - 6, surface, false, random);
			if (pos == null) {
				continue;
			}
			double roll = random.nextDouble();
			InfectedKind kind = roll < (night ? 0.18 : 0.08) ? InfectedKind.RUNNER : roll < 0.26 ? InfectedKind.CRAWLER : roll < 0.30 ? InfectedKind.BRUTE
				: roll < 0.33 ? InfectedKind.BLOATER : InfectedKind.WALKER;
			// Members are registered before they are added, so the load hook keeps them.
			InfectedEntity member = SpawnFinder.spawn(level, kind, pos, EntitySpawnReason.EVENT, pack, record.id(), members);
			if (member != null) {
				i++;
			}
		}
		if (members.isEmpty()) {
			MEMBERS.remove(record.id());
			LAST_NEAR.remove(record.id());
		}
	}

	/**
	 * Take the members out of the world. {@code settle}: they arrived (or the group dissolved) and
	 * stay as ordinary infected; otherwise they are removed and the record keeps counting them.
	 */
	private static void release(ServerLevel level, MigrationRecord record, DeadlandsWorldData data, boolean settle) {
		Set<UUID> members = MEMBERS.remove(record.id());
		LAST_NEAR.remove(record.id());
		if (members == null) {
			return;
		}
		for (UUID id : members) {
			if (level.getEntity(id) instanceof InfectedEntity infected) {
				if (settle) {
					infected.setMigrationId(0);
				} else {
					infected.discard();
				}
			}
		}
	}

	/** Loaded migrating infected per group (for commands). */
	public static int materializedGroups() {
		return MEMBERS.size();
	}
}
