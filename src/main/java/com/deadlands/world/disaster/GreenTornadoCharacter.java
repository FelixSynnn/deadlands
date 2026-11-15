package com.deadlands.world.disaster;

import com.deadlands.Deadlands;
import com.deadlands.config.ConfigManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * PLACEHOLDER: the custom character tied to the Green Tornado ({@link Disaster#GREEN_TORNADO}).
 *
 * <p>The owner is sending this character's design separately; nothing about it is decided here. The
 * disaster already calls these three hooks, so the character only has to be filled in:
 * <ul>
 *   <li>{@link #arrive}: the tornado touches down (its funnel's position, at the ground);</li>
 *   <li>{@link #tick}: every disaster tick while it is on the ground (the funnel's position and strength, 0 to 1);</li>
 *   <li>{@link #leave}: the tornado has passed.</li>
 * </ul>
 * Switch: {@code disasters.greenTornadoCharacter}.
 */
public final class GreenTornadoCharacter {
	private GreenTornadoCharacter() {
	}

	private static boolean on() {
		return ConfigManager.get().disasters.greenTornadoCharacter;
	}

	public static void arrive(ServerLevel level, Vec3 funnel) {
		if (on()) {
			Deadlands.LOGGER.debug("DEADLANDS: the Green Tornado touched down at {} (its character is not in yet)", funnel);
		}
	}

	public static void tick(ServerLevel level, Vec3 funnel, float strength) {
		// The character goes here.
	}

	public static void leave(ServerLevel level) {
		// The character goes here.
	}
}
