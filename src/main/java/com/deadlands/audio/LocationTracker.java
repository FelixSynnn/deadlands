package com.deadlands.audio;

import com.deadlands.network.LocationPayload;
import com.deadlands.world.power.PowerGrid;
import com.deadlands.world.town.TownBuildingPiece;
import com.deadlands.world.town.TownLookup;
import com.deadlands.world.town.TownSite;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Tells each survivor's client what kind of place they are in, so the ambience can fit it: a suburb
 * or a city street, a hospital, a school, industry, a farm, a campsite, a bunker, underground, a
 * forest or open country, and whether the town around them has power. Worked out every two seconds
 * from the structures at the survivor's position (only the ones the chunk refers to, so it stays
 * cheap) and the biome, and sent when it changes.
 */
public final class LocationTracker {
	public enum Place {
		OPEN, FOREST, SUBURB, CITY, HOSPITAL, SCHOOL, INDUSTRIAL, FARM, CAMPSITE, BUNKER, UNDERGROUND;

		public static Place byIndex(int index) {
			Place[] places = values();
			return places[Math.floorMod(index, places.length)];
		}
	}

	private static final Map<UUID, Integer> SENT = new HashMap<>();

	private LocationTracker() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 40 == 29) {
				tick(server);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SENT.remove(handler.getPlayer().getUUID()));
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!ServerPlayNetworking.canSend(player, LocationPayload.TYPE)) {
				continue;
			}
			ServerLevel level = player.level();
			BlockPos pos = player.blockPosition();
			Place place = Place.OPEN;
			boolean powered = false;
			boolean underground = pos.getY() < level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) - 6 && !level.canSeeSky(pos);
			var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
			for (Map.Entry<Structure, it.unimi.dsi.fastutil.longs.LongSet> entry : level.structureManager().getAllStructuresAt(pos).entrySet()) {
				Identifier id = registry.getKey(entry.getKey());
				if (id == null || !id.getNamespace().equals("deadlands")) {
					continue;
				}
				Place found = switch (id.getPath()) {
					case "town" -> townPlace(level, pos, entry.getKey());
					case "bunker" -> Place.BUNKER;
					case "camp", "expedition_camp", "evacuation_camp", "forest_outpost", "roadside_shack" -> Place.CAMPSITE;
					case "farm_site" -> Place.FARM;
					case "utility_site", "power_station", "treatment_plant", "research_facility", "site_calder", "research_outpost" -> Place.INDUSTRIAL;
					case "school" -> Place.SCHOOL;
					default -> null;
				};
				if (found != null) {
					place = found;
					if (id.getPath().equals("town")) {
						TownSite town = TownLookup.nearest(level, pos, 200.0).orElse(null);
						powered = town != null && PowerGrid.isPowered(level, town);
					}
					break;
				}
			}
			if (place == Place.OPEN) {
				if (underground) {
					place = Place.UNDERGROUND;
				} else {
					String biome = level.getBiome(pos).unwrapKey().map(key -> key.identifier().getPath()).orElse("");
					if (biome.contains("forest") || biome.contains("pine") || biome.contains("taiga") || biome.contains("wood") || biome.contains("jungle")) {
						place = Place.FOREST;
					}
				}
			}
			int code = place.ordinal() * 2 + (powered ? 1 : 0);
			Integer last = SENT.put(player.getUUID(), code);
			if (last == null || last != code) {
				ServerPlayNetworking.send(player, new LocationPayload(place.ordinal(), powered));
			}
		}
	}

	private static Place townPlace(ServerLevel level, BlockPos pos, Structure town) {
		StructureStart start = level.structureManager().getStructureWithPieceAt(pos, town);
		if (start.isValid()) {
			for (StructurePiece piece : start.getPieces()) {
				if (piece instanceof TownBuildingPiece building && building.getBoundingBox().isInside(pos)) {
					String name = building.templateName();
					if (name.contains("clinic") || name.contains("emergency") || name.contains("pharmacy") || name.contains("hospital")) {
						return Place.HOSPITAL;
					}
					if (name.contains("school")) {
						return Place.SCHOOL;
					}
					if (name.contains("factory") || name.contains("garage") || name.contains("warehouse") || name.contains("works")) {
						return Place.INDUSTRIAL;
					}
					if (name.contains("barn") || name.contains("farmhouse")) {
						return Place.FARM;
					}
				}
			}
		}
		TownSite site = TownLookup.nearest(level, pos, 200.0).orElse(null);
		return site != null && site.size().major() ? Place.CITY : Place.SUBURB;
	}
}
