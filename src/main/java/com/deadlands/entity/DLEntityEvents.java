package com.deadlands.entity;

import java.util.List;

/**
 * Every entity event id DEADLANDS sends with {@code Level.broadcastEntityEvent}.
 *
 * <p>Vanilla declares ids 1 to 69 in {@link net.minecraft.world.entity.EntityEvent}, and the client
 * handles some of them itself before the entity ever sees them:
 * {@code ClientPacketListener.handleEntityEvent} casts the entity to {@code Guardian} for 21 and to
 * {@code Sniffer} for 63. Any other entity sent one of those ids crashes the client with a
 * {@link ClassCastException} (the Stalker's pounce used to be 63). Ids vanilla reuses for its own
 * mobs can also be sent by vanilla code for reasons of its own. DEADLANDS ids therefore live in a
 * range of their own, well above vanilla's, and a server game test fails the build if one of them
 * ever overlaps an id vanilla declares.
 */
public final class DLEntityEvents {
	/** The first id DEADLANDS may use; everything below is vanilla's, now or in a future version. */
	public static final byte FIRST = 100;

	/** An infected starts its attack swing (the damage lands a few ticks later). */
	public static final byte INFECTED_ATTACK = 100;
	/** A Brute's ground slam. */
	public static final byte INFECTED_SLAM = 101;
	/** A Stalker crouches and leaps. */
	public static final byte INFECTED_POUNCE = 102;
	/** A Screamer screams. */
	public static final byte INFECTED_SCREAM = 103;
	/** An infected climbs out of its nest. */
	public static final byte INFECTED_EMERGE = 104;

	public static final List<Byte> ALL = List.of(INFECTED_ATTACK, INFECTED_SLAM, INFECTED_POUNCE, INFECTED_SCREAM, INFECTED_EMERGE);

	private DLEntityEvents() {
	}
}
