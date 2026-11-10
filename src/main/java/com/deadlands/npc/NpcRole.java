package com.deadlands.npc;

/**
 * What a survivor does. The role decides the job (what they trade or offer in conversation), how
 * they fight, and where they stand at night.
 */
public enum NpcRole {
	//       id          fights  health damage
	SETTLER("settler", false, 20.0, 2.0),
	GUARD("guard", true, 30.0, 3.0),
	TRADER("trader", false, 20.0, 2.0),
	DOCTOR("doctor", false, 20.0, 2.0),
	MECHANIC("mechanic", false, 22.0, 3.0),
	FARMER("farmer", false, 22.0, 3.0),
	LEADER("leader", true, 28.0, 3.0),
	BANDIT("bandit", true, 24.0, 2.5),
	/** Travelling merchants and lone survivors met on the road. */
	WANDERER("wanderer", false, 22.0, 3.0),
	/** Harley's people: they study the infection in the research camps. */
	RESEARCHER("researcher", false, 20.0, 2.0);

	private final String id;
	private final boolean fighter;
	private final double health;
	private final double damage;

	NpcRole(String id, boolean fighter, double health, double damage) {
		this.id = id;
		this.fighter = fighter;
		this.health = health;
		this.damage = damage;
	}

	public String id() {
		return id;
	}

	/** Fighters stand their ground against the infected and their enemies; the rest run. */
	public boolean fighter() {
		return fighter;
	}

	public double health() {
		return health;
	}

	public double damage() {
		return damage;
	}

	/** Has something to sell. */
	public boolean trades() {
		return this == TRADER || this == DOCTOR || this == MECHANIC || this == FARMER || this == WANDERER || this == RESEARCHER;
	}

	public String translationKey() {
		return "npc.deadlands.role." + id;
	}

	public static NpcRole byIndex(int index) {
		NpcRole[] roles = values();
		return roles[Math.floorMod(index, roles.length)];
	}
}
