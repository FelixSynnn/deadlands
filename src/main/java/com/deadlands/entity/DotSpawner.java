package com.deadlands.entity;

import com.deadlands.Deadlands;
import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import com.deadlands.entity.ai.Stalking;
import com.deadlands.registry.DLDot;
import com.deadlands.world.town.TownLookup;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Where D.O.T. walks. Once a minute at night, for each survivor with no D.O.T. within {@code dot.areaRadius}, a
 * {@code dot.spawnChance} roll:
 * <ul>
 *   <li>inside one of the big structures (tag {@code deadlands:dot_haunts}: subway stations, the research facility,
 *       Site Calder, the school, the power station, the treatment plant), he appears elsewhere in it, 16 to 48 blocks
 *       away and out of sight, walking;</li>
 *   <li>in a town, he stands in the road 40 to 64 blocks ahead of them, facing them, in the way.</li>
 * </ul>
 * Nowhere else. He leaves at dawn.
 */
public final class DotSpawner {
	public static final TagKey<Structure> HAUNTS = TagKey.create(Registries.STRUCTURE, Deadlands.id("dot_haunts"));
	private static final Set<String> ROAD = Set.of("asphalt", "cracked_asphalt", "asphalt_yellow_line", "asphalt_white_line", "potholed_asphalt");

	private DotSpawner() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 1200 == 611) {
				tick(server);
			}
		});
	}

	private static void tick(MinecraftServer server) {
		DeadlandsConfig.Dot config = ConfigManager.get().dot;
		ServerLevel level = server.overworld();
		long time = Math.floorMod(level.getDayTime(), 24000L);
		if (!config.enabled || level.getDifficulty() == Difficulty.PEACEFUL || time < 13000L || time > 22500L) {
			return;
		}
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.isCreative() || random.nextDouble() >= config.spawnChance) {
				continue;
			}
			double r = config.areaRadius;
			if (!level.getEntitiesOfClass(DotEntity.class, new AABB(player.blockPosition()).inflate(r, 128.0, r), DotEntity::isAlive).isEmpty()) {
				continue; // one per area
			}
			StructureStart haunt = level.structureManager().getStructureWithPieceAt(player.blockPosition(), HAUNTS);
			if (config.jumpscares && config.onTownRoads && !haunt.isValid() && random.nextInt(3) == 0
				&& TownLookup.nearest(level, player.blockPosition(), 160.0).isPresent() && roadGhost(level, player, random)) {
				continue; // a Road Ghost this time
			}
			if (config.inStructures && haunt.isValid()) {
				inStructure(level, player, haunt.getBoundingBox(), random);
			} else if (config.onTownRoads && TownLookup.nearest(level, player.blockPosition(), 160.0).isPresent()) {
				onRoad(level, player, random);
			}
		}
	}

	private static void inStructure(ServerLevel level, ServerPlayer player, BoundingBox box, RandomSource random) {
		for (int tries = 0; tries < 24; tries++) {
			BlockPos probe = new BlockPos(box.minX() + random.nextInt(Math.max(1, box.getXSpan())), player.getBlockY() + random.nextInt(9) - 4,
				box.minZ() + random.nextInt(Math.max(1, box.getZSpan())));
			BlockPos floor = Stalking.standable(level, probe, 3);
			if (floor == null) {
				continue;
			}
			double d = Math.sqrt(floor.distSqr(player.blockPosition()));
			if (d < 16.0 || d > 48.0 || Stalking.visibleFrom(player, Vec3.atBottomCenterOf(floor), 2.6)) {
				continue;
			}
			spawn(level, floor, player, false);
			return;
		}
	}

	private static void onRoad(ServerLevel level, ServerPlayer player, RandomSource random) {
		Vec3 ahead = player.getViewVector(1.0F).multiply(1.0, 0.0, 1.0);
		if (ahead.lengthSqr() < 1.0E-4) {
			return;
		}
		ahead = ahead.normalize();
		for (int tries = 0; tries < 12; tries++) {
			double distance = 40.0 + random.nextDouble() * 24.0;
			Vec3 at = player.position().add(ahead.yRot((float) ((random.nextDouble() - 0.5) * 0.5)).scale(distance));
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
			if (!level.isLoaded(top)) {
				continue;
			}
			Identifier below = BuiltInRegistries.BLOCK.getKey(level.getBlockState(top.below()).getBlock());
			if (!below.getNamespace().equals(Deadlands.MOD_ID) || !ROAD.contains(below.getPath())) {
				continue;
			}
			spawn(level, top, player, true);
			return;
		}
	}

	/** Road Ghost: on a town road at night he stands far ahead in the road, the screen tears, and he is gone (3 nights). */
	private static boolean roadGhost(ServerLevel level, ServerPlayer player, RandomSource random) {
		Identifier under = BuiltInRegistries.BLOCK.getKey(level.getBlockState(player.blockPosition().below()).getBlock());
		if (!ROAD.contains(under.getPath())) {
			return false; // only while they walk the road
		}
		Vec3 ahead = player.getViewVector(1.0F).multiply(1.0, 0.0, 1.0);
		if (ahead.lengthSqr() < 1.0E-4) {
			return false;
		}
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(player.position().add(ahead.normalize().scale(44.0))));
		if (!level.isLoaded(top) || !com.deadlands.horror.HorrorDirector.fire(player, "deadlands:dot_road_ghost", 72000L)) {
			return false;
		}
		DotEntity dot = DLDot.DOT.create(level, EntitySpawnReason.NATURAL);
		if (dot == null) {
			return false;
		}
		dot.snapTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, 0.0F, 0.0F);
		level.addFreshEntity(dot);
		dot.ghost(player, 30);
		return true;
	}

	private static void spawn(ServerLevel level, BlockPos at, ServerPlayer player, boolean standing) {
		DotEntity dot = DLDot.DOT.create(level, EntitySpawnReason.NATURAL);
		if (dot == null) {
			return;
		}
		dot.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		level.addFreshEntity(dot);
		if (standing) {
			dot.standInTheWay(player);
		}
	}
}
