package com.deadlands.world;

/** Deterministic hashing for world generation decisions (SplitMix64 finaliser). */
public final class Hashing {
	private Hashing() {
	}

	public static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	public static long of(long seed, long a, long b, long salt) {
		return mix(seed ^ mix(a * 0x9E3779B97F4A7C15L ^ mix(b * 0xC2B2AE3D27D4EB4FL ^ salt)));
	}

	/** A value in [0, 1). */
	public static double unit(long hash) {
		return (hash >>> 11) * 0x1.0p-53;
	}
}
