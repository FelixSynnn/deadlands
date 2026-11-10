package com.deadlands.world.town;

import com.deadlands.config.ConfigManager;
import com.deadlands.world.DLWorldgen;
import com.deadlands.world.WorldgenView;
import com.deadlands.world.road.RoadEdge;
import com.deadlands.world.road.RoadNetwork;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * A town. The structure set places a candidate in every region; this structure starts only where
 * {@link TownSites} accepts the site, so it agrees with the road network about which towns exist.
 * The layout itself is {@link TownPlanner}'s job.
 *
 * <p>Data ({@code data/deadlands/worldgen/structure/town.json}) lists the building templates per
 * category (residential, commercial, civic, medical, industrial, rural), so datapacks can add
 * buildings without code.
 */
public class TownStructure extends Structure {
	public static final MapCodec<TownStructure> CODEC = RecordCodecBuilder.<TownStructure>mapCodec(instance -> instance.group(
		settingsCodec(instance),
		Codec.unboundedMap(Codec.STRING, Identifier.CODEC.listOf()).fieldOf("buildings").forGetter(structure -> structure.buildings)
	).apply(instance, TownStructure::new));

	private final Map<String, List<Identifier>> buildings;

	public TownStructure(Structure.StructureSettings settings, Map<String, List<Identifier>> buildings) {
		super(settings);
		this.buildings = buildings;
	}

	@Override
	protected Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
		WorldgenView view = WorldgenView.of(context);
		Optional<RoadNetwork> network = RoadNetwork.of(view);
		if (network.isEmpty()) {
			return Optional.empty();
		}
		Optional<TownSite> site = TownSites.startingIn(view, network.get().placement(), context.chunkPos());
		if (site.isEmpty()) {
			return Optional.empty();
		}
		TownSite town = site.get();
		List<RoadEdge> roads = ConfigManager.get().world.roadNetwork ? network.get().roadsOf(town) : List.of();
		return Optional.of(new Structure.GenerationStub(town.center(),
			builder -> TownPlanner.plan(builder, context.structureTemplateManager(), view, town, roads, buildings)));
	}

	/** Building templates per category, as listed in the structure's data. */
	public Map<String, List<Identifier>> buildings() {
		return buildings;
	}

	@Override
	public StructureType<?> type() {
		return DLWorldgen.TOWN;
	}
}
