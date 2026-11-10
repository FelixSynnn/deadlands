package com.deadlands.world.road;

import com.deadlands.world.WorldgenView;
import com.deadlands.world.town.TownSite;
import com.deadlands.world.town.TownSites;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * The roads between towns. Towns are the nodes; two towns are linked when no third town is closer
 * to both of them than they are to each other (a relative neighbourhood graph). That gives every
 * town two to four roads, never a long road that duplicates a shorter detour, and the same answer
 * from any chunk that asks, so roads are generated one chunk at a time without seams.
 *
 * <p>Instances are cached per world; road geometry is computed once per road and shared.
 */
public final class RoadNetwork {
	private static final Map<Long, RoadNetwork> NETWORKS = new ConcurrentHashMap<>();
	/** How many placement regions away a linked town may be. */
	private static final int REACH = 2;

	private final WorldgenView view;
	private final RandomSpreadStructurePlacement placement;
	private final double maxLength;
	private final Map<Long, List<Long>> edgesFromRegion = new ConcurrentHashMap<>();
	private final Map<Long, RoadEdge> edges = new ConcurrentHashMap<>();

	private RoadNetwork(WorldgenView view, RandomSpreadStructurePlacement placement) {
		this.view = view;
		this.placement = placement;
		this.maxLength = placement.spacing() * 16 * 2.5;
	}

	/** The network of the world being generated, or empty if towns are not configured. */
	public static Optional<RoadNetwork> of(WorldgenView view) {
		RoadNetwork cached = NETWORKS.get(view.cacheKey());
		if (cached != null) {
			return Optional.of(cached);
		}
		return TownSites.placement(view).map(placement -> NETWORKS.computeIfAbsent(view.cacheKey(), key -> new RoadNetwork(view, placement)));
	}

	public static void clearCaches() {
		NETWORKS.clear();
	}

	public WorldgenView view() {
		return view;
	}

	public RandomSpreadStructurePlacement placement() {
		return placement;
	}

	public Optional<TownSite> town(int regionX, int regionZ) {
		return TownSites.inRegion(view, placement, regionX, regionZ);
	}

	/** Every road that leaves the given town. */
	public List<RoadEdge> roadsOf(TownSite town) {
		List<RoadEdge> out = new ArrayList<>();
		for (int dx = -REACH; dx <= REACH; dx++) {
			for (int dz = -REACH; dz <= REACH; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				Optional<TownSite> other = town(town.regionX() + dx, town.regionZ() + dz);
				if (other.isPresent() && linked(town, other.get())) {
					out.add(edge(town, other.get()));
				}
			}
		}
		return out;
	}

	/** Every road whose corridor overlaps the given chunk. */
	public List<RoadEdge> roadsNear(int chunkX, int chunkZ) {
		int spacing = placement.spacing();
		int regionX = Math.floorDiv(chunkX, spacing);
		int regionZ = Math.floorDiv(chunkZ, spacing);
		List<RoadEdge> out = new ArrayList<>();
		for (int dx = -REACH; dx <= REACH; dx++) {
			for (int dz = -REACH; dz <= REACH; dz++) {
				for (long key : edgesFrom(regionX + dx, regionZ + dz)) {
					RoadEdge edge = edges.get(key);
					if (edge != null && edge.touchesChunk(chunkX, chunkZ)) {
						out.add(edge);
					}
				}
			}
		}
		return out;
	}

	/** Keys of the roads whose first town (in key order) is in this region; builds their geometry. */
	private List<Long> edgesFrom(int regionX, int regionZ) {
		long regionKey = net.minecraft.world.level.ChunkPos.asLong(regionX, regionZ);
		List<Long> cached = edgesFromRegion.get(regionKey);
		if (cached != null) {
			return cached;
		}
		List<Long> keys = new ArrayList<>();
		Optional<TownSite> here = town(regionX, regionZ);
		if (here.isPresent()) {
			for (int dx = -REACH; dx <= REACH; dx++) {
				for (int dz = -REACH; dz <= REACH; dz++) {
					if (dx == 0 && dz == 0) {
						continue;
					}
					Optional<TownSite> other = town(regionX + dx, regionZ + dz);
					if (other.isPresent() && here.get().key() < other.get().key() && linked(here.get(), other.get())) {
						keys.add(edge(here.get(), other.get()).key());
					}
				}
			}
		}
		edgesFromRegion.put(regionKey, keys);
		return keys;
	}

	private RoadEdge edge(TownSite a, TownSite b) {
		TownSite first = a.key() < b.key() ? a : b;
		TownSite second = first == a ? b : a;
		long key = first.key() * 31L + second.key();
		return edges.computeIfAbsent(key, k -> RoadEdge.build(view, k, first, second));
	}

	/** Relative neighbourhood test: no third town is closer to both towns than they are to each other. */
	private boolean linked(TownSite a, TownSite b) {
		double ab = a.distanceTo(b);
		if (ab > maxLength) {
			return false;
		}
		int minX = Math.min(a.regionX(), b.regionX()) - 1;
		int maxX = Math.max(a.regionX(), b.regionX()) + 1;
		int minZ = Math.min(a.regionZ(), b.regionZ()) - 1;
		int maxZ = Math.max(a.regionZ(), b.regionZ()) + 1;
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				Optional<TownSite> c = town(x, z);
				if (c.isEmpty() || c.get().key() == a.key() || c.get().key() == b.key()) {
					continue;
				}
				if (Math.max(a.distanceTo(c.get()), b.distanceTo(c.get())) < ab) {
					return false;
				}
			}
		}
		return true;
	}
}
