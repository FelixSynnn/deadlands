package com.deadlands.story;

/** The archive's shelves. */
public enum LoreCategory {
	RESEARCH("research"),
	CHARACTER("character"),
	MILITARY("military"),
	MEDICAL("medical"),
	GOVERNMENT("government"),
	NEWSPAPER("newspaper"),
	RADIO("radio"),
	MISSING("missing"),
	JOURNAL("journal");

	private final String id;

	LoreCategory(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public String translationKey() {
		return "lore.deadlands.category." + id;
	}
}
