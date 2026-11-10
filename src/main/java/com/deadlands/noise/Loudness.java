package com.deadlands.noise;

import com.deadlands.config.ConfigManager;

/**
 * How loud a noise is. Each level has a base hearing radius in blocks; each infected kind scales
 * it by its own hearing, and every solid block in the way shortens it (see NoiseSystem.muffling).
 *
 * <p>Gunfire uses the {@code zombies.gunshotHearingRange} setting. DEADLANDS has no firearms yet
 * (weapons arrive with the survival systems), so for now it is only emitted through
 * {@link NoiseSystem#emit} by code and the {@code /deadlands noise} command.
 */
public enum Loudness {
	/** Walking, swimming, gliding. */
	QUIET(6),
	/** Running, landing from a jump, opening doors and containers, placing blocks, fighting. */
	LOW(12),
	/** Breaking blocks, firing a bow, splashing into water. */
	MODERATE(22),
	/** Music, lightning, a Screamer's scream. */
	LOUD(48),
	/** Gunfire. */
	GUNSHOT(-1),
	/** Explosions. */
	EXPLOSION(-2);

	private final int baseRadius;

	Loudness(int baseRadius) {
		this.baseRadius = baseRadius;
	}

	/** Hearing radius in blocks, reading the current config for gunfire and explosions. */
	public double radius() {
		return switch (this) {
			case GUNSHOT -> ConfigManager.get().zombies.gunshotHearingRange;
			case EXPLOSION -> Math.max(96.0, ConfigManager.get().zombies.gunshotHearingRange * 1.5);
			default -> baseRadius;
		};
	}

	/** Higher levels interrupt investigations of lower ones. */
	public int priority() {
		return ordinal() + 1;
	}

	/** How long (ticks) an infected keeps investigating a noise of this level. */
	public int attentionTicks() {
		return 300 + ordinal() * 120;
	}
}
