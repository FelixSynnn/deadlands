package com.deadlands.survival;

/**
 * Things that can be wrong with a survivor. Each is shown on the HUD by its icon
 * ({@code textures/gui/sprites/survival/<id>.png}) with its name and what to do about it
 * ({@code condition.deadlands.<id>} and {@code condition.deadlands.<id>.hint}).
 */
public enum Condition {
	THIRSTY("thirsty", false),
	DEHYDRATED("dehydrated", true),
	COLD("cold", false),
	FREEZING("freezing", true),
	HOT("hot", false),
	OVERHEATING("overheating", true),
	TIRED("tired", false),
	EXHAUSTED("exhausted", true),
	BLEEDING("bleeding", true),
	HEAVY_BLEEDING("heavy_bleeding", true),
	FRACTURE("fracture", true),
	SICK("sick", false),
	INFECTED("infected", true),
	PAINKILLERS("painkillers", false),
	WINDED("winded", false),
	ENCUMBERED("encumbered", false);

	private final String id;
	private final boolean serious;

	Condition(String id, boolean serious) {
		this.id = id;
		this.serious = serious;
	}

	public String id() {
		return id;
	}

	/** Serious conditions are drawn in the danger colour. */
	public boolean serious() {
		return serious;
	}

	public int bit() {
		return 1 << ordinal();
	}

	public String translationKey() {
		return "condition.deadlands." + id;
	}
}
