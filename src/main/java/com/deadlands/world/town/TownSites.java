package com.deadlands.world.town;

import com.deadlands.Deadlands;
import com.deadlands.world.Hashing;
import com.deadlands.world.WorldgenView;
import com.deadlands.world.biome.DLBiomes;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * Where towns stand. The town structure set (data/deadlands/worldgen/structure_set/towns.json)
 * places one candidate per region with vanilla random spread; this class reads that placement and
 * applies the one test both the town structure and the road network use: the centre must be dry
 * land in a town biome, and the ground around it neither flooded nor steep. Results are cached
 * per world, because roads ask about the same towns from many chunks.
 */
public final class TownSites {
	public static final ResourceKey<StructureSet> TOWN_SET = ResourceKey.create(Registries.STRUCTURE_SET, Deadlands.id("towns"));
	private static final Map<Long, Map<Long, Optional<TownSite>>> CACHE = new ConcurrentHashMap<>();

	private TownSites() {
	}

	public static Optional<RandomSpreadStructurePlacement> placement(WorldgenView view) {
		StructureSet set = view.registries().lookupOrThrow(Registries.STRUCTURE_SET).getValue(TOWN_SET);
		if (set != null && set.placement() instanceof RandomSpreadStructurePlacement spread) {
			return Optional.of(spread);
		}
		return Optional.empty();
	}

	/** The town founded in placement region (regionX, regionZ), if any. */
	public static Optional<TownSite> inRegion(WorldgenView view, RandomSpreadStructurePlacement placement, int regionX, int regionZ) {
		Map<Long, Optional<TownSite>> sites = CACHE.computeIfAbsent(view.cacheKey(), key -> new ConcurrentHashMap<>());
		long key = ChunkPos.asLong(regionX, regionZ);
		Optional<TownSite> site = sites.get(key);
		if (site == null) {
			site = evaluate(view, placement, regionX, regionZ);
			sites.put(key, site);
		}
		return site;
	}

	/** The town whose structure starts in this chunk, if any. */
	public static Optional<TownSite> startingIn(WorldgenView view, RandomSpreadStructurePlacement placement, ChunkPos chunk) {
		int spacing = placement.spacing();
		return inRegion(view, placement, Math.floorDiv(chunk.x, spacing), Math.floorDiv(chunk.z, spacing))
			.filter(site -> site.chunkX() == chunk.x && site.chunkZ() == chunk.z);
	}

	/** Height of a gate: where a town's main street ends and a road begins. Never below sea level. */
	public static int gateHeight(WorldgenView view, int x, int z) {
		return Math.max(view.surface(x, z), view.seaLevel());
	}

	private static Optional<TownSite> evaluate(WorldgenView view, RandomSpreadStructurePlacement placement, int regionX, int regionZ) {
		int spacing = placement.spacing();
		ChunkPos chunk = placement.getPotentialStructureChunk(view.seed(), regionX * spacing, regionZ * spacing);
		int x = chunk.getMiddleBlockX();
		int z = chunk.getMiddleBlockZ();
		int y = view.surface(x, z);
		int sea = view.seaLevel();
		if (y < sea || !view.biome(x, y, z).is(DLBiomes.HAS_TOWN)) {
			return Optional.empty();
		}
		long hash = Hashing.of(view.seed(), chunk.x, chunk.z, 0x70A1L);
		TownSize size = TownSize.pick(hash);
		TownCondition condition = TownCondition.pick(Hashing.mix(hash ^ 0xC0D1L));
		int ring = size.radius() * 2 / 3;
		int flooded = 0;
		int steepest = 0;
		for (int i = 0; i < 8; i++) {
			double angle = i * Math.PI / 4.0;
			int sx = x + (int) Math.round(Math.cos(angle) * ring);
			int sz = z + (int) Math.round(Math.sin(angle) * ring);
			int sy = view.surface(sx, sz);
			if (sy < sea) {
				flooded++;
			}
			steepest = Math.max(steepest, Math.abs(sy - y));
		}
		if (flooded > 2 || steepest > 16) {
			return Optional.empty();
		}
		return Optional.of(new TownSite(regionX, regionZ, chunk.x, chunk.z, x, y, z, size, condition));
	}

	public static void clearCaches() {
		CACHE.clear();
	}
}
