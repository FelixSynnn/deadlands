package com.deadlands.vehicle;

import com.deadlands.registry.DLVehicles;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Abandoned vehicles for the world generator. Which kinds turn up is weighted: ordinary cars
 * and pickups are common, service and heavy vehicles rare, and each is left in a random state of
 * disrepair ({@link VehicleEntity#abandon}).
 */
public final class VehicleSpawns {
	private static final VehicleKind[] KINDS = VehicleKind.values();
	//                                     sedan pickup suv jeep ambulance police van bus fire cargo bike tractor
	private static final int[] ROAD_WEIGHTS = {26, 18, 16, 4, 3, 5, 8, 3, 2, 6, 6, 3};

	private VehicleSpawns() {
	}

	public static VehicleKind pick(RandomSource random) {
		int total = 0;
		for (int weight : ROAD_WEIGHTS) {
			total += weight;
		}
		int roll = random.nextInt(total);
		for (int i = 0; i < KINDS.length; i++) {
			roll -= ROAD_WEIGHTS[i];
			if (roll < 0) {
				return KINDS[i];
			}
		}
		return VehicleKind.SEDAN;
	}

	/** Put an abandoned vehicle at a position (its feet), facing {@code yaw}. Returns it, or null if it could not be created. */
	public static VehicleEntity abandon(ServerLevelAccessor level, VehicleKind kind, double x, double y, double z, float yaw, RandomSource random) {
		VehicleEntity vehicle = DLVehicles.type(kind).create(level.getLevel(), EntitySpawnReason.STRUCTURE);
		if (vehicle == null) {
			return null;
		}
		vehicle.snapTo(x, y, z, yaw, 0.0F);
		vehicle.abandon(random);
		level.addFreshEntity(vehicle);
		return vehicle;
	}
}
