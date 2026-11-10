package com.deadlands.persistence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The current (or last) horde night, saved so a horde survives a restart: which day it belongs
 * to, how big it is, how many have been sent and killed, the side it comes from, and whether it
 * is still going.
 */
public record HordeRecord(long day, int total, int spawned, int killed, float bearing, boolean active) {
	public static final Codec<HordeRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.LONG.fieldOf("day").forGetter(HordeRecord::day),
		Codec.INT.fieldOf("total").forGetter(HordeRecord::total),
		Codec.INT.optionalFieldOf("spawned", 0).forGetter(HordeRecord::spawned),
		Codec.INT.optionalFieldOf("killed", 0).forGetter(HordeRecord::killed),
		Codec.FLOAT.optionalFieldOf("bearing", 0.0F).forGetter(HordeRecord::bearing),
		Codec.BOOL.optionalFieldOf("active", false).forGetter(HordeRecord::active)
	).apply(instance, HordeRecord::new));

	public static HordeRecord begin(long day, int total, float bearing) {
		return new HordeRecord(day, total, 0, 0, bearing, true);
	}

	public HordeRecord withSpawned(int value) {
		return new HordeRecord(day, total, value, killed, bearing, active);
	}

	public HordeRecord withKilled(int value) {
		return new HordeRecord(day, total, spawned, value, bearing, active);
	}

	public HordeRecord ended() {
		return new HordeRecord(day, total, spawned, killed, bearing, false);
	}

	/** Infected still to beat: alive members plus those not sent yet. */
	public int remaining() {
		return Math.max(0, total - killed);
	}
}
