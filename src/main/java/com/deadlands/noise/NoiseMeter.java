package com.deadlands.noise;

import com.deadlands.network.NoisePayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * How loud each survivor is right now, for the HUD's noise meter. Two things feed it:
 *
 * <ul>
 *   <li><b>Noises they make</b> ({@link NoiseSystem#emit} with a survivor as the source): the level jumps to that noise's
 *       reach (a footstep's 6 blocks is a sliver of the bar, a gunshot fills it) and then dies away over about a second.</li>
 *   <li><b>How they move</b>: a floor under it while they're not still. Creeping barely registers, walking is a low hum,
 *       sprinting and swimming are louder.</li>
 * </ul>
 * A survivor is sent their level every other tick, but only when it has visibly changed.
 */
public final class NoiseMeter {
	/** The loudest reach a noise counts for on the bar: LOUD (48 blocks); gunshots and explosions fill it. */
	private static final double FULL = 48.0;
	private static final float DECAY_PER_STEP = 0.035F;

	private static final Map<UUID, Float> EVENT = new HashMap<>();
	private static final Map<UUID, Float> SENT = new HashMap<>();

	private NoiseMeter() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 2 == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					tick(player);
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			EVENT.clear();
			SENT.clear();
		});
	}

	private static void forget(UUID id) {
		EVENT.remove(id);
		SENT.remove(id);
	}

	/** A survivor made a noise of this loudness. */
	public static void record(ServerPlayer player, Loudness loudness) {
		double radius = loudness.radius();
		float level = (float) Mth.clamp(Math.pow(radius / FULL, 0.75), 0.0, 1.0);
		EVENT.merge(player.getUUID(), level, Math::max);
	}

	/** How loud simply moving is. */
	private static float baseline(ServerPlayer player) {
		Vec3 move = player.getDeltaMovement();
		double speed = Math.sqrt(move.x * move.x + move.z * move.z);
		if (player.isPassenger() || speed < 0.02 && !player.isSwimming()) {
			return 0.0F;
		}
		if (player.isSwimming()) {
			return 0.22F;
		}
		if (player.isShiftKeyDown()) {
			return 0.03F;
		}
		return player.isSprinting() ? 0.34F : 0.12F;
	}

	private static void tick(ServerPlayer player) {
		UUID id = player.getUUID();
		boolean listening = player.isAlive() && !player.isSpectator() && !player.isCreative();
		float event = Math.max(0.0F, EVENT.getOrDefault(id, 0.0F) - DECAY_PER_STEP);
		if (event > 0.0F) {
			EVENT.put(id, event);
		} else {
			EVENT.remove(id);
		}
		float level = listening ? Math.max(event, baseline(player)) : 0.0F;
		float sent = SENT.getOrDefault(id, -1.0F);
		if (Math.abs(level - sent) >= 0.03F || level == 0.0F && sent != 0.0F) {
			SENT.put(id, level);
			ServerPlayNetworking.send(player, new NoisePayload(level));
		}
	}
}
