package com.deadlands.entity;

import com.deadlands.Deadlands;
import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import com.deadlands.npc.SafeSpots;
import com.deadlands.registry.DLAsesino;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Brings the Asesino out (the {@code asesino} config section): only at night (unless {@code nightOnly}
 * is off), in the overworld, never on Peaceful. Every half minute each survivor is a chance: a good one
 * in or near a town ({@code townSpawnChance}), a small one elsewhere ({@code wildSpawnChance}). At most
 * one within {@code areaRadius} blocks (about one per town) and {@code maxAlive} in the world. He comes
 * in 32 to 56 blocks away, somewhere safe that no survivor can see.
 */
public final class AsesinoSpawner {
	private static final ResourceKey<Structure> TOWN = ResourceKey.create(Registries.STRUCTURE, Deadlands.id("town"));

	private AsesinoSpawner() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 600 == 299) {
				tick(server);
			}
		});
	}

	private static void tick(MinecraftServer server) {
		DeadlandsConfig.Asesino config = ConfigManager.get().asesino;
		ServerLevel level = server.overworld();
		if (!config.enabled || level.getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		long time = Math.floorMod(level.getDayTime(), 24000L);
		if (config.nightOnly && (time < 13000L || time > 22500L)) {
			return;
		}
		int alive = level.getEntities(DLAsesino.ASESINO, e -> e.isAlive()).size();
		for (ServerPlayer player : level.players()) {
			if (alive >= config.maxAlive) {
				return;
			}
			if (player.isSpectator() || player.isCreative()) {
				continue;
			}
			double radius = config.areaRadius;
			if (!level.getEntitiesOfClass(AsesinoEntity.class, new AABB(player.blockPosition()).inflate(radius, 128.0, radius)).isEmpty()) {
				continue;
			}
			double chance = nearTown(level, player.blockPosition()) ? config.townSpawnChance : config.wildSpawnChance;
			if (level.getRandom().nextDouble() >= chance) {
				continue;
			}
			if (spawn(level, player, level.getRandom())) {
				alive++;
			}
		}
	}

	/** In a town, or within about 48 blocks of one. */
	private static boolean nearTown(ServerLevel level, BlockPos pos) {
		Structure town = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(TOWN);
		if (town == null) {
			return false;
		}
		for (BlockPos probe : new BlockPos[] {pos, pos.offset(48, 0, 0), pos.offset(-48, 0, 0), pos.offset(0, 0, 48), pos.offset(0, 0, -48)}) {
			if (level.structureManager().getStructureAt(probe, town).isValid()) {
				return true;
			}
		}
		return false;
	}

	private static boolean spawn(ServerLevel level, ServerPlayer player, RandomSource random) {
		for (int attempt = 0; attempt < 6; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = 32.0 + random.nextDouble() * 24.0;
			BlockPos around = BlockPos.containing(player.getX() + Math.cos(angle) * distance, player.getY(), player.getZ() + Math.sin(angle) * distance);
			if (!level.isLoaded(around)) {
				continue;
			}
			around = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, around);
			BlockPos spot = SafeSpots.find(level, around, 0, 6, random);
			if (spot == null || seen(level, spot)) {
				continue;
			}
			AsesinoEntity asesino = DLAsesino.ASESINO.create(level, EntitySpawnReason.NATURAL);
			if (asesino == null) {
				return false;
			}
			asesino.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			level.addFreshEntity(asesino);
			return true;
		}
		return false;
	}

	private static boolean seen(ServerLevel level, BlockPos spot) {
		Vec3 head = Vec3.atBottomCenterOf(spot).add(0.0, 1.6, 0.0);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(head) < 64.0 * 64.0 && level.clip(new ClipContext(player.getEyePosition(), head, ClipContext.Block.VISUAL,
				ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS) {
				return true;
			}
		}
		return false;
	}
}
