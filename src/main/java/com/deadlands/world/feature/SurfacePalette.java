package com.deadlands.world.feature;

import com.deadlands.registry.DLBlocks;
import com.deadlands.world.biome.DLBiomes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The ground of each DEADLANDS biome: which surface blocks it repaints and with what, as bands of
 * a large-scale patch noise (so ash, mud or cracked earth form believable patches rather than
 * speckle). A band with no block keeps the original ground.
 */
final class SurfacePalette {
	private static final Set<Block> GRASSY = Set.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.PODZOL, Blocks.MYCELIUM, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT);
	private static final Set<Block> GRASSY_AND_SAND = Set.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.PODZOL, Blocks.MYCELIUM, Blocks.COARSE_DIRT,
		Blocks.ROOTED_DIRT, Blocks.SAND, Blocks.RED_SAND);
	private static final Map<ResourceKey<Biome>, SurfacePalette> BY_BIOME = new HashMap<>();

	static {
		palette(DLBiomes.DEAD_FOREST, GRASSY)
			.band(-0.45, Blocks.PODZOL).band(-0.05, DLBlocks.DEAD_GRASS_BLOCK).keep(0.25).band(0.45, Blocks.COARSE_DIRT).rest(DLBlocks.DEAD_GRASS_BLOCK);
		palette(DLBiomes.OVERGROWN_SUBURBS, GRASSY)
			.band(-0.55, Blocks.MOSS_BLOCK).band(-0.4, Blocks.ROOTED_DIRT).keepRest();
		palette(DLBiomes.ABANDONED_FARMLAND, GRASSY)
			.band(-0.5, Blocks.COARSE_DIRT).band(-0.2, DLBlocks.DEAD_GRASS_BLOCK).keep(0.5).band(0.65, Blocks.ROOTED_DIRT).keepRest();
		palette(DLBiomes.DRY_WASTELAND, GRASSY_AND_SAND)
			.band(-0.4, Blocks.SAND).band(0.0, DLBlocks.DRY_DIRT).band(0.35, DLBlocks.CRACKED_EARTH).band(0.55, Blocks.COARSE_DIRT).rest(DLBlocks.DRY_DIRT);
		palette(DLBiomes.FLOODED_MARSH, GRASSY)
			.band(-0.25, Blocks.MUD).keep(0.35).band(0.5, Blocks.MUD).keepRest()
			.puddles(0.35);
		palette(DLBiomes.DEAD_PINE_VALLEY, GRASSY)
			.band(-0.4, Blocks.PODZOL).band(-0.1, Blocks.COARSE_DIRT).band(0.25, DLBlocks.DEAD_GRASS_BLOCK).keepRest();
		palette(DLBiomes.FROZEN_OUTSKIRTS, GRASSY)
			.band(-0.3, Blocks.COARSE_DIRT).band(0.3, DLBlocks.DEAD_GRASS_BLOCK).keepRest();
		palette(DLBiomes.INDUSTRIAL_WASTELAND, GRASSY_AND_SAND)
			.band(-0.5, Blocks.GRAVEL).band(-0.2, Blocks.COARSE_DIRT).band(0.05, DLBlocks.DEAD_GRASS_BLOCK).keep(0.25)
			.band(0.4, DLBlocks.RUBBLE).band(0.6, Blocks.GRAVEL).rest(DLBlocks.DEAD_GRASS_BLOCK);
		palette(DLBiomes.BURNED_FOREST, GRASSY)
			.band(-0.5, Blocks.COARSE_DIRT).band(0.4, DLBlocks.ASH).band(0.55, DLBlocks.DEAD_GRASS_BLOCK).rest(DLBlocks.ASH);
		palette(DLBiomes.RURAL_COUNTRYSIDE, GRASSY)
			.band(-0.55, Blocks.COARSE_DIRT).band(-0.35, DLBlocks.DEAD_GRASS_BLOCK).keepRest();
	}

	private record Band(double upTo, Optional<BlockState> state) {
	}

	private final Set<Block> replaces;
	private final List<Band> bands = new ArrayList<>();
	private double puddleThreshold = Double.POSITIVE_INFINITY;

	private SurfacePalette(Set<Block> replaces) {
		this.replaces = replaces;
	}

	private static SurfacePalette palette(ResourceKey<Biome> biome, Set<Block> replaces) {
		SurfacePalette palette = new SurfacePalette(replaces);
		BY_BIOME.put(biome, palette);
		return palette;
	}

	private SurfacePalette band(double upTo, Block block) {
		bands.add(new Band(upTo, Optional.of(block.defaultBlockState())));
		return this;
	}

	private SurfacePalette keep(double upTo) {
		bands.add(new Band(upTo, Optional.empty()));
		return this;
	}

	private SurfacePalette rest(Block block) {
		return band(Double.POSITIVE_INFINITY, block);
	}

	private SurfacePalette keepRest() {
		return keep(Double.POSITIVE_INFINITY);
	}

	/** Low ground at or below sea level becomes standing water where the detail noise exceeds this. */
	private SurfacePalette puddles(double threshold) {
		this.puddleThreshold = threshold;
		return this;
	}

	static SurfacePalette forBiome(Holder<Biome> biome) {
		return biome.unwrapKey().map(BY_BIOME::get).orElse(null);
	}

	boolean replaces(BlockState top) {
		return replaces.contains(top.getBlock());
	}

	boolean wantsPuddle(double detail, int y, int seaLevel) {
		return y <= seaLevel && detail > puddleThreshold;
	}

	/** The block to put on top, or empty to keep what is there. */
	Optional<BlockState> pick(double patch) {
		for (Band band : bands) {
			if (patch < band.upTo()) {
				return band.state();
			}
		}
		return Optional.empty();
	}
}
