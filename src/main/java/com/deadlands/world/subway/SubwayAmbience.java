package com.deadlands.world.subway;

import com.deadlands.Deadlands;
import com.deadlands.registry.DLSounds;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;

/**
 * Sound in the abandoned subway ({@code deadlands:subway_station}), for anyone inside a station or its tunnels.
 *
 * <ul>
 *   <li><b>The PA</b>: every two to five minutes a loudspeaker crackles into life somewhere along the station
 *       (from a point near the ceiling on the hall's long axis, so it comes from the speakers, not from you).
 *       Each station has its own three announcements, picked from the eight by its position; one station in
 *       four has a dead PA that only hisses. Some announcements cut off mid-sentence (the files do).</li>
 *   <li><b>The occupied dark</b>: every 25 to 70 seconds, something somewhere else in the station: a groan
 *       from a tunnel end, shuffling, a bang of metal behind a wall. Always 14 or more blocks away, out at the
 *       tunnel ends or behind the rooms, at a natural volume; never the same sound twice running; most of
 *       them mean nothing is coming.</li>
 * </ul>
 * One check a second per survivor; nothing is spawned.
 */
public final class SubwayAmbience {
	public static final ResourceKey<Structure> STATION = ResourceKey.create(Registries.STRUCTURE, Deadlands.id("subway_station"));
	private static final List<String> ANNOUNCEMENTS = List.of("pa_evacuate_1", "pa_evacuate_2", "pa_missing", "pa_doors", "pa_north",
		"pa_checks", "pa_lights", "pa_sorry");
	private static final List<String> STATIC = List.of("pa_static_1", "pa_static_2", "pa_static_3");

	private static final Map<UUID, Long> NEXT_PA = new HashMap<>();
	private static final Map<UUID, Long> NEXT_NOISE = new HashMap<>();
	private static final Map<UUID, Integer> LAST_NOISE = new HashMap<>();

	private SubwayAmbience() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 9) {
				ServerLevel level = server.overworld();
				for (ServerPlayer player : level.players()) {
					if (!player.isSpectator()) {
						tick(level, player);
					}
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUUID();
			NEXT_PA.remove(id);
			NEXT_NOISE.remove(id);
			LAST_NOISE.remove(id);
		});
	}

	/** The station this position is in (its hall, rooms or tunnels), or null. */
	public static StructureStart stationAt(ServerLevel level, BlockPos pos) {
		Structure station = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(STATION);
		if (station == null) {
			return StructureStart.INVALID_START;
		}
		return level.structureManager().getStructureWithPieceAt(pos, station);
	}

	private static void tick(ServerLevel level, ServerPlayer player) {
		StructureStart start = stationAt(level, player.blockPosition());
		if (!start.isValid()) {
			return;
		}
		RandomSource random = player.getRandom();
		long now = level.getGameTime();
		UUID id = player.getUUID();
		BoundingBox box = start.getBoundingBox();
		long stationSeed = Mth.getSeed(box.minX(), 0, box.minZ());
		if (now >= NEXT_PA.computeIfAbsent(id, k -> now + 400 + random.nextInt(1200))) {
			NEXT_PA.put(id, now + 2400 + random.nextInt(3600)); // two to five minutes
			announce(level, player, box, stationSeed, random);
		}
		if (now >= NEXT_NOISE.computeIfAbsent(id, k -> now + 300 + random.nextInt(600))) {
			NEXT_NOISE.put(id, now + 500 + random.nextInt(900)); // 25 to 70 seconds
			noise(level, player, box, random);
		}
	}

	/** The hall's long axis: x for an unrotated station, z if it was turned. */
	private static boolean alongX(BoundingBox box) {
		return box.getXSpan() >= box.getZSpan();
	}

	private static Vec3 alongAxis(BoundingBox box, double t, double y) {
		double cx = (box.minX() + box.maxX() + 1) / 2.0;
		double cz = (box.minZ() + box.maxZ() + 1) / 2.0;
		return alongX(box) ? new Vec3(Mth.lerp(t, box.minX(), box.maxX()), y, cz) : new Vec3(cx, y, Mth.lerp(t, box.minZ(), box.maxZ()));
	}

	private static void announce(ServerLevel level, ServerPlayer player, BoundingBox box, long stationSeed, RandomSource random) {
		boolean dead = Math.floorMod(stationSeed, 4L) == 0; // one station in four: the PA only hisses
		String name;
		if (dead || random.nextInt(6) == 0) {
			name = STATIC.get(random.nextInt(STATIC.size()));
		} else {
			// This station's three announcements.
			int pick = random.nextInt(3);
			int index = (int) Math.floorMod(stationSeed + pick * 3L, (long) ANNOUNCEMENTS.size());
			name = ANNOUNCEMENTS.get(index);
		}
		// The speaker nearest them of the three along the hall, up by the ceiling.
		Vec3 best = null;
		double bestDistance = Double.MAX_VALUE;
		for (double t : new double[] {0.3, 0.5, 0.7}) {
			Vec3 speaker = alongAxis(box, t, box.minY() + 7.0);
			double d = speaker.distanceToSqr(player.position());
			if (d < bestDistance) {
				bestDistance = d;
				best = speaker;
			}
		}
		play(level, best, name, 2.4F, 1.0F);
	}

	private static void noise(ServerLevel level, ServerPlayer player, BoundingBox box, RandomSource random) {
		Vec3 at = null;
		for (int tries = 0; tries < 6 && at == null; tries++) {
			// The tunnel ends, or behind the rooms' far wall.
			double t = random.nextBoolean() ? random.nextDouble() * 0.12 : 0.88 + random.nextDouble() * 0.12;
			Vec3 spot = random.nextInt(4) == 0 ? alongAxis(box, random.nextDouble(), box.minY() + 3.0) : alongAxis(box, t, box.minY() + 3.0);
			if (spot.distanceTo(player.position()) >= 14.0) {
				at = spot;
			}
		}
		if (at == null) {
			return;
		}
		int last = LAST_NOISE.getOrDefault(player.getUUID(), -1);
		int kind;
		do {
			kind = random.nextInt(6);
		} while (kind == last);
		LAST_NOISE.put(player.getUUID(), kind);
		SoundEvent sound = switch (kind) {
			case 0 -> DLSounds.INFECTED_WALKER_AMBIENT.value();
			case 1 -> DLSounds.INFECTED_CRAWLER_AMBIENT.value();
			case 2 -> DLSounds.INFECTED_BRUTE_STEP.value();
			case 3 -> DLSounds.GUEST_KNOCK.value(); // a bang on a door somewhere
			case 4 -> DLSounds.GUEST_TEAR.value(); // metal, wrenched
			default -> DLSounds.INFECTED_RUNNER_AMBIENT.value();
		};
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, 0.7F + random.nextFloat() * 0.6F, 0.8F + random.nextFloat() * 0.2F);
	}

	private static void play(ServerLevel level, Vec3 at, String name, float volume, float pitch) {
		SoundEvent event = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(Deadlands.MOD_ID, "subway." + name));
		level.playSound(null, at.x, at.y, at.z, event, SoundSource.AMBIENT, volume, pitch);
	}
}
