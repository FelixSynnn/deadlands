package com.deadlands.story;

import com.deadlands.Deadlands;
import com.deadlands.world.town.TownBuildingPiece;
import com.deadlands.world.town.TownLookup;
import com.deadlands.world.town.TownSite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jspecify.annotations.Nullable;

/**
 * The places the story sends survivors to, how to tell when one is standing in one, and how to find
 * the nearest. Sites are their own structures ({@code deadlands:research_facility},
 * {@code site_calder}, {@code evacuation_camp}, {@code expedition_camp}), each in a structure tag
 * {@code deadlands:story/<place>} for finding; a hospital is any clinic, pharmacy or emergency room
 * building in a town, and the nearest is looked for in the nearest large town or city.
 */
public final class StoryPlaces {
	public enum Place {
		HOSPITAL("hospital", ""),
		RESEARCH_FACILITY("research_facility", "research_facility"),
		EVACUATION_SITE("evacuation_site", "evacuation_camp"),
		EXPEDITION_CAMP("expedition_camp", "expedition_camp"),
		MILITARY_FACILITY("military_facility", "site_calder");

		private final String id;
		private final String structure;

		Place(String id, String structure) {
			this.id = id;
			this.structure = structure;
		}

		public String id() {
			return id;
		}

		public Component displayName() {
			return Component.translatable("story.deadlands.place." + id);
		}

		@Nullable ResourceKey<Structure> structureKey() {
			return structure.isEmpty() ? null : ResourceKey.create(Registries.STRUCTURE, Deadlands.id(structure));
		}

		TagKey<Structure> tag() {
			return TagKey.create(Registries.STRUCTURE, Deadlands.id("story/" + id));
		}
	}

	private static final ResourceKey<Structure> TOWN = ResourceKey.create(Registries.STRUCTURE, Deadlands.id("town"));

	private StoryPlaces() {
	}

	/** The story place {@code pos} is inside, or null. */
	public static @Nullable Place at(ServerLevel level, BlockPos pos) {
		var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (Place place : Place.values()) {
			ResourceKey<Structure> key = place.structureKey();
			Structure structure = key == null ? null : structures.getValue(key);
			if (structure != null && level.structureManager().getStructureWithPieceAt(pos, structure).isValid()) {
				return place;
			}
		}
		Structure town = structures.getValue(TOWN);
		if (town != null) {
			StructureStart start = level.structureManager().getStructureWithPieceAt(pos, town);
			if (start.isValid()) {
				for (StructurePiece piece : start.getPieces()) {
					if (piece instanceof TownBuildingPiece building && building.getBoundingBox().isInside(pos)) {
						String name = building.templateName();
						if (name.contains("clinic") || name.contains("emergency") || name.contains("pharmacy") || name.contains("hospital")) {
							return Place.HOSPITAL;
						}
					}
				}
			}
		}
		return null;
	}

	/** The nearest place of a kind to {@code from}, or null if none is within about 1600 blocks. Expensive: call rarely. */
	public static @Nullable BlockPos locate(ServerLevel level, Place place, BlockPos from) {
		if (place == Place.HOSPITAL) {
			TownSite best = null;
			double bestSqr = Double.MAX_VALUE;
			for (TownSite town : TownLookup.around(level, from, 3)) {
				if (!town.size().major()) {
					continue;
				}
				double d = town.center().distSqr(from);
				if (d < bestSqr) {
					bestSqr = d;
					best = town;
				}
			}
			return best == null ? null : best.center();
		}
		return level.findNearestMapStructure(place.tag(), from, 100, false);
	}
}
