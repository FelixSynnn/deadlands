package com.deadlands.world;

import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.core.Holder;

/**
 * Everything town and road generation needs to look at the world before it exists: the seed, the
 * chunk generator's terrain and biome functions, and the registries. Both a structure's
 * generation context and a feature's placement context can build one, and every town and road
 * decision is a pure function of it, so a town planned in one chunk and a road rasterised in
 * another always agree.
 */
public record WorldgenView(long seed, ChunkGenerator generator, RandomState randomState, LevelHeightAccessor heights, RegistryAccess registries) {
	public static WorldgenView of(Structure.GenerationContext context) {
		return new WorldgenView(context.seed(), context.chunkGenerator(), context.randomState(), stable(context.heightAccessor()), context.registryAccess());
	}

	public static WorldgenView of(WorldGenLevel level, ChunkGenerator generator) {
		return new WorldgenView(level.getSeed(), generator, level.getLevel().getChunkSource().randomState(), stable(level), level.registryAccess());
	}

	/** Copies just the height range, so cached views never keep a transient chunk region alive. */
	private static LevelHeightAccessor stable(LevelHeightAccessor heights) {
		return LevelHeightAccessor.create(heights.getMinY(), heights.getHeight());
	}

	/** Y of the topmost block (water counts), as terrain is generated, before any decoration. */
	public int surface(int x, int z) {
		return generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heights, randomState) - 1;
	}

	/** Y of the topmost solid block (the bed under any water). */
	public int ground(int x, int z) {
		return generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heights, randomState) - 1;
	}

	public int seaLevel() {
		return generator.getSeaLevel();
	}

	public Holder<Biome> biome(int x, int y, int z) {
		return generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), randomState.sampler());
	}

	/** Identifies the world being generated, for caches (the generator instance belongs to one world). */
	public long cacheKey() {
		return seed * 31L + System.identityHashCode(generator);
	}
}
