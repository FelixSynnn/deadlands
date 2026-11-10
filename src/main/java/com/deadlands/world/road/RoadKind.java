package com.deadlands.world.road;

import com.deadlands.world.town.TownSite;
import com.deadlands.world.town.TownSize;

/**
 * What kind of road links two towns: four-lane highways between large towns and cities, gravel
 * and dirt roads between farming settlements, ordinary two-lane roads otherwise. Widths are half
 * widths in blocks from the centre line (asphalt, then the shoulder). Every road narrows to the
 * ordinary width near a town gate, where it meets the town's own streets.
 */
public enum RoadKind {
	HIGHWAY(4.5F, 6.2F),
	ROAD(2.5F, 3.6F),
	DIRT(1.6F, 2.6F);

	private final float paved;
	private final float shoulder;

	RoadKind(float paved, float shoulder) {
		this.paved = paved;
		this.shoulder = shoulder;
	}

	public float paved() {
		return paved;
	}

	public float shoulder() {
		return shoulder;
	}

	public static RoadKind between(TownSite a, TownSite b) {
		if (a.size().major() && b.size().major()) {
			return HIGHWAY;
		}
		if (a.size() == TownSize.SETTLEMENT && b.size() == TownSize.SETTLEMENT) {
			return DIRT;
		}
		return ROAD;
	}
}
