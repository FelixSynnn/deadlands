package com.deadlands.world.town;

import com.deadlands.world.WorldgenView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * Server-side questions about where towns are (for spawning and migrating hordes). Uses the same
 * deterministic, cached site evaluation as world generation, so it never loads chunks.
 */
public final class TownLookup {
	private TownLookup() {
	}

	/** The nearest town whose centre is within {@code maxDistance} blocks of {@code pos}. */
	public static Optional<TownSite> nearest(ServerLevel level, BlockPos pos, double maxDistance) {
		TownSite best = null;
		double bestSqr = maxDistance * maxDistance;
		for (TownSite site : around(level, pos, 1)) {
			double dx = site.centerX() - pos.getX();
			double dz = site.centerZ() - pos.getZ();
			double d = dx * dx + dz * dz;
			if (d <= bestSqr) {
				bestSqr = d;
				best = site;
			}
		}
		return Optional.ofNullable(best);
	}

	/** Every town in the placement regions within {@code reach} regions of {@code pos}. */
	public static List<TownSite> around(ServerLevel level, BlockPos pos, int reach) {
		WorldgenView view = WorldgenView.of(level, level.getChunkSource().getGenerator());
		Optional<RandomSpreadStructurePlacement> placement = TownSites.placement(view);
		if (placement.isEmpty()) {
			return List.of();
		}
		int spacing = placement.get().spacing();
		int regionX = Math.floorDiv(pos.getX() >> 4, spacing);
		int regionZ = Math.floorDiv(pos.getZ() >> 4, spacing);
		List<TownSite> sites = new ArrayList<>();
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				TownSites.inRegion(view, placement.get(), regionX + dx, regionZ + dz).ifPresent(sites::add);
			}
		}
		return sites;
	}
}
