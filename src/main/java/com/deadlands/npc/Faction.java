package com.deadlands.npc;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Who a survivor belongs to. A survivor's reputation is kept per faction ({@link Reputation}).
 *
 * <ul>
 *   <li>SETTLERS: the survivor holdouts. They start neutral.</li>
 *   <li>MILITARY: what is left of the army at its checkpoints. Neutral, slow to trust.</li>
 *   <li>BANDITS: they start hostile, and can be bought round.</li>
 *   <li>DRIFTERS: travelling merchants and loners. Neutral to everyone.</li>
 *   <li>RESEARCH: Harley and those around her. Friendly from the start.</li>
 * </ul>
 */
public enum Faction implements StringRepresentable {
	SETTLERS("settlers", 0),
	MILITARY("military", 0),
	BANDITS("bandits", -50),
	DRIFTERS("drifters", 0),
	RESEARCH("research", 40);

	public static final Codec<Faction> CODEC = StringRepresentable.fromEnum(Faction::values);

	private final String id;
	private final int startingReputation;

	Faction(String id, int startingReputation) {
		this.id = id;
		this.startingReputation = startingReputation;
	}

	public String id() {
		return id;
	}

	@Override
	public String getSerializedName() {
		return id;
	}

	/** Where a newcomer stands with this faction. */
	public int startingReputation() {
		return startingReputation;
	}

	public String translationKey() {
		return "npc.deadlands.faction." + id;
	}

	/** Factions at war attack each other's fighters on sight. */
	public boolean hostileTo(Faction other) {
		return (this == BANDITS) != (other == BANDITS);
	}

	public static Faction byIndex(int index) {
		Faction[] factions = values();
		return factions[Math.floorMod(index, factions.length)];
	}
}
