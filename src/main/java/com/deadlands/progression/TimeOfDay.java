package com.deadlands.progression;

import java.util.Locale;

/** Converts Minecraft day time (0 = 06:00) into clock text and a coarse period of the day. */
public final class TimeOfDay {
	private TimeOfDay() {
	}

	public enum Period {
		DAWN, MORNING, AFTERNOON, DUSK, NIGHT;

		public String translationKey() {
			return "time.deadlands." + name().toLowerCase(Locale.ROOT);
		}
	}

	/** 24-hour clock text such as "06:00" or "23:45". */
	public static String clock(long dayTime) {
		long t = Math.floorMod(dayTime, WorldClock.TICKS_PER_DAY);
		long hours = (t / 1000L + 6L) % 24L;
		long minutes = (t % 1000L) * 60L / 1000L;
		return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
	}

	public static Period period(long dayTime) {
		long t = Math.floorMod(dayTime, WorldClock.TICKS_PER_DAY);
		if (t >= 23000L || t < 1000L) {
			return Period.DAWN;
		}
		if (t < 6000L) {
			return Period.MORNING;
		}
		if (t < 11500L) {
			return Period.AFTERNOON;
		}
		if (t < 13500L) {
			return Period.DUSK;
		}
		return Period.NIGHT;
	}
}
