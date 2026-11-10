package com.deadlands.world.town;

/**
 * How big a town is. The radius is how far its main streets run from the centre; the gate (where
 * a road from another town arrives) sits just beyond it. Large towns add side streets; major
 * cities lay out a grid of them, and highways link them to other cities and large towns.
 */
public enum TownSize {
	SETTLEMENT("settlement", 44, false, 5, 12, 45),
	SMALL_TOWN("small_town", 68, false, 15, 30, 38),
	LARGE_TOWN("large_town", 96, true, 30, 60, 17),
	CITY("city", 104, true, 60, 80, 7);

	private final String id;
	private final int radius;
	private final boolean sideStreets;
	private final int minBuildings;
	private final int maxBuildings;
	private final int weight;

	TownSize(String id, int radius, boolean sideStreets, int minBuildings, int maxBuildings, int weight) {
		this.id = id;
		this.radius = radius;
		this.sideStreets = sideStreets;
		this.minBuildings = minBuildings;
		this.maxBuildings = maxBuildings;
		this.weight = weight;
	}

	public String id() {
		return id;
	}

	public int radius() {
		return radius;
	}

	/** Distance from the centre to a gate, where the town's main street meets the road network. */
	public int gateRadius() {
		return radius + 4;
	}

	public boolean sideStreets() {
		return sideStreets;
	}

	/** Large towns and cities: linked to each other by highways rather than ordinary roads. */
	public boolean major() {
		return this == LARGE_TOWN || this == CITY;
	}

	public int minBuildings() {
		return minBuildings;
	}

	public int maxBuildings() {
		return maxBuildings;
	}

	/** Deterministic size for the town founded at a site. */
	public static TownSize pick(long hash) {
		int total = 0;
		for (TownSize size : values()) {
			total += size.weight;
		}
		int roll = (int) Math.floorMod(hash, (long) total);
		for (TownSize size : values()) {
			roll -= size.weight;
			if (roll < 0) {
				return size;
			}
		}
		return SETTLEMENT;
	}
}
