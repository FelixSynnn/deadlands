package com.deadlands.progression;

import com.deadlands.Deadlands;
import com.deadlands.config.ConfigManager;
import com.deadlands.event.DayChangeCallback;
import com.deadlands.network.DLNetworking;
import com.deadlands.persistence.DeadlandsWorldData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Turns the overworld's time of day into DEADLANDS days.
 *
 * <p>Checked once per second. The processed day is saved in {@link DeadlandsWorldData}, so a day
 * change fires {@link DayChangeCallback} exactly once even across restarts, and sleeping or
 * {@code /time} jumps are handled (skipped days are reported, rewinds re-sync without firing).
 */
public final class WorldClock {
	private static final int CHECK_INTERVAL_TICKS = 20;
	public static final long TICKS_PER_DAY = 24_000L;

	private WorldClock() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_INTERVAL_TICKS == 0) {
				tick(server);
			}
		});
	}

	/** 1-based day number of the world right now. */
	public static long currentDay(MinecraftServer server) {
		return server.overworld().getDayCount() + 1;
	}

	/**
	 * Process a pending day change, if any. Public so commands and tests can force an immediate
	 * check after moving time. Returns true when a new day was announced.
	 */
	public static boolean tick(MinecraftServer server) {
		long dayIndex = server.overworld().getDayCount();
		DeadlandsWorldData data = DeadlandsWorldData.get(server);
		long last = data.lastProcessedDay();
		if (last == dayIndex) {
			return false;
		}
		data.setLastProcessedDay(dayIndex);
		if (last < 0) {
			Deadlands.LOGGER.info("World clock started on day {}", dayIndex + 1);
			return false;
		}
		if (dayIndex < last) {
			Deadlands.LOGGER.info("World clock moved back from day {} to day {}", last + 1, dayIndex + 1);
			DLNetworking.broadcastStatus(server);
			return false;
		}
		long previousDay = last + 1;
		long newDay = dayIndex + 1;
		long skipped = dayIndex - last - 1;
		Deadlands.LOGGER.info("Day {} begins{}", newDay, skipped > 0 ? " (" + skipped + " days skipped)" : "");
		DayChangeCallback.EVENT.invoker().onDayChanged(server, previousDay, newDay, skipped);
		if (ConfigManager.get().general.announceNewDays) {
			DLNetworking.broadcastDayBegin(server, newDay);
		}
		DLNetworking.broadcastStatus(server);
		return true;
	}

	/** Move every dimension's clock to the given time, as {@code /time set} does. */
	public static void setDayTime(MinecraftServer server, long dayTime) {
		for (ServerLevel level : server.getAllLevels()) {
			level.setDayTime(dayTime);
		}
	}
}
