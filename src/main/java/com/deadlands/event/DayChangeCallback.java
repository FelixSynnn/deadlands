package com.deadlands.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.MinecraftServer;

/**
 * Fired on the server thread exactly once when a new in-game day begins (6:00, {@code dayTime}
 * crossing a multiple of 24000). The world clock persists which day it processed, so restarting
 * the server never fires the same day twice.
 *
 * <p>Hordes (Milestone 4), bosses and radio broadcasts (Milestone 8) and settlement upkeep
 * (Milestone 7) hang off this event.
 */
@FunctionalInterface
public interface DayChangeCallback {
	Event<DayChangeCallback> EVENT = EventFactory.createArrayBacked(DayChangeCallback.class, listeners -> (server, previousDay, newDay, skippedDays) -> {
		for (DayChangeCallback listener : listeners) {
			listener.onDayChanged(server, previousDay, newDay, skippedDays);
		}
	});

	/**
	 * @param previousDay 1-based day that just ended
	 * @param newDay      1-based day that just began
	 * @param skippedDays whole days jumped over (for example by {@code /time add}); 0 normally
	 */
	void onDayChanged(MinecraftServer server, long previousDay, long newDay, long skippedDays);
}
