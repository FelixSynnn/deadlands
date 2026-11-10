package com.deadlands.persistence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A group of infected migrating toward a town. While nobody is near it only exists as this
 * record (position, destination, how many are left), moving a little every second; near a
 * survivor it becomes real infected, and turns back into a record when everyone has left.
 */
public record MigrationRecord(int id, double x, double z, double destX, double destZ, int count, long created) {
	public static final Codec<MigrationRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.INT.fieldOf("id").forGetter(MigrationRecord::id),
		Codec.DOUBLE.fieldOf("x").forGetter(MigrationRecord::x),
		Codec.DOUBLE.fieldOf("z").forGetter(MigrationRecord::z),
		Codec.DOUBLE.fieldOf("dest_x").forGetter(MigrationRecord::destX),
		Codec.DOUBLE.fieldOf("dest_z").forGetter(MigrationRecord::destZ),
		Codec.INT.fieldOf("count").forGetter(MigrationRecord::count),
		Codec.LONG.optionalFieldOf("created", 0L).forGetter(MigrationRecord::created)
	).apply(instance, MigrationRecord::new));

	public MigrationRecord withPosition(double newX, double newZ) {
		return new MigrationRecord(id, newX, newZ, destX, destZ, count, created);
	}

	public MigrationRecord withCount(int newCount) {
		return new MigrationRecord(id, x, z, destX, destZ, Math.max(0, newCount), created);
	}

	public double distanceToDestination() {
		double dx = destX - x;
		double dz = destZ - z;
		return Math.sqrt(dx * dx + dz * dz);
	}
}
