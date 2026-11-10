package com.deadlands.spawning;

import com.deadlands.entity.InfectedEntity;
import com.deadlands.entity.InfectedKind;
import com.deadlands.entity.ai.InfectedNav;
import com.deadlands.registry.DLEntities;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Finds places to put infected and spawns them. Shared by natural spawning, hordes, nests,
 * screamers and migrating groups, so all of them follow the same rules: loaded and ticking
 * chunks only, room for the body, no fluid, and (unless told otherwise) not inside lit rooms.
 */
public final class SpawnFinder {
	private static final int SURFACE_SCAN_DEPTH = 24;
	private static final int UNDERGROUND_BAND = 8;
	/** Block light at or above this keeps natural infected from appearing (survivor-lit rooms). */
	public static final int MAX_SPAWN_BLOCK_LIGHT = 7;

	private SpawnFinder() {
	}

	/**
	 * A standable spot in the column at x/z. Near the surface this is the open surface or a roofed
	 * floor below it (inside a building); when {@code refY} is deep underground the column is
	 * searched around that height instead (caves, basements, tunnels).
	 */
	public static @Nullable BlockPos column(ServerLevel level, int x, int z, int refY, boolean wantIndoors, RandomSource random) {
		BlockPos probe = new BlockPos(x, refY, z);
		if (!level.isLoaded(probe)) {
			return null;
		}
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		boolean underground = refY < top - 10;
		int from = underground ? Math.min(top, refY + UNDERGROUND_BAND) : top;
		int to = Math.max(level.getMinY() + 1, underground ? refY - UNDERGROUND_BAND : top - SURFACE_SCAN_DEPTH);
		List<BlockPos> roofed = new ArrayList<>(4);
		BlockPos open = null;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, from, z);
		for (int y = from; y >= to; y--) {
			cursor.setY(y);
			if (InfectedNav.isStandable(level, cursor)) {
				BlockPos found = cursor.immutable();
				if (level.canSeeSky(found)) {
					if (open == null) {
						open = found;
					}
				} else {
					roofed.add(found);
				}
				y--; // the block below a standable spot is its floor
			}
		}
		if (wantIndoors && !roofed.isEmpty()) {
			return roofed.get(random.nextInt(roofed.size()));
		}
		if (open != null) {
			return open;
		}
		return roofed.isEmpty() ? null : roofed.get(random.nextInt(roofed.size()));
	}

	/** A spot in a ring between {@code min} and {@code max} blocks around {@code center}. */
	public static @Nullable BlockPos around(ServerLevel level, BlockPos center, double min, double max, RandomSource random, boolean wantIndoors) {
		return towards(level, center, random.nextDouble() * Math.PI * 2.0, Math.PI, min, max, random, wantIndoors);
	}

	/** Like {@link #around} but within {@code spread} radians of {@code bearing} (hordes come from one side). */
	public static @Nullable BlockPos towards(ServerLevel level, BlockPos center, double bearing, double spread, double min, double max,
		RandomSource random, boolean wantIndoors) {
		double angle = bearing + (random.nextDouble() * 2.0 - 1.0) * spread;
		double distance = min + random.nextDouble() * Math.max(0.0, max - min);
		int x = center.getX() + (int) Math.round(Math.cos(angle) * distance);
		int z = center.getZ() + (int) Math.round(Math.sin(angle) * distance);
		return column(level, x, z, center.getY(), wantIndoors, random);
	}

	/** Room for the body, no fluid, in a chunk that ticks entities. */
	public static boolean canSpawn(ServerLevel level, EntityType<?> type, BlockPos pos) {
		if (!level.isPositionEntityTicking(pos)) {
			return false;
		}
		AABB body = type.getDimensions().makeBoundingBox(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		return level.noCollision(body) && !level.containsAnyLiquid(body);
	}

	public static boolean isDark(ServerLevel level, BlockPos pos) {
		return level.getBrightness(LightLayer.BLOCK, pos) <= MAX_SPAWN_BLOCK_LIGHT;
	}

	/** Spawn one infected of {@code kind} at {@code pos}, or return null if it does not fit there. */
	public static @Nullable InfectedEntity spawn(ServerLevel level, InfectedKind kind, BlockPos pos, EntitySpawnReason reason,
		@Nullable SpawnGroupData group) {
		EntityType<? extends InfectedEntity> type = DLEntities.type(kind);
		if (!canSpawn(level, type, pos)) {
			return null;
		}
		InfectedEntity infected = type.create(level, reason);
		if (infected == null) {
			return null;
		}
		infected.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		infected.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), reason, group);
		return level.addFreshEntity(infected) ? infected : null;
	}

	/**
	 * Spawn a member of a migrating group. It is marked and listed in {@code members} before it
	 * enters the world, so the migration system's entity-load check recognises it.
	 */
	public static @Nullable InfectedEntity spawn(ServerLevel level, InfectedKind kind, BlockPos pos, EntitySpawnReason reason,
		@Nullable SpawnGroupData group, int migrationId, java.util.Set<java.util.UUID> members) {
		EntityType<? extends InfectedEntity> type = DLEntities.type(kind);
		if (!canSpawn(level, type, pos)) {
			return null;
		}
		InfectedEntity infected = type.create(level, reason);
		if (infected == null) {
			return null;
		}
		infected.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		infected.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), reason, group);
		infected.setMigrationId(migrationId);
		members.add(infected.getUUID());
		if (level.addFreshEntity(infected)) {
			return infected;
		}
		members.remove(infected.getUUID());
		return null;
	}
}
