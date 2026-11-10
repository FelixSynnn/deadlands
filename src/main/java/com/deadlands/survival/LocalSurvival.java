package com.deadlands.survival;

import com.deadlands.network.SurvivalPayload;

/**
 * The local player's body state as last sent by the server. It lives in common code so the sprint
 * check (a mixin into {@code Player}, which runs on both sides) can read it on the client; on a
 * dedicated server it is never written and stays inactive.
 */
public final class LocalSurvival {
	private static volatile SurvivalPayload state = SurvivalPayload.INACTIVE;

	private LocalSurvival() {
	}

	public static SurvivalPayload get() {
		return state;
	}

	public static void set(SurvivalPayload payload) {
		state = payload;
	}

	public static void reset() {
		state = SurvivalPayload.INACTIVE;
	}

	public static boolean winded() {
		SurvivalPayload s = state;
		return s.active() && (s.conditions() & Condition.WINDED.bit()) != 0;
	}
}
