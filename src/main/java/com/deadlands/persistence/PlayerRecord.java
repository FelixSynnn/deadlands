package com.deadlands.persistence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What the world remembers about one survivor. Day values are 0-based day indices
 * ({@code dayTime / 24000}); the UI adds one to show "Day 1" for index 0.
 */
public final class PlayerRecord {
	public static final Codec<PlayerRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.optionalFieldOf("name", "").forGetter(r -> r.lastKnownName),
		Codec.LONG.optionalFieldOf("first_join_day", 0L).forGetter(r -> r.firstJoinDay),
		Codec.LONG.optionalFieldOf("last_death_day", -1L).forGetter(r -> r.lastDeathDay),
		Codec.INT.optionalFieldOf("deaths", 0).forGetter(r -> r.deaths),
		Codec.BOOL.optionalFieldOf("journal_given", false).forGetter(r -> r.journalGiven),
		Codec.LONG.optionalFieldOf("last_seen_day", 0L).forGetter(r -> r.lastSeenDay),
		Codec.INT.optionalFieldOf("infected_killed", 0).forGetter(r -> r.infectedKilled)
	).apply(instance, PlayerRecord::new));

	private String lastKnownName;
	private long firstJoinDay;
	private long lastDeathDay;
	private int deaths;
	private boolean journalGiven;
	private long lastSeenDay;
	private int infectedKilled;

	public PlayerRecord(String lastKnownName, long firstJoinDay, long lastDeathDay, int deaths, boolean journalGiven, long lastSeenDay,
		int infectedKilled) {
		this.lastKnownName = lastKnownName;
		this.firstJoinDay = firstJoinDay;
		this.lastDeathDay = lastDeathDay;
		this.deaths = deaths;
		this.journalGiven = journalGiven;
		this.lastSeenDay = lastSeenDay;
		this.infectedKilled = infectedKilled;
	}

	public static PlayerRecord arriving(String name, long dayIndex) {
		return new PlayerRecord(name, dayIndex, -1L, 0, false, dayIndex, 0);
	}

	public int infectedKilled() {
		return infectedKilled;
	}

	public void addInfectedKill() {
		infectedKilled++;
	}

	/** Whole days survived since arrival or since the last death, whichever is later. */
	public long daysSurvived(long currentDayIndex) {
		long since = lastDeathDay >= 0 ? lastDeathDay : firstJoinDay;
		return Math.max(0L, currentDayIndex - since);
	}

	public void recordDeath(long dayIndex) {
		deaths++;
		lastDeathDay = dayIndex;
	}

	public String lastKnownName() {
		return lastKnownName;
	}

	public void setLastKnownName(String name) {
		this.lastKnownName = name;
	}

	public long firstJoinDay() {
		return firstJoinDay;
	}

	public long lastDeathDay() {
		return lastDeathDay;
	}

	public int deaths() {
		return deaths;
	}

	public boolean journalGiven() {
		return journalGiven;
	}

	public void setJournalGiven(boolean journalGiven) {
		this.journalGiven = journalGiven;
	}

	public long lastSeenDay() {
		return lastSeenDay;
	}

	public void setLastSeenDay(long lastSeenDay) {
		this.lastSeenDay = lastSeenDay;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof PlayerRecord r && r.lastKnownName.equals(lastKnownName) && r.firstJoinDay == firstJoinDay
			&& r.lastDeathDay == lastDeathDay && r.deaths == deaths && r.journalGiven == journalGiven && r.lastSeenDay == lastSeenDay
			&& r.infectedKilled == infectedKilled;
	}

	@Override
	public int hashCode() {
		return java.util.Objects.hash(lastKnownName, firstJoinDay, lastDeathDay, deaths, journalGiven, lastSeenDay, infectedKilled);
	}
}
