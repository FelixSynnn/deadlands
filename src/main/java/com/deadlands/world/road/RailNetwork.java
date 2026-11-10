package com.deadlands.world.road;

import com.deadlands.world.Hashing;
import com.deadlands.world.WorldgenView;
import com.deadlands.world.town.TownSite;
import com.deadlands.world.town.TownSize;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.ChunkPos;

/**
 * The railway network: a subset of the road links, between towns that are not mere settlements
 * (about four in ten of those links get a line). Like roads, every decision is a pure function of
 * the seed and cached per world, so lines are generated chunk by chunk without seams.
 */
public final class RailNetwork {
	private static final Map<Long, RailNetwork> NETWORKS = new ConcurrentHashMap<>();
	private static final int REACH = 3;
	private static final double LINE_SHARE = 0.4;

	private final RoadNetwork roads;
	private final Map<Long, List<RailEdge>> byRegion = new ConcurrentHashMap<>();

	private RailNetwork(RoadNetwork roads) {
		this.roads = roads;
	}

	public static Optional<RailNetwork> of(WorldgenView view) {
		RailNetwork cached = NETWORKS.get(view.cacheKey());
		if (cached != null) {
			return Optional.of(cached);
		}
		return RoadNetwork.of(view).map(roads -> NETWORKS.computeIfAbsent(view.cacheKey(), key -> new RailNetwork(roads)));
	}

	public static void clearCaches() {
		NETWORKS.clear();
	}

	/** Every line whose corridor overlaps the given chunk. */
	public List<RailEdge> railsNear(int chunkX, int chunkZ) {
		int spacing = roads.placement().spacing();
		int regionX = Math.floorDiv(chunkX, spacing);
		int regionZ = Math.floorDiv(chunkZ, spacing);
		List<RailEdge> out = new ArrayList<>();
		for (int dx = -REACH; dx <= REACH; dx++) {
			for (int dz = -REACH; dz <= REACH; dz++) {
				for (RailEdge edge : railsFrom(regionX + dx, regionZ + dz)) {
					if (edge.touchesChunk(chunkX, chunkZ)) {
						out.add(edge);
					}
				}
			}
		}
		return out;
	}

	/** Lines whose first town (in key order) is in this region. */
	private List<RailEdge> railsFrom(int regionX, int regionZ) {
		long regionKey = ChunkPos.asLong(regionX, regionZ);
		List<RailEdge> cached = byRegion.get(regionKey);
		if (cached != null) {
			return cached;
		}
		List<RailEdge> lines = new ArrayList<>();
		Optional<TownSite> here = roads.town(regionX, regionZ);
		if (here.isPresent() && here.get().size() != TownSize.SETTLEMENT) {
			TownSite town = here.get();
			for (RoadEdge road : roads.roadsOf(town)) {
				TownSite other = road.from().key() == town.key() ? road.to() : road.from();
				if (town.key() >= other.key() || other.size() == TownSize.SETTLEMENT) {
					continue;
				}
				long key = town.key() * 37L + other.key();
				if (Hashing.unit(Hashing.mix(key ^ roads.view().seed() ^ 0x7A11L)) < LINE_SHARE) {
					lines.add(RailEdge.build(roads.view(), key, town, other));
				}
			}
		}
		byRegion.put(regionKey, lines);
		return lines;
	}
}
