package com.deadlands.world.feature;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;

/**
 * Repaints the top block of every column in a chunk with its biome's ground (see
 * {@link SurfacePalette}): ash in the Burned Forest, cracked earth in the Dry Wasteland, mud and
 * standing water in the Flooded Marsh, gravel and rubble in the Industrial Wasteland.
 *
 * <p>Runs once per chunk in the local-modifications step, before vegetation, so plants and trees
 * root in the new ground. It only writes inside its own chunk and the noise is in world space and
 * seeded by the world seed, so patches continue seamlessly across chunk borders.
 */
public class SurfaceCoverFeature extends Feature<NoneFeatureConfiguration> {
	/** Noise for one world seed; one immutable holder so threads never mix a seed with another's noise. */
	private record NoiseSet(long seed, PerlinSimplexNoise[] noise) {
	}

	private static volatile NoiseSet cached;

	public SurfaceCoverFeature() {
		super(NoneFeatureConfiguration.CODEC);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel level = context.level();
		BlockPos origin = context.origin();
		int minX = SectionPos.blockToSectionCoord(origin.getX()) << 4;
		int minZ = SectionPos.blockToSectionCoord(origin.getZ()) << 4;
		PerlinSimplexNoise[] noise = noise(level.getSeed());
		RandomSource random = context.random();
		int seaLevel = context.chunkGenerator().getSeaLevel();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean changed = false;
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = minX + dx;
				int z = minZ + dz;
				int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
				pos.set(x, y, z);
				BlockState top = level.getBlockState(pos);
				SurfacePalette palette = SurfacePalette.forBiome(level.getBiome(pos));
				if (palette == null || !palette.replaces(top)) {
					continue;
				}
				double detail = noise[1].getValue(x * 0.11, z * 0.11, false);
				if (palette.wantsPuddle(detail, y, seaLevel) && canHoldWater(level, pos)) {
					level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
					changed = true;
					continue;
				}
				double patch = noise[0].getValue(x * 0.035, z * 0.035, false) + (random.nextDouble() - 0.5) * 0.16;
				Optional<BlockState> cover = palette.pick(patch);
				if (cover.isPresent() && !cover.get().is(top.getBlock())) {
					level.setBlock(pos, cover.get(), Block.UPDATE_CLIENTS);
					changed = true;
				}
			}
		}
		return changed;
	}

	/** A puddle needs solid ground under it and on all four sides, so the water cannot run off. */
	private static boolean canHoldWater(WorldGenLevel level, BlockPos pos) {
		if (!level.getBlockState(pos.below()).isSolid()) {
			return false;
		}
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockState neighbour = level.getBlockState(pos.relative(side));
			if (!neighbour.isSolid() && !neighbour.is(Blocks.WATER)) {
				return false;
			}
		}
		return level.getBlockState(pos.above()).isAir();
	}

	private static PerlinSimplexNoise[] noise(long seed) {
		NoiseSet set = cached;
		if (set == null || set.seed() != seed) {
			set = new NoiseSet(seed, new PerlinSimplexNoise[] {
				new PerlinSimplexNoise(new WorldgenRandom(new LegacyRandomSource(seed ^ 0x5EED_C0FFEEL)), List.of(0, 1, 2)),
				new PerlinSimplexNoise(new WorldgenRandom(new LegacyRandomSource(seed ^ 0x0DD_BA11L)), List.of(0, 1))
			});
			cached = set;
		}
		return set.noise();
	}
}
