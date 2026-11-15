package com.deadlands.survival;

import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import com.deadlands.config.DeadlandsConfig.SurvivalDifficulty;

/**
 * The numbers behind the survival systems, per difficulty preset ({@code survival.difficulty}).
 * All rates below are per second at the SURVIVOR preset with {@code survival.drainMultiplier} 1.
 *
 * <ul>
 *   <li>STORY: slow drain, weak injuries, and nothing here can kill: dehydration, cold, heat,
 *       bleeding and infection stop at half a heart.</li>
 *   <li>SURVIVOR: the intended experience. A full thirst bar lasts about a day and a half of
 *       walking; staying awake two and a half days exhausts you; an untreated infection kills in
 *       about three days.</li>
 *   <li>VETERAN: a quarter faster, injuries more likely.</li>
 *   <li>NIGHTMARE: half again as fast, injuries much more likely, infection twice as fast.</li>
 * </ul>
 */
public final class SurvivalRules {
	/** Thirst lost per second standing still: 100 in 30 minutes (a day and a half). */
	public static final float THIRST_PER_SECOND = 100.0F / 1800.0F;
	/** Fatigue gained per second awake: 100 in 50 minutes (two and a half days). */
	public static final float FATIGUE_PER_SECOND = 100.0F / 3000.0F;
	/** Infection gained per second once infected: 100 in an hour (three days). */
	public static final float INFECTION_PER_SECOND = 100.0F / 3600.0F;
	/** Stamina goes 30% further than it used to: sprinting, jumping and swinging cost less, and it comes back faster. */
	public static final float STAMINA_BOOST = 1.3F;
	public static final float STAMINA_SPRINT_PER_SECOND = 7.0F / STAMINA_BOOST;
	public static final float STAMINA_REGEN_PER_SECOND = 9.0F * STAMINA_BOOST;
	public static final float STAMINA_JUMP = 4.0F / STAMINA_BOOST;
	public static final float STAMINA_ATTACK = 5.0F / STAMINA_BOOST;
	/** A winded survivor can sprint again once stamina is back above this. */
	public static final float WINDED_RECOVERY = 25.0F;
	public static final int FRACTURE_TICKS = 20 * 60 * 10;
	public static final int SICKNESS_TICKS = 20 * 60 * 3;
	public static final int PAINKILLER_TICKS = 20 * 60 * 4;

	private SurvivalRules() {
	}

	private static DeadlandsConfig.Survival config() {
		return ConfigManager.get().survival;
	}

	public static SurvivalDifficulty difficulty() {
		return config().difficulty;
	}

	/** Multiplier on thirst, fatigue and hunger drain. */
	public static float drain() {
		float preset = switch (difficulty()) {
			case STORY -> 0.6F;
			case SURVIVOR -> 1.0F;
			case VETERAN -> 1.25F;
			case NIGHTMARE -> 1.5F;
		};
		return preset * (float) config().drainMultiplier;
	}

	/** Multiplier on the chance of a hit causing bleeding, a bite infecting, a fall breaking a leg. */
	public static float injuryChance() {
		return switch (difficulty()) {
			case STORY -> 0.5F;
			case SURVIVOR -> 1.0F;
			case VETERAN -> 1.3F;
			case NIGHTMARE -> 1.7F;
		};
	}

	public static float infectionSpeed() {
		return switch (difficulty()) {
			case STORY -> 0.5F;
			case SURVIVOR -> 1.0F;
			case VETERAN -> 1.25F;
			case NIGHTMARE -> 2.0F;
		};
	}

	/** On STORY the survival systems hurt but never deal the killing blow. */
	public static boolean lethal() {
		return difficulty() != SurvivalDifficulty.STORY;
	}

	public static boolean thirst() {
		return config().thirst;
	}

	public static boolean temperature() {
		return config().temperature;
	}

	public static boolean stamina() {
		return config().stamina;
	}

	public static boolean sleep() {
		return config().sleep;
	}

	public static boolean weight() {
		return config().weight;
	}

	public static boolean infection() {
		return config().infection;
	}
}
