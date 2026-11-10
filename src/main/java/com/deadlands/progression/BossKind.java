package com.deadlands.progression;

import net.minecraft.network.chat.Component;

/**
 * The boss roster, in encounter order. Boss number N (counting from 1) on the boss schedule is
 * {@link #forIndex(int)}; after Patient Zero the roster repeats at a higher escalation tier.
 *
 * <p>The entities are {@code entity.BossEntity}, one type per boss ({@code registry.DLBosses});
 * {@code horde.BossDirector} brings them on their nights.
 */
public enum BossKind {
	ABOMINATION("abomination"),
	BUTCHER("butcher"),
	WARDEN("warden"),
	EXPERIMENT("experiment"),
	PATIENT_ZERO("patient_zero");

	private final String id;

	BossKind(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public Component displayName() {
		return Component.translatable("boss.deadlands." + id);
	}

	/** Boss for the Nth boss encounter (1-based). */
	public static BossKind forIndex(int index) {
		BossKind[] roster = values();
		return roster[Math.floorMod(Math.max(1, index) - 1, roster.length)];
	}

	/** 0 for the first pass through the roster, 1 for the second, and so on. */
	public static int escalationTier(int index) {
		return (Math.max(1, index) - 1) / values().length;
	}
}
