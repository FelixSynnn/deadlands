package com.deadlands.spawning;

import com.deadlands.config.ConfigManager;
import com.deadlands.entity.InfectedKind;
import com.deadlands.world.town.TownCondition;
import com.deadlands.world.town.TownSite;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * Which infected appear where. Each DEADLANDS biome has its own mix (more Brutes and Military
 * among the factories, Bloaters and Crawlers in the marsh, Charred in the burned forest...);
 * Runners are added on top so their share matches {@code zombies.runnerShare}, scaled per biome
 * (more on open, dry ground). Nocturnal kinds are rare by day, military towns are full of Military
 * infected, and Crawlers are more common underground.
 */
public final class InfectedSpawnTable {
	/** Weights in {@link InfectedKind} order, without Runners (WALKER, RUNNER=0, BRUTE, SCREAMER, MILITARY, CRAWLER, BLOATER, STALKER, CHARRED). */
	private record Mix(int[] weights, double runnerFactor) {
	}

	private static final Mix DEFAULT = mix(1.0, 70, 4, 2, 2, 8, 3, 2, 0);
	private static final Map<String, Mix> BY_BIOME = Map.of(
		"dead_forest", mix(1.0, 60, 3, 3, 1, 8, 2, 6, 0),
		"overgrown_suburbs", mix(1.0, 70, 4, 3, 1, 8, 3, 2, 0),
		"abandoned_farmland", mix(1.0, 70, 4, 2, 1, 6, 2, 2, 0),
		"dry_wasteland", mix(1.6, 55, 8, 3, 3, 8, 1, 2, 2),
		"flooded_marsh", mix(0.6, 55, 2, 2, 1, 14, 14, 2, 0),
		"dead_pine_valley", mix(1.0, 60, 3, 3, 1, 8, 2, 7, 0),
		"frozen_outskirts", mix(1.4, 60, 6, 2, 2, 5, 1, 6, 0),
		"industrial_wasteland", mix(0.9, 50, 14, 2, 8, 10, 8, 2, 2),
		"burned_forest", mix(1.0, 40, 3, 2, 1, 8, 1, 4, 30),
		"rural_countryside", mix(1.0, 70, 5, 2, 1, 6, 2, 2, 0)
	);

	private InfectedSpawnTable() {
	}

	private static Mix mix(double runnerFactor, int walker, int brute, int screamer, int military, int crawler, int bloater, int stalker, int charred) {
		return new Mix(new int[] {walker, 0, brute, screamer, military, crawler, bloater, stalker, charred}, runnerFactor);
	}

	public static InfectedKind pick(ServerLevel level, BlockPos pos, boolean night, @Nullable TownSite town, boolean underground, RandomSource random) {
		Holder<Biome> biome = level.getBiome(pos);
		String id = biome.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
		Mix mix = BY_BIOME.getOrDefault(id, DEFAULT);
		Map<InfectedKind, Double> weights = new EnumMap<>(InfectedKind.class);
		double others = 0.0;
		for (InfectedKind kind : InfectedKind.values()) {
			double w = mix.weights()[kind.ordinal()];
			if (!night && kind.nocturnal()) {
				w *= kind == InfectedKind.STALKER ? 0.0 : 0.15;
			}
			if (underground && kind == InfectedKind.CRAWLER) {
				w *= 2.0;
			}
			if (town != null && town.condition() == TownCondition.MILITARY && kind == InfectedKind.MILITARY) {
				w += 60.0;
			}
			weights.put(kind, w);
			others += w;
		}
		double share = Mth.clamp(ConfigManager.get().zombies.runnerShare * mix.runnerFactor() * (night ? 1.0 : 0.6), 0.0, 0.95);
		weights.put(InfectedKind.RUNNER, share >= 0.95 ? others * 19.0 : others * share / (1.0 - share));

		double total = 0.0;
		for (double w : weights.values()) {
			total += w;
		}
		double roll = random.nextDouble() * total;
		for (Map.Entry<InfectedKind, Double> entry : weights.entrySet()) {
			roll -= entry.getValue();
			if (roll < 0.0) {
				return entry.getKey();
			}
		}
		return InfectedKind.WALKER;
	}

	/** How many of a kind appear together. */
	public static int groupSize(InfectedKind kind, RandomSource random) {
		int base = baseGroupSize(kind, random);
		return Math.max(1, (int) Math.round(base * ConfigManager.get().zombies.groupSizeFactor));
	}

	/** The group size before {@code zombies.groupSizeFactor}. */
	private static int baseGroupSize(InfectedKind kind, RandomSource random) {
		return switch (kind) {
			case WALKER -> 2 + random.nextInt(3);
			case RUNNER -> 1 + random.nextInt(3);
			case MILITARY, CHARRED -> 1 + random.nextInt(3);
			case CRAWLER -> 1 + random.nextInt(2);
			case BRUTE, SCREAMER, BLOATER, STALKER -> 1;
		};
	}
}
