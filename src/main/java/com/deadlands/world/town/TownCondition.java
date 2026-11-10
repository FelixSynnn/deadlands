package com.deadlands.world.town;

/**
 * What happened to a town during the outbreak. The condition decides how damaged its buildings
 * are, what blocks its gates, how much blood is on its streets and how much loot is left.
 */
public enum TownCondition {
	EVACUATED("evacuated", 22, 0.15, 0.25, 1.1, Gate.EVACUATION),
	OVERRUN("overrun", 30, 0.45, 0.8, 0.9, Gate.NONE),
	MILITARY("military", 10, 0.2, 0.35, 1.3, Gate.CHECKPOINT),
	SURVIVOR("survivor", 10, 0.1, 0.15, 0.7, Gate.BARRICADE),
	BANDIT("bandit", 10, 0.3, 0.45, 0.8, Gate.BARRICADE),
	ABANDONED("abandoned", 18, 0.35, 0.3, 1.0, Gate.NONE);

	/** What a town's condition puts across its gates. */
	public enum Gate {
		NONE, EVACUATION, CHECKPOINT, BARRICADE
	}

	private final String id;
	private final int weight;
	private final double damage;
	private final double blood;
	private final double lootMultiplier;
	private final Gate gate;

	TownCondition(String id, int weight, double damage, double blood, double lootMultiplier, Gate gate) {
		this.id = id;
		this.weight = weight;
		this.damage = damage;
		this.blood = blood;
		this.lootMultiplier = lootMultiplier;
		this.gate = gate;
	}

	public String id() {
		return id;
	}

	public String translationKey() {
		return "town.deadlands.condition." + id;
	}

	/** Share of buildings and street tiles that are wrecked (0..1). */
	public double damage() {
		return damage;
	}

	/** How much blood and violence the streets show (0..1). */
	public double blood() {
		return blood;
	}

	public double lootMultiplier() {
		return lootMultiplier;
	}

	public Gate gate() {
		return gate;
	}

	public static TownCondition pick(long hash) {
		int total = 0;
		for (TownCondition condition : values()) {
			total += condition.weight;
		}
		int roll = (int) Math.floorMod(hash, (long) total);
		for (TownCondition condition : values()) {
			roll -= condition.weight;
			if (roll < 0) {
				return condition;
			}
		}
		return ABANDONED;
	}

	public static TownCondition byId(String id) {
		for (TownCondition condition : values()) {
			if (condition.id.equals(id)) {
				return condition;
			}
		}
		return ABANDONED;
	}
}
