package com.deadlands.world;

import com.deadlands.Deadlands;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Hard Rule 2: no vanilla creature exists in DEADLANDS, by any route. Every creature a survivor
 * sees is one of ours.
 *
 * <ul>
 *   <li><b>Natural spawning:</b> every vanilla entry is removed from every biome's spawn list
 *       (vanilla biomes such as oceans and peaks included), and the vanilla dungeon features are
 *       removed from every biome.</li>
 *   <li><b>Structures</b> that bring vanilla mobs (villages, outposts, monuments, mansions, ancient
 *       cities, strongholds, trial chambers, mineshafts...) are disabled by empty biome tags
 *       ({@code gen_immersion.py}).</li>
 *   <li><b>Events:</b> raids, patrols, phantoms and wandering traders are switched off whenever a
 *       server starts.</li>
 *   <li><b>Everything else</b> (spawners, spawn eggs, golems and the wither, breeding, conversions,
 *       {@code /summon}) is refused when it tries to enter a level ({@code ServerLevelMixin}), and
 *       vanilla mobs already saved in a world are removed as they load.</li>
 *   <li><b>Creative:</b> vanilla spawn eggs are taken out of the Spawn Eggs tab.</li>
 * </ul>
 */
public final class VanillaMobs {
	private static final ResourceKey<PlacedFeature> MONSTER_ROOM = vanillaFeature("monster_room");
	private static final ResourceKey<PlacedFeature> MONSTER_ROOM_DEEP = vanillaFeature("monster_room_deep");

	private VanillaMobs() {
	}

	/** True for any vanilla mob (hostile, passive, ambient, aquatic, golem, villager...). Players are not mobs. */
	public static boolean banned(Entity entity) {
		return entity instanceof Mob && vanilla(entity.getType());
	}

	private static boolean vanilla(EntityType<?> type) {
		return "minecraft".equals(BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace());
	}

	private static ResourceKey<PlacedFeature> vanillaFeature(String name) {
		return ResourceKey.create(Registries.PLACED_FEATURE, Identifier.withDefaultNamespace(name));
	}

	public static void init() {
		BiomeModifications.create(Deadlands.id("no_vanilla_mobs")).add(ModificationPhase.REMOVALS, BiomeSelectors.all(), context -> {
			context.getSpawnSettings().removeSpawns((category, spawner) -> vanilla(spawner.type()));
			context.getGenerationSettings().removeFeature(MONSTER_ROOM);
			context.getGenerationSettings().removeFeature(MONSTER_ROOM_DEEP);
		});
		// Saved before this rule existed: gone as soon as they load (next tick, not mid-load).
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (banned(entity)) {
				level.getServer().execute(entity::discard);
			}
		});
		ServerLifecycleEvents.SERVER_STARTED.register(VanillaMobs::switchOffVanillaEvents);
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(entries -> {
			entries.getDisplayStacks().removeIf(VanillaMobs::vanillaEgg);
			entries.getSearchTabStacks().removeIf(VanillaMobs::vanillaEgg);
		});
	}

	private static boolean vanillaEgg(ItemStack stack) {
		return stack.getItem() instanceof SpawnEggItem && "minecraft".equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
	}

	private static void switchOffVanillaEvents(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		GameRules rules = overworld.getGameRules();
		rules.set(GameRules.RAIDS, false, server);
		rules.set(GameRules.SPAWN_PATROLS, false, server);
		rules.set(GameRules.SPAWN_PHANTOMS, false, server);
		rules.set(GameRules.SPAWN_WANDERING_TRADERS, false, server);
	}

	/** For {@code /deadlands mobs}: vanilla mobs loaded in every level. */
	public static int countLoaded(MinecraftServer server) {
		int count = 0;
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (banned(entity)) {
					count++;
				}
			}
		}
		return count;
	}
}
