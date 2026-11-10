package com.deadlands.persistence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A town someone has found: its name, where it is, and who reached it first on which day. The rest
 * of a town's identity is derived from the world seed (see {@code TownIdentity}), so only discovery
 * needs saving.
 *
 * @param day 1-based day of the first visit
 */
public record TownRecord(String name, long day, String discoveredBy, int x, int z, String size, String condition) {
	public static final Codec<TownRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("name").forGetter(TownRecord::name),
		Codec.LONG.fieldOf("day").forGetter(TownRecord::day),
		Codec.STRING.optionalFieldOf("by", "").forGetter(TownRecord::discoveredBy),
		Codec.INT.fieldOf("x").forGetter(TownRecord::x),
		Codec.INT.fieldOf("z").forGetter(TownRecord::z),
		Codec.STRING.optionalFieldOf("size", "").forGetter(TownRecord::size),
		Codec.STRING.optionalFieldOf("condition", "").forGetter(TownRecord::condition)
	).apply(instance, TownRecord::new));
}
