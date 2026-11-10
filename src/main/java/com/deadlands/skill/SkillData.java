package com.deadlands.skill;

import com.mojang.serialization.Codec;
import java.util.EnumMap;
import java.util.Map;

/** A survivor's experience in each skill. Attached to the player, saved, and kept through death. */
public final class SkillData {
	public static final Codec<SkillData> CODEC = Codec.unboundedMap(Skill.CODEC, Codec.INT).xmap(SkillData::new, data -> Map.copyOf(data.xp));

	private final Map<Skill, Integer> xp = new EnumMap<>(Skill.class);

	public SkillData() {
	}

	private SkillData(Map<Skill, Integer> saved) {
		saved.forEach((skill, value) -> xp.put(skill, Math.max(0, value)));
	}

	public int xp(Skill skill) {
		return xp.getOrDefault(skill, 0);
	}

	public int level(Skill skill) {
		return Skill.levelFor(xp(skill));
	}

	/** Add experience. Returns the new level if this crossed into one, otherwise 0. */
	public int add(Skill skill, int amount) {
		int before = level(skill);
		xp.put(skill, Math.min(Skill.xpFor(Skill.MAX_LEVEL), xp(skill) + Math.max(0, amount)));
		int after = level(skill);
		return after > before ? after : 0;
	}
}
