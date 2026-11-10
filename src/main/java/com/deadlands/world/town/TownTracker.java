package com.deadlands.world.town;

import com.deadlands.Deadlands;
import com.deadlands.config.ConfigManager;
import com.deadlands.network.DLNetworking;
import com.deadlands.network.TownCardPayload;
import com.deadlands.persistence.ChronicleEntry;
import com.deadlands.persistence.DeadlandsWorldData;
import com.deadlands.persistence.TownRecord;
import com.deadlands.world.WorldgenView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * Notices survivors walking into towns. The first survivor to reach a town discovers it (saved,
 * and written to the chronicle); everyone gets the town's card when they arrive, and again after
 * they have been away from it for a while.
 */
public final class TownTracker {
	private static final ResourceKey<Structure> TOWN = ResourceKey.create(Registries.STRUCTURE, Deadlands.id("town"));
	private static final int CHECK_INTERVAL = 20;
	/** A survivor who stays out of a town this long sees its card again on returning. */
	private static final long RESHOW_AFTER = 20L * 60 * 5;
	/** Landmark candidates by template, with how strongly each stands out (civic buildings first). */
	private static final Map<String, Integer> LANDMARKS = Map.ofEntries(
		Map.entry("police_station", 60), Map.entry("town_hall", 60), Map.entry("church", 55), Map.entry("clinic", 45),
		Map.entry("clinic_small", 40), Map.entry("pharmacy", 30), Map.entry("diner", 25), Map.entry("grocery", 25),
		Map.entry("corner_store", 20), Map.entry("hardware_store", 20), Map.entry("warehouse", 10), Map.entry("auto_garage", 8),
		Map.entry("auto_garage_large", 10), Map.entry("barn", 5), Map.entry("courthouse", 62), Map.entry("fire_station", 55),
		Map.entry("bank", 42), Map.entry("department_store", 35), Map.entry("saloon", 40), Map.entry("general_store", 25),
		Map.entry("motel", 20), Map.entry("office_block", 15), Map.entry("apartment_block", 6),
		Map.entry("library", 45), Map.entry("factory", 12));
	private static final Map<String, String> LANDMARK_NAMES = Map.of("clinic_small", "clinic", "auto_garage_large", "auto_garage");

	private record Seen(long town, long tick) {
	}

	private static final Map<UUID, Seen> SEEN = new HashMap<>();

	private TownTracker() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(TownTracker::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SEEN.remove(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SEEN.clear());
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_INTERVAL != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		Structure town = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(TOWN);
		if (town == null) {
			return;
		}
		long now = server.getTickCount();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			StructureStart start = level.structureManager().getStructureAt(player.blockPosition(), town);
			if (!start.isValid()) {
				continue;
			}
			ChunkPos chunk = start.getChunkPos();
			long key = ChunkPos.asLong(chunk.x, chunk.z);
			Seen seen = SEEN.get(player.getUUID());
			SEEN.put(player.getUUID(), new Seen(key, now));
			if (seen != null && seen.town() == key && now - seen.tick() < RESHOW_AFTER) {
				continue;
			}
			site(level, chunk).ifPresent(site -> arrive(server, level, player, start, site, key));
		}
	}

	private static Optional<TownSite> site(ServerLevel level, ChunkPos chunk) {
		WorldgenView view = WorldgenView.of(level, level.getChunkSource().getGenerator());
		Optional<RandomSpreadStructurePlacement> placement = TownSites.placement(view);
		return placement.flatMap(p -> TownSites.startingIn(view, p, chunk));
	}

	private static void arrive(MinecraftServer server, ServerLevel level, ServerPlayer player, StructureStart start, TownSite site, long key) {
		TownIdentity identity = TownIdentity.of(level.getSeed(), site);
		DeadlandsWorldData data = DeadlandsWorldData.get(server);
		long day = level.getDayCount() + 1;
		String who = player.getName().getString();
		boolean first = data.discoverTown(key, new TownRecord(identity.name(), day, who, site.centerX(), site.centerZ(),
			site.size().id(), site.condition().id()));
		if (first) {
			data.addChronicle(new ChronicleEntry(day, "chronicle.deadlands.town_found", List.of(who, identity.name())),
				ConfigManager.get().general.chronicleLength);
			DLNetworking.broadcastStatus(server);
		}
		DLNetworking.sendTownCard(player, new TownCardPayload(identity.name(), site.size().id(), site.condition().id(), identity.population(),
			identity.factionKey(), identity.factionArg(), identity.historyKey(), landmark(start, site), identity.lootKey(), identity.storyKey(), first,
			com.deadlands.world.power.PowerGrid.isPowered(level, site)));
	}

	/** The most prominent building that actually stands in the town, nearest the centre first. */
	private static String landmark(StructureStart start, TownSite site) {
		String best = "";
		double bestScore = Double.MAX_VALUE;
		for (StructurePiece piece : start.getPieces()) {
			if (!(piece instanceof TownBuildingPiece building)) {
				continue;
			}
			String template = building.templateName();
			String base = template.substring(template.lastIndexOf('/') + 1);
			if (!LANDMARKS.containsKey(base) && base.matches(".*_[a-z]")) {
				base = base.substring(0, base.length() - 2); // seeded variants: police_station_b is a police station
			}
			Integer weight = LANDMARKS.get(base);
			if (weight == null) {
				continue;
			}
			BoundingBox box = piece.getBoundingBox();
			double distance = Math.hypot((box.minX() + box.maxX()) / 2.0 - site.centerX(), (box.minZ() + box.maxZ()) / 2.0 - site.centerZ());
			double score = distance - weight;
			if (score < bestScore) {
				bestScore = score;
				best = "town.deadlands.landmark." + LANDMARK_NAMES.getOrDefault(base, base);
			}
		}
		return best;
	}
}
