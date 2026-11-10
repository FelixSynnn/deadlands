package com.deadlands.nest;

import com.deadlands.entity.InfectedKind;
import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;

/**
 * Nest types. Each has its own look (block textures by tools/assetgen/gen_nests.py), how many
 * infected it shelters, how many it lets out at once, how keen its hearing is, its cooldown, how
 * fast it refills and which kinds live in it.
 */
public enum NestKind implements StringRepresentable {
	//                  pop  out  out  hear  cooldown regen
	RESIDENTIAL("residential", 4, 2, 3, 1.0F, 2400, 3600,
		new Weighted(InfectedKind.WALKER, 80), new Weighted(InfectedKind.CRAWLER, 12), new Weighted(InfectedKind.RUNNER, 8)),
	INDUSTRIAL("industrial", 5, 2, 4, 0.9F, 2400, 3600,
		new Weighted(InfectedKind.WALKER, 60), new Weighted(InfectedKind.CRAWLER, 15), new Weighted(InfectedKind.BRUTE, 10),
		new Weighted(InfectedKind.BLOATER, 8), new Weighted(InfectedKind.RUNNER, 7)),
	MILITARY("military", 5, 2, 3, 1.0F, 2000, 3000,
		new Weighted(InfectedKind.MILITARY, 60), new Weighted(InfectedKind.WALKER, 30), new Weighted(InfectedKind.RUNNER, 10)),
	UNDERGROUND("underground", 6, 3, 4, 1.2F, 2400, 3000,
		new Weighted(InfectedKind.CRAWLER, 45), new Weighted(InfectedKind.WALKER, 35), new Weighted(InfectedKind.BLOATER, 12),
		new Weighted(InfectedKind.STALKER, 8)),
	ADVANCED("advanced", 10, 3, 6, 1.5F, 1600, 2000,
		new Weighted(InfectedKind.WALKER, 30), new Weighted(InfectedKind.RUNNER, 25), new Weighted(InfectedKind.BRUTE, 10),
		new Weighted(InfectedKind.STALKER, 10), new Weighted(InfectedKind.BLOATER, 10), new Weighted(InfectedKind.SCREAMER, 8),
		new Weighted(InfectedKind.CRAWLER, 7));

	public static final Codec<NestKind> CODEC = StringRepresentable.fromEnum(NestKind::values);

	public record Weighted(InfectedKind kind, int weight) {
	}

	private final String id;
	private final int maxPopulation;
	private final int releaseMin;
	private final int releaseMax;
	private final float hearing;
	private final int cooldownTicks;
	private final int regenTicks;
	private final Weighted[] residents;

	NestKind(String id, int maxPopulation, int releaseMin, int releaseMax, float hearing, int cooldownTicks, int regenTicks, Weighted... residents) {
		this.id = id;
		this.maxPopulation = maxPopulation;
		this.releaseMin = releaseMin;
		this.releaseMax = releaseMax;
		this.hearing = hearing;
		this.cooldownTicks = cooldownTicks;
		this.regenTicks = regenTicks;
		this.residents = residents;
	}

	public String id() {
		return id;
	}

	/** Block id of this nest's core. */
	public String blockId() {
		return id + "_nest";
	}

	@Override
	public String getSerializedName() {
		return id;
	}

	public int maxPopulation() {
		return maxPopulation;
	}

	public int releaseCount(RandomSource random) {
		return releaseMin + random.nextInt(releaseMax - releaseMin + 1);
	}

	public float hearing() {
		return hearing;
	}

	public int cooldownTicks() {
		return cooldownTicks;
	}

	/** Ticks to grow back one infected (halved at night). */
	public int regenTicks() {
		return regenTicks;
	}

	public InfectedKind pickResident(RandomSource random) {
		int total = 0;
		for (Weighted w : residents) {
			total += w.weight();
		}
		int roll = random.nextInt(Math.max(1, total));
		for (Weighted w : residents) {
			roll -= w.weight();
			if (roll < 0) {
				return w.kind();
			}
		}
		return InfectedKind.WALKER;
	}

	public static NestKind byName(String name) {
		for (NestKind kind : values()) {
			if (kind.id.equals(name.toLowerCase(Locale.ROOT))) {
				return kind;
			}
		}
		return RESIDENTIAL;
	}
}
