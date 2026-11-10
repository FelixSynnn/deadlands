package com.deadlands.world.biome;

import com.deadlands.config.ConfigManager;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

/**
 * Turns vanilla's overworld land biomes into DEADLANDS biomes while vanilla builds its climate
 * table (see {@code OverworldBiomeBuilderMixin}). Terrain shape, oceans, rivers, beaches, peaks and
 * caves are untouched: the same climate slots simply name a DEADLANDS biome, so the world keeps
 * vanilla's believable geography and every land region becomes part of the wasteland.
 *
 * <p>Where one vanilla biome should become two DEADLANDS biomes, the slot's temperature band
 * decides (for example cold plains become Rural Countryside, warm plains Abandoned Farmland).
 */
public final class BiomeReplacement {
	/** Upper bounds of vanilla's five temperature bands (OverworldBiomeBuilder.temperatures). */
	private static final float[] TEMPERATURE_BANDS = {-0.45F, -0.15F, 0.2F, 0.55F};
	private static final Map<ResourceKey<Biome>, IntFunction<ResourceKey<Biome>>> RULES = new HashMap<>();

	static {
		always(DLBiomes.FROZEN_OUTSKIRTS, Biomes.SNOWY_PLAINS, Biomes.ICE_SPIKES, Biomes.SNOWY_TAIGA, Biomes.GROVE, Biomes.SNOWY_SLOPES);
		always(DLBiomes.DEAD_PINE_VALLEY, Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA,
			Biomes.WINDSWEPT_FOREST, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS);
		RULES.put(Biomes.PLAINS, band -> band <= 1 ? DLBiomes.RURAL_COUNTRYSIDE : DLBiomes.ABANDONED_FARMLAND);
		always(DLBiomes.RURAL_COUNTRYSIDE, Biomes.MEADOW);
		always(DLBiomes.INDUSTRIAL_WASTELAND, Biomes.SUNFLOWER_PLAINS);
		always(DLBiomes.OVERGROWN_SUBURBS, Biomes.FLOWER_FOREST, Biomes.CHERRY_GROVE, Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE);
		RULES.put(Biomes.FOREST, band -> band >= 3 ? DLBiomes.BURNED_FOREST : DLBiomes.DEAD_FOREST);
		always(DLBiomes.DEAD_FOREST, Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.DARK_FOREST, Biomes.PALE_GARDEN);
		always(DLBiomes.BURNED_FOREST, Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA, Biomes.WOODED_BADLANDS);
		always(DLBiomes.DRY_WASTELAND, Biomes.DESERT, Biomes.BADLANDS, Biomes.ERODED_BADLANDS);
		always(DLBiomes.FLOODED_MARSH, Biomes.SWAMP, Biomes.MANGROVE_SWAMP, Biomes.MUSHROOM_FIELDS);
	}

	private BiomeReplacement() {
	}

	@SafeVarargs
	private static void always(ResourceKey<Biome> target, ResourceKey<Biome>... vanilla) {
		for (ResourceKey<Biome> key : vanilla) {
			RULES.put(key, band -> target);
		}
	}

	/** True when DEADLANDS biomes should replace vanilla ones (read when a world's biome table is built). */
	public static boolean enabled() {
		return ConfigManager.get().world.deadlandsBiomes;
	}

	/** The DEADLANDS biome for a vanilla surface biome slot, or the vanilla biome itself if it is kept. */
	public static ResourceKey<Biome> replace(ResourceKey<Biome> vanilla, Climate.Parameter temperature) {
		IntFunction<ResourceKey<Biome>> rule = RULES.get(vanilla);
		return rule == null ? vanilla : rule.apply(temperatureBand(temperature));
	}

	private static int temperatureBand(Climate.Parameter temperature) {
		float mid = (Climate.unquantizeCoord(temperature.min()) + Climate.unquantizeCoord(temperature.max())) * 0.5F;
		for (int band = 0; band < TEMPERATURE_BANDS.length; band++) {
			if (mid < TEMPERATURE_BANDS[band]) {
				return band;
			}
		}
		return TEMPERATURE_BANDS.length;
	}
}
