package com.deadlands.noise;

import com.deadlands.entity.InfectedEntity;
import com.deadlands.nest.NestRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Turns what survivors do into noise the infected and their nests can hear.
 *
 * <p>Most noise comes from Minecraft's own game events (the same events sculk sensors hear),
 * forwarded by {@code ServerLevelMixin}: footsteps, doors, containers, breaking and placing blocks,
 * bows, splashes, music, explosions and lightning. Crouching survivors make no footstep noise.
 * Movement noise is rate-limited per survivor so a running player does not flood the system.
 *
 * <p>A noise is delivered once, when it happens: every infected within its hearing range is told
 * ({@link InfectedEntity#hearNoise}) and decides for itself whether to investigate, and loaded
 * nests nearby may wake ({@link NestRegistry#onNoise}). Only survivor-made noise and explosions
 * can wake a nest, so the infected can never set off an endless chain of releases.
 */
public final class NoiseSystem {
	/** Share of a noise's reach each solid block in the way takes. */
	public static final double WALL_LOSS = 0.2;
	/** Walls never cut a noise's reach below this share: a gunshot is heard through a house. */
	public static final double MIN_MUFFLING = 0.3;
	private static final int MAX_WALL_SAMPLES = 48;
	private static final int QUIET_STEP_INTERVAL = 12;
	private static final int LOUD_STEP_INTERVAL = 6;
	private static final double MAX_VERTICAL_REACH = 24.0;
	private static final float BEST_HEARING = 1.5F;

	/** Game time of each survivor's last movement noise. */
	private static final Map<UUID, Long> LAST_MOVEMENT = new HashMap<>();

	private NoiseSystem() {
	}

	public static void init() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_MOVEMENT.remove(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_MOVEMENT.clear());
	}

	/** Called for every game event in a server level (see {@code ServerLevelMixin}). Must stay cheap. */
	public static void onGameEvent(ServerLevel level, Holder<GameEvent> event, Vec3 pos, GameEvent.Context context) {
		Entity source = context.sourceEntity();
		if (event == GameEvent.EXPLODE) {
			emit(level, pos, Loudness.EXPLOSION, source, true);
			return;
		}
		if (event == GameEvent.LIGHTNING_STRIKE) {
			emit(level, pos, Loudness.LOUD, null, false);
			return;
		}
		Player player = noisySurvivor(source);
		if (player == null) {
			// The infected hear each other tearing through barricades, but nests ignore it.
			if (source instanceof InfectedEntity && event == GameEvent.BLOCK_DESTROY) {
				emit(level, pos, Loudness.LOW, source, false);
			}
			return;
		}

		Loudness loudness;
		boolean movement = false;
		if (event == GameEvent.STEP) {
			if (player.isShiftKeyDown()) {
				return;
			}
			loudness = player.isSprinting() ? Loudness.LOW : Loudness.QUIET;
			movement = true;
		} else if (event == GameEvent.HIT_GROUND) {
			loudness = Loudness.LOW;
			movement = true;
		} else if (event == GameEvent.SWIM || event == GameEvent.ELYTRA_GLIDE) {
			loudness = Loudness.QUIET;
			movement = true;
		} else if (event == GameEvent.SPLASH || event == GameEvent.BLOCK_DESTROY || event == GameEvent.PROJECTILE_SHOOT) {
			loudness = Loudness.MODERATE;
		} else if (event == GameEvent.BLOCK_OPEN || event == GameEvent.BLOCK_CLOSE || event == GameEvent.CONTAINER_OPEN
			|| event == GameEvent.CONTAINER_CLOSE || event == GameEvent.BLOCK_PLACE || event == GameEvent.ENTITY_DAMAGE
			|| event == GameEvent.PRIME_FUSE) {
			loudness = Loudness.LOW;
		} else if (event == GameEvent.NOTE_BLOCK_PLAY || event == GameEvent.JUKEBOX_PLAY || event == GameEvent.INSTRUMENT_PLAY) {
			loudness = Loudness.LOUD;
		} else {
			return;
		}
		if (movement && !movementNoiseDue(level, player, loudness)) {
			return;
		}
		emit(level, pos, loudness, player, true);
	}

	/**
	 * Deliver a noise at {@code pos}. {@code byThreat} is true for noise made by survivors (and
	 * explosions); only that kind can wake nests.
	 */
	public static void emit(ServerLevel level, Vec3 pos, Loudness loudness, @Nullable Entity source, boolean byThreat) {
		double radius = loudness.radius();
		if (radius <= 0.0) {
			return;
		}
		if (source instanceof net.minecraft.server.level.ServerPlayer survivor) {
			NoiseMeter.record(survivor, loudness); // the HUD's noise meter
		}
		double reach = radius * BEST_HEARING;
		AABB area = AABB.ofSize(pos, reach * 2.0, Math.min(reach, MAX_VERTICAL_REACH) * 2.0, reach * 2.0);
		for (InfectedEntity infected : level.getEntitiesOfClass(InfectedEntity.class, area, InfectedEntity::isAlive)) {
			if (infected == source) {
				continue;
			}
			double heard = radius * infected.kind().hearing();
			double distanceSqr = infected.distanceToSqr(pos);
			if (distanceSqr > heard * heard) {
				continue;
			}
			// Only those in open range pay for the wall check.
			heard *= muffling(level, pos.add(0.0, 0.5, 0.0), infected.getEyePosition());
			if (distanceSqr <= heard * heard) {
				infected.hearNoise(pos, loudness, byThreat, Math.sqrt(distanceSqr));
			}
		}
		if (byThreat) {
			NestRegistry.onNoise(level, pos, loudness, radius);
		}
	}

	/**
	 * How much of a noise's reach survives the walls between two points: 1 in the open, less for
	 * every solid block on the straight line between them (each takes {@link #WALL_LOSS}), never
	 * below {@link #MIN_MUFFLING}. Sampled once per block, at most {@link #MAX_WALL_SAMPLES}
	 * times, and only in loaded chunks.
	 */
	public static double muffling(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 line = to.subtract(from);
		double length = line.length();
		if (length < 1.5) {
			return 1.0;
		}
		int steps = (int) Math.min(MAX_WALL_SAMPLES, Math.floor(length));
		Vec3 step = line.scale(1.0 / (steps + 1));
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		double kept = 1.0;
		long last = Long.MIN_VALUE;
		for (int i = 1; i <= steps; i++) {
			cursor.set(from.x + step.x * i, from.y + step.y * i, from.z + step.z * i);
			long key = cursor.asLong();
			if (key == last || !level.isLoaded(cursor)) {
				continue;
			}
			last = key;
			if (level.getBlockState(cursor).isCollisionShapeFullBlock(level, cursor)) {
				kept *= 1.0 - WALL_LOSS;
				if (kept <= MIN_MUFFLING) {
					return MIN_MUFFLING;
				}
			}
		}
		return kept;
	}

	private static @Nullable Player noisySurvivor(@Nullable Entity source) {
		if (source instanceof Player player && !player.isSpectator() && !player.isCreative()) {
			return player;
		}
		return null;
	}

	private static boolean movementNoiseDue(ServerLevel level, Player player, Loudness loudness) {
		long now = level.getGameTime();
		int interval = loudness == Loudness.QUIET ? QUIET_STEP_INTERVAL : LOUD_STEP_INTERVAL;
		Long last = LAST_MOVEMENT.get(player.getUUID());
		if (last != null && now - last < interval && now >= last) {
			return false;
		}
		LAST_MOVEMENT.put(player.getUUID(), now);
		return true;
	}
}
