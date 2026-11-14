package com.deadlands;

import com.deadlands.command.DeadlandsCommand;
import com.deadlands.config.ConfigManager;
import com.deadlands.network.DLNetworking;
import com.deadlands.player.SurvivorTracker;
import com.deadlands.progression.WorldClock;
import com.deadlands.registry.DLBlockEntities;
import com.deadlands.registry.DLBlocks;
import com.deadlands.registry.DLContainers;
import com.deadlands.registry.DLCreativeTabs;
import com.deadlands.registry.DLDataComponents;
import com.deadlands.registry.DLDecor;
import com.deadlands.registry.DLEntities;
import com.deadlands.registry.DLNests;
import com.deadlands.registry.DLMenus;
import com.deadlands.registry.DLItems;
import com.deadlands.registry.DLSounds;
import com.deadlands.registry.DLSurvivalItems;
import com.deadlands.registry.DLWeapons;
import com.deadlands.skill.Skills;
import com.deadlands.survival.SurvivalSystem;
import com.deadlands.spawning.InfectedSystems;
import com.deadlands.world.DLWorldgen;
import com.deadlands.world.town.TownTracker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DEADLANDS common entrypoint (runs on both the client and the dedicated server).
 *
 * <p>Boot order matters: config first (everything reads it), then registries (content before the
 * creative tabs that list it), then networking and the server-side systems that use them.
 */
public final class Deadlands implements ModInitializer {
	public static final String MOD_ID = "deadlands";
	public static final Logger LOGGER = LoggerFactory.getLogger("DEADLANDS");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static String version() {
		return FabricLoader.getInstance().getModContainer(MOD_ID)
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("dev");
	}

	@Override
	public void onInitialize() {
		long start = System.nanoTime();
		ConfigManager.load();

		DLSounds.init();
		DLDataComponents.init();
		DLItems.init();
		DLSurvivalItems.init();
		DLWeapons.init();
		DLBlocks.init();
		DLDecor.init();
		com.deadlands.registry.DLLights.init();
		com.deadlands.registry.DLInteriors.init();
		DLNests.init();
		DLContainers.init();
		DLBlockEntities.init();
		DLMenus.init();
		DLEntities.init();
		com.deadlands.registry.DLMarkers.init();
		com.deadlands.registry.DLBosses.init();
		com.deadlands.registry.DLAsesino.init();
		com.deadlands.registry.DLGuest.init();
		com.deadlands.registry.DLDot.init();
		com.deadlands.registry.DLMythos.init();
		com.deadlands.world.disaster.MythosTornado.init();
		com.deadlands.registry.DLVehicles.init();
		com.deadlands.registry.DLStory.init();
		com.deadlands.registry.DLParticles.init();
		com.deadlands.registry.DLEvents.init();
		com.deadlands.world.subway.SubwayStructure.register(); // before the worldgen data loads
		DLWorldgen.init();
		DLCreativeTabs.init();

		DLNetworking.init();
		WorldClock.init();
		SurvivorTracker.init();
		TownTracker.init();
		InfectedSystems.init();
		Skills.init();
		com.deadlands.npc.Reputation.init();
		com.deadlands.npc.NpcSpawns.init();
		com.deadlands.npc.NpcDialogue.init();
		com.deadlands.npc.Settlements.init();
		com.deadlands.npc.Access.init();
		com.deadlands.progression.Tasks.init();
		com.deadlands.story.Archive.init();
		com.deadlands.story.StoryDirector.init();
		com.deadlands.story.StoryIntro.init();
		com.deadlands.story.StoryReunion.init();
		com.deadlands.story.FamilyHouse.init();
		com.deadlands.story.HarleyRoad.init();
		com.deadlands.story.MythosTerminal.init();
		com.deadlands.story.Pinecrest.init();
		com.deadlands.player.SafeRespawn.init();
		com.deadlands.item.FlashlightBeam.init();
		com.deadlands.player.RestlessNights.init();
		com.deadlands.story.StoryArchive.init();
		com.deadlands.world.subway.SubwayAmbience.init();
		com.deadlands.world.disaster.DisasterDirector.init();
		com.deadlands.entity.AsesinoSpawner.init();
		com.deadlands.entity.GuestSpawner.init();
		com.deadlands.entity.DotSpawner.init();
		com.deadlands.story.Radio.init();
		com.deadlands.horde.BossDirector.init();
		com.deadlands.story.Endings.init();
		com.deadlands.world.weather.WeatherDirector.init();
		com.deadlands.world.VanillaMobs.init();
		com.deadlands.item.Flashlights.init();
		com.deadlands.audio.ChaseTracker.init();
		com.deadlands.content.DLContent.init();
		com.deadlands.horror.HorrorDirector.init();
		com.deadlands.horror.DarkBuildings.init();
		com.deadlands.mission.MissionManager.init();
		com.deadlands.achievement.Achievements.init();
		com.deadlands.vehicle.VehicleEntity.init();
		com.deadlands.event.WorldEvents.init();
		com.deadlands.audio.LocationTracker.init();
		com.deadlands.cutscene.Cutscenes.init();
		SurvivalSystem.init();
		DeadlandsCommand.init();

		LOGGER.info("DEADLANDS {} ready in {} ms: {} blocks, {} items, {} entity types, {} sounds, {} creative tabs, config at {}",
			version(), (System.nanoTime() - start) / 1_000_000L, DLItems.blockItems().size(), DLItems.all().size(),
			com.deadlands.entity.InfectedKind.values().length, DLSounds.all().size(), DLCreativeTabs.registered().size(), ConfigManager.path());
	}
}
