package com.deadlands.skill;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * The five skill trees. A skill grows by doing what it is about and gives a small bonus per level
 * (see {@link Skills} for the numbers), up to level {@link #MAX_LEVEL}.
 *
 * <ul>
 *   <li>SURVIVALIST: grows by staying alive and killing infected. Slower thirst and fatigue, more
 *       carrying capacity.</li>
 *   <li>MEDIC: grows by treating injuries. Bandages and first aid kits heal more.</li>
 *   <li>GUNSLINGER: grows by kills with weapons. Firearms hit harder and are ready sooner.</li>
 *   <li>ENGINEER: grows by restoring power. A chance to keep the fuse.</li>
 *   <li>SCAVENGER: grows by searching containers. A chance of extra loot.</li>
 * </ul>
 */
public enum Skill implements StringRepresentable {
	SURVIVALIST("survivalist"),
	MEDIC("medic"),
	GUNSLINGER("gunslinger"),
	ENGINEER("engineer"),
	SCAVENGER("scavenger");

	public static final int MAX_LEVEL = 10;
	public static final Codec<Skill> CODEC = StringRepresentable.fromEnum(Skill::values);

	private final String id;

	Skill(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	@Override
	public String getSerializedName() {
		return id;
	}

	public String translationKey() {
		return "skill.deadlands." + id;
	}

	/** Experience needed to reach {@code level}: 50, 200, 450 ... 5000 at level 10. */
	public static int xpFor(int level) {
		return 50 * level * level;
	}

	public static int levelFor(int xp) {
		int level = (int) Math.floor(Math.sqrt(Math.max(0, xp) / 50.0));
		return Math.min(MAX_LEVEL, level);
	}
}
