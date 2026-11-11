package com.deadlands.audio;

import com.deadlands.Deadlands;
import com.deadlands.config.ConfigManager;
import com.deadlands.entity.InfectedEntity;
import com.deadlands.network.ChaseMusicPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Decides when a survivor hears chase music (Audio Rules). Ordinary encounters never do: the music
 * starts only once at least {@code audio.chaseMusicThreshold} infected (8) within
 * {@value #RANGE} blocks have been targeting them for {@value #START_TICKS} ticks, and stops once
 * fewer than {@code audio.chaseMusicStopBelow} (5) have been for {@value #STOP_TICKS} ticks; the
 * client fades it out. Checked every {@value #CHECK_TICKS} ticks.
 */
public final class ChaseTracker {
	static final double RANGE = 48.0;
	static final int CHECK_TICKS = 10;
	static final int START_TICKS = 40;
	static final int STOP_TICKS = 200;

	private static final Map<UUID, State> STATES = new HashMap<>();

	private static final class State {
		boolean chased;
		int above;
		int below;
	}

	private ChaseTracker() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_TICKS == 3) {
				tick(server);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.getPlayer().getUUID()));
	}

	/** How many infected within range are targeting this survivor right now. */
	public static int chasers(ServerPlayer player) {
		return player.level().getEntitiesOfClass(InfectedEntity.class, player.getBoundingBox().inflate(RANGE),
			infected -> infected.isAlive() && infected.getTarget() == player && infected.distanceToSqr(player) <= RANGE * RANGE).size();
	}

	private static void tick(MinecraftServer server) {
		int start = Math.max(1, ConfigManager.get().audio.chaseMusicThreshold);
		int stopBelow = Math.max(1, Math.min(start, ConfigManager.get().audio.chaseMusicStopBelow));
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
			int count = player.isSpectator() || player.isCreative() ? 0 : chasers(player);
			state.above = count >= start ? state.above + CHECK_TICKS : 0;
			state.below = count < stopBelow ? state.below + CHECK_TICKS : 0;
			boolean chased = state.chased;
			if (!chased && state.above >= START_TICKS) {
				chased = true;
			} else if (chased && state.below >= STOP_TICKS) {
				chased = false;
			}
			if (chased != state.chased) {
				state.chased = chased;
				ServerPlayNetworking.send(player, new ChaseMusicPayload(chased));
				if (ConfigManager.get().general.debugLogging) {
					Deadlands.LOGGER.info("Chase music {} for {} ({} chasing)", chased ? "on" : "off", player.getScoreboardName(), count);
				}
			}
		}
	}

	/** For {@code /deadlands mobs}: whether a survivor currently has chase music. */
	public static boolean chased(ServerPlayer player) {
		State state = STATES.get(player.getUUID());
		return state != null && state.chased;
	}
}
