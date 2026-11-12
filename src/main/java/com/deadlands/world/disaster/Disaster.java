package com.deadlands.world.disaster;

import com.deadlands.config.DeadlandsConfig;
import com.deadlands.world.weather.WeatherDirector;
import org.jspecify.annotations.Nullable;

/**
 * DEADLANDS' disaster weather (Phase 5). Original events: each has a warning lead time before it
 * starts, a duration range, a rarity weight and its own setting in the {@code disasters} config
 * section. Anything that changes the world (blocks broken, water or snow placed, fires) only happens
 * with {@code disasters.destructive} on, which is off by default.
 *
 * <p>Append only: the saved state stores the index.
 */
public enum Disaster {
	/** A twister that wanders across the land, dragging and lifting anything near it. */
	TWISTER("twister", 30, 60, 120, 9, WeatherDirector.Kind.WIND),
	/** Torrential rain; low ground runs with water that drags and slows you. */
	FLASH_FLOOD("flash_flood", 40, 120, 240, 11, WeatherDirector.Kind.HEAVY_RAIN),
	/** The ground shakes in waves; you stumble and dust falls. */
	TREMOR("tremor", 20, 30, 70, 9, null),
	/** Burning rocks fall from the sky around survivors. */
	SKYFALL("skyfall", 30, 45, 90, 5, null),
	/** A freezing whiteout: you can barely see, and the cold gets into you outside. */
	WHITEOUT("whiteout", 40, 120, 240, 9, WeatherDirector.Kind.FOG),
	/** A wall of dust: low visibility, choking air, hard going. */
	DUST_WALL("dust_wall", 40, 90, 200, 10, WeatherDirector.Kind.DUST),
	/** Rain that burns: stay under cover or wear a helmet. */
	CAUSTIC_RAIN("caustic_rain", 40, 90, 180, 8, WeatherDirector.Kind.HEAVY_RAIN),
	/** A lightning storm that strikes close and often. */
	STATIC_STORM("static_storm", 30, 90, 180, 12, null);

	private final String id;
	private final int warnSeconds;
	private final int minSeconds;
	private final int maxSeconds;
	private final int weight;
	private final WeatherDirector.@Nullable Kind sky;

	Disaster(String id, int warnSeconds, int minSeconds, int maxSeconds, int weight, WeatherDirector.@Nullable Kind sky) {
		this.id = id;
		this.warnSeconds = warnSeconds;
		this.minSeconds = minSeconds;
		this.maxSeconds = maxSeconds;
		this.weight = weight;
		this.sky = sky;
	}

	public String id() {
		return id;
	}

	/** Seconds of warning before it starts. */
	public int warnSeconds() {
		return warnSeconds;
	}

	public int minSeconds() {
		return minSeconds;
	}

	public int maxSeconds() {
		return maxSeconds;
	}

	/** How likely it is to be the one picked, against the others' weights (rarity). */
	public int weight() {
		return weight;
	}

	/** The DEADLANDS weather it brings with it for its looks (fog, dust, rain), or null. */
	public WeatherDirector.@Nullable Kind sky() {
		return sky;
	}

	public boolean enabled(DeadlandsConfig.Disasters config) {
		return switch (this) {
			case TWISTER -> config.twister;
			case FLASH_FLOOD -> config.flashFlood;
			case TREMOR -> config.tremor;
			case SKYFALL -> config.skyfall;
			case WHITEOUT -> config.whiteout;
			case DUST_WALL -> config.dustWall;
			case CAUSTIC_RAIN -> config.causticRain;
			case STATIC_STORM -> config.staticStorm;
		};
	}

	public static @Nullable Disaster byIndex(int index) {
		Disaster[] all = values();
		return index >= 0 && index < all.length ? all[index] : null;
	}
}
