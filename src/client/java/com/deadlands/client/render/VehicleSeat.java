package com.deadlands.client.render;

/**
 * Added to Minecraft's humanoid render state by a mixin: how someone sitting in a DEADLANDS vehicle
 * holds their arms. 0 not in a vehicle, 1 driving (hands on the wheel or bars), 2 riding along
 * (hands in the lap).
 */
public interface VehicleSeat {
	int deadlands$seat();

	void deadlands$setSeat(int seat);
}
