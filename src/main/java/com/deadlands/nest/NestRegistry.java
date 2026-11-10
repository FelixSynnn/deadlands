package com.deadlands.nest;

import com.deadlands.noise.Loudness;
import com.deadlands.noise.NoiseSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Which nests are loaded right now, per dimension, so a noise can find the nests that hear it
 * without scanning chunks. Kept current by Fabric's block-entity load and unload events.
 */
public final class NestRegistry {
	private static final Map<ResourceKey<Level>, Set<BlockPos>> LOADED = new HashMap<>();

	private NestRegistry() {
	}

	public static void init() {
		ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> {
			if (blockEntity instanceof NestBlockEntity) {
				LOADED.computeIfAbsent(level.dimension(), unused -> new HashSet<>()).add(blockEntity.getBlockPos().immutable());
			}
		});
		ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, level) -> {
			if (blockEntity instanceof NestBlockEntity) {
				Set<BlockPos> nests = LOADED.get(level.dimension());
				if (nests != null) {
					nests.remove(blockEntity.getBlockPos());
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LOADED.clear());
	}

	/** Deliver a survivor-made noise to every loaded nest that can hear it. */
	public static void onNoise(ServerLevel level, Vec3 pos, Loudness loudness, double radius) {
		Set<BlockPos> nests = LOADED.get(level.dimension());
		if (nests == null || nests.isEmpty()) {
			return;
		}
		// Copy first: a release can spawn infected whose noise reaches here again.
		List<BlockPos> candidates = new ArrayList<>(nests);
		for (BlockPos nestPos : candidates) {
			if (level.getBlockEntity(nestPos) instanceof NestBlockEntity nest) {
				double heard = radius * nest.kind().hearing();
				double distance = Math.sqrt(nestPos.distToCenterSqr(pos));
				if (distance <= heard) {
					// One block above the core, so the nest block itself does not count as a wall.
					heard *= NoiseSystem.muffling(level, pos.add(0.0, 0.5, 0.0), Vec3.atCenterOf(nestPos.above()));
				}
				if (distance <= heard) {
					nest.hear(level, pos, loudness, distance, heard);
				}
			}
		}
	}

	/** Loaded nests in a dimension (for commands and tests). */
	public static int loadedCount(ServerLevel level) {
		Set<BlockPos> nests = LOADED.get(level.dimension());
		return nests == null ? 0 : nests.size();
	}
}
