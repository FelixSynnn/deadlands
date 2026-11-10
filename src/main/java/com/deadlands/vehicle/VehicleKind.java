package com.deadlands.vehicle;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

/**
 * The twelve vehicles and everything that differs between them. Speeds are blocks per tick on a
 * good road (0.5 is 10 blocks a second; a sprinting survivor does 5.6); off the road every vehicle
 * is slower, the motorcycle and the tractor least of all. Fuel use is litres per 100 blocks.
 *
 * <p>Models and textures come from tools/assetgen/gen_vehicles.py, which takes the same body
 * dimensions (in model pixels) from its own table: keep the two in step.
 */
public enum VehicleKind {
	//            id               width height seats rows  speed  accel   turn  tank  use   body offroad
	SEDAN("sedan", 1.9F, 1.5F, 4, 2, 0.62F, 0.016F, 3.2F, 50.0F, 1.6F, 120.0F, 0.6F),
	PICKUP("pickup", 2.0F, 1.8F, 2, 4, 0.56F, 0.014F, 3.0F, 70.0F, 2.2F, 150.0F, 0.75F),
	SUV("suv", 2.0F, 1.9F, 4, 3, 0.56F, 0.014F, 3.0F, 65.0F, 2.2F, 150.0F, 0.75F),
	MILITARY_JEEP("military_jeep", 1.9F, 1.8F, 4, 2, 0.54F, 0.016F, 3.4F, 60.0F, 2.4F, 190.0F, 0.9F),
	AMBULANCE("ambulance", 2.1F, 2.4F, 3, 4, 0.52F, 0.012F, 2.6F, 70.0F, 2.6F, 160.0F, 0.6F),
	POLICE_CAR("police_car", 1.9F, 1.6F, 4, 2, 0.68F, 0.019F, 3.4F, 55.0F, 1.9F, 130.0F, 0.6F),
	DELIVERY_VAN("delivery_van", 2.1F, 2.4F, 2, 6, 0.48F, 0.011F, 2.5F, 70.0F, 2.6F, 150.0F, 0.6F),
	BUS("bus", 2.4F, 2.8F, 10, 3, 0.42F, 0.008F, 1.8F, 200.0F, 5.0F, 240.0F, 0.5F),
	FIRE_TRUCK("fire_truck", 2.4F, 2.8F, 4, 4, 0.46F, 0.009F, 2.0F, 180.0F, 5.0F, 280.0F, 0.6F),
	CARGO_TRUCK("cargo_truck", 2.4F, 2.9F, 2, 6, 0.44F, 0.008F, 1.9F, 220.0F, 5.5F, 260.0F, 0.6F),
	MOTORCYCLE("motorcycle", 0.9F, 1.3F, 2, 1, 0.72F, 0.024F, 4.2F, 16.0F, 0.8F, 60.0F, 0.85F),
	TRACTOR("tractor", 1.8F, 2.2F, 1, 1, 0.3F, 0.008F, 2.6F, 90.0F, 3.0F, 200.0F, 1.0F);

	private static final Map<EntityType<?>, VehicleKind> BY_TYPE = new IdentityHashMap<>();

	private final String id;
	private final float width;
	private final float height;
	private final int seats;
	private final int storageRows;
	private final float maxSpeed;
	private final float acceleration;
	private final float turnRate;
	private final float tank;
	private final float fuelUse;
	private final float bodyStrength;
	private final float offRoad;

	VehicleKind(String id, float width, float height, int seats, int storageRows, float maxSpeed, float acceleration, float turnRate, float tank,
		float fuelUse, float bodyStrength, float offRoad) {
		this.id = id;
		this.width = width;
		this.height = height;
		this.seats = seats;
		this.storageRows = storageRows;
		this.maxSpeed = maxSpeed;
		this.acceleration = acceleration;
		this.turnRate = turnRate;
		this.tank = tank;
		this.fuelUse = fuelUse;
		this.bodyStrength = bodyStrength;
		this.offRoad = offRoad;
	}

	public String id() {
		return id;
	}

	/** Hitbox width (a square footprint: long vehicles are longer than their box). */
	public float width() {
		return width;
	}

	public float height() {
		return height;
	}

	public int seats() {
		return seats;
	}

	/** Rows of nine storage slots. */
	public int storageRows() {
		return storageRows;
	}

	/** Top speed on a road, blocks per tick. */
	public float maxSpeed() {
		return maxSpeed;
	}

	public float acceleration() {
		return acceleration;
	}

	/** Degrees per tick at full lock. */
	public float turnRate() {
		return turnRate;
	}

	/** Tank size in litres. */
	public float tank() {
		return tank;
	}

	/** Litres per 100 blocks. */
	public float fuelUse() {
		return fuelUse;
	}

	/** How much damage the body takes before the vehicle is a wreck. */
	public float bodyStrength() {
		return bodyStrength;
	}

	/** Share of the road speed it keeps off the road. */
	public float offRoad() {
		return offRoad;
	}

	public String translationKey() {
		return "entity.deadlands." + id;
	}

	/**
	 * Where seat {@code index} is, relative to the vehicle's centre: x to the left (the driver sits
	 * on the left), y up from the floor, z forward. Rows of two from the front; single-file on the
	 * motorcycle and tractor.
	 */
	public float[] seat(int index) {
		if (this == MOTORCYCLE || this == TRACTOR) {
			return new float[] {0.0F, this == TRACTOR ? 1.0F : 0.55F, 0.1F - index * 0.7F};
		}
		int row = index / 2;
		float side = index % 2 == 0 ? 0.42F : -0.42F;
		float front = this == BUS ? 2.2F : 0.35F;
		float pitch = this == BUS ? 1.0F : 1.05F;
		float y = height > 2.0F ? 0.85F : 0.45F;
		return new float[] {side, y, front - row * pitch};
	}

	public static void bind(VehicleKind kind, EntityType<?> type) {
		BY_TYPE.put(type, kind);
	}

	public static @Nullable VehicleKind of(EntityType<?> type) {
		return BY_TYPE.get(type);
	}
}
