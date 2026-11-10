package com.deadlands.progression;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Pure schedule logic for horde nights and boss encounters (no world access, safe on both sides).
 *
 * <p>Design rules from the DEADLANDS brief:
 * <ul>
 *   <li>A horde every {@code hordeInterval} days (default 5), starting around 30 infected and growing.</li>
 *   <li>A boss every {@code bossInterval} days (default 10). On a boss day the boss <em>replaces</em>
 *       the horde; both never happen together.</li>
 *   <li>Boss order: Abomination, Butcher, Warden, Experiment, Patient Zero, then escalating repeats.</li>
 * </ul>
 * Day numbers are 1-based throughout ("Day 1" is the first day).
 */
public final class EncounterSchedule {
	private static final int SEARCH_LIMIT_DAYS = 10_000;

	private EncounterSchedule() {
	}

	public enum Kind {
		NONE, HORDE, BOSS
	}

	/**
	 * @param boss       the boss for a BOSS encounter, else null
	 * @param bossIndex  1-based count of boss encounters up to and including this one (0 if none)
	 * @param tier       escalation tier for bosses (0 on the first pass through the roster)
	 * @param hordeIndex 1-based count of hordes up to and including this one (0 if none)
	 * @param hordeSize  infected in the horde (0 if none)
	 */
	public record Encounter(long day, Kind kind, @Nullable BossKind boss, int bossIndex, int tier, int hordeIndex, int hordeSize) {
		public static Encounter none(long day) {
			return new Encounter(day, Kind.NONE, null, 0, 0, 0, 0);
		}

		public boolean isNone() {
			return kind == Kind.NONE;
		}
	}

	public static Encounter forDay(long day, ScheduleSettings s) {
		if (day <= 0) {
			return Encounter.none(day);
		}
		if (s.bossesEnabled() && day % s.bossInterval() == 0) {
			int index = (int) Math.min(Integer.MAX_VALUE, day / s.bossInterval());
			return new Encounter(day, Kind.BOSS, BossKind.forIndex(index), index, BossKind.escalationTier(index), 0, 0);
		}
		if (s.hordesEnabled() && day % s.hordeInterval() == 0) {
			int index = hordeIndex(day, s);
			return new Encounter(day, Kind.HORDE, null, 0, 0, index, hordeSize(index, s));
		}
		return Encounter.none(day);
	}

	/** First encounter on or after {@code fromDay}, or a NONE encounter if nothing is scheduled. */
	public static Encounter next(long fromDay, ScheduleSettings s) {
		long start = Math.max(1, fromDay);
		for (long day = start; day < start + SEARCH_LIMIT_DAYS; day++) {
			Encounter e = forDay(day, s);
			if (!e.isNone()) {
				return e;
			}
		}
		return Encounter.none(start);
	}

	/** The next {@code count} encounters on or after {@code fromDay}. */
	public static List<Encounter> upcoming(long fromDay, int count, ScheduleSettings s) {
		List<Encounter> out = new ArrayList<>(count);
		long day = Math.max(1, fromDay);
		while (out.size() < count) {
			Encounter e = next(day, s);
			if (e.isNone()) {
				break;
			}
			out.add(e);
			day = e.day() + 1;
		}
		return out;
	}

	/** How many hordes happen on days 1..day (boss days replace hordes, so they do not count). */
	static int hordeIndex(long day, ScheduleSettings s) {
		long hordeDays = day / s.hordeInterval();
		if (s.bossesEnabled()) {
			hordeDays -= day / lcm(s.hordeInterval(), s.bossInterval());
		}
		return (int) Math.max(0, Math.min(Integer.MAX_VALUE, hordeDays));
	}

	public static int hordeSize(int hordeIndex, ScheduleSettings s) {
		if (hordeIndex <= 0) {
			return 0;
		}
		double size = s.hordeBaseSize() * (1.0 + s.hordeGrowth() * (hordeIndex - 1));
		return (int) Math.min(s.hordeMaxSize(), Math.round(size));
	}

	private static long lcm(long a, long b) {
		return a / gcd(a, b) * b;
	}

	private static long gcd(long a, long b) {
		while (b != 0) {
			long t = a % b;
			a = b;
			b = t;
		}
		return Math.max(1, a);
	}
}
