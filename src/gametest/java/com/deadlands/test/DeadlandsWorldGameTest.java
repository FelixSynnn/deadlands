package com.deadlands.test;

import com.deadlands.Deadlands;
import com.deadlands.block.DecorKind;
import com.deadlands.registry.DLDecor;
import com.deadlands.registry.DLItems;
import com.deadlands.world.biome.BiomeReplacement;
import com.deadlands.world.biome.DLBiomes;
import com.deadlands.world.town.TownCondition;
import com.deadlands.world.town.TownIdentity;
import com.deadlands.world.town.TownSite;
import com.deadlands.world.town.TownSize;
import com.deadlands.world.town.TownStructure;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Milestone 3 checks: biome replacement, town data and building templates, town identity, cities and
 * western towns, the sites outside towns, railways and the power grid's parts.
 *
 * <p>Written while the project owner asked for no test runs; run in every build since the
 * atmosphere overhaul.
 */
public class DeadlandsWorldGameTest {
	private static final List<String> TOWN_LOOT = List.of("house_kitchen", "house_bathroom", "house_bedroom", "house_drawers", "store_storage",
		"pharmacy", "clinic", "police_locker", "office", "workshop", "farm", "church", "fire_station", "school_locker", "school_office",
		"school_key", "school_hidden", "hunting", "campsite", "utility", "research_lab", "gas_station", "library");
	private static final List<String> SITES = List.of("school", "forest_outpost", "camp", "military_post", "bunker", "utility_site",
		"power_station", "treatment_plant", "farm_site", "research_outpost", "gas_station", "roadside_stop");
	private static final List<String> SITE_TEMPLATES = List.of("bunker_a", "bunker_b", "campsite_a", "campsite_b", "campsite_c",
		"checkpoint_a", "checkpoint_b", "checkpoint_c", "evacuation_camp_a", "evacuation_camp_b", "hunting_cabin_a", "hunting_cabin_b",
		"hunting_cabin_c", "power_station", "radio_tower_a", "radio_tower_b", "ranger_station_a", "ranger_station_b", "ranger_station_c",
		"research_outpost", "school_evacuated", "school_overrun", "school_shelter", "silo_a", "silo_b", "substation_a", "substation_b",
		"substation_c", "treatment_plant", "watchtower_a", "watchtower_b", "watchtower_c", "water_tower_a", "water_tower_b", "water_tower_c",
		"windmill_a", "windmill_b", "farmstead_a", "farmstead_b", "gas_station_a", "gas_station_b", "rest_stop", "roadside_stand");

	@GameTest
	public void landBiomesBecomeDeadlandsBiomes(GameTestHelper helper) {
		Climate.Parameter mild = Climate.Parameter.span(-0.15F, 0.2F);
		helper.assertTrue(DLBiomes.ALL.contains(BiomeReplacement.replace(Biomes.PLAINS, mild)), "plains become a DEADLANDS biome");
		helper.assertTrue(DLBiomes.ALL.contains(BiomeReplacement.replace(Biomes.FOREST, mild)), "forest becomes a DEADLANDS biome");
		helper.assertValueEqual(BiomeReplacement.replace(Biomes.DESERT, mild), DLBiomes.DRY_WASTELAND, "desert becomes the dry wasteland");
		helper.assertValueEqual(BiomeReplacement.replace(Biomes.OCEAN, mild), Biomes.OCEAN, "oceans stay oceans");
		helper.assertValueEqual(BiomeReplacement.replace(Biomes.RIVER, mild), Biomes.RIVER, "rivers stay rivers");
		helper.succeed();
	}

	@GameTest
	public void townTemplatesFitTheirPlots(GameTestHelper helper) {
		var structures = helper.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Structure structure = structures.getValue(ResourceKey.create(Registries.STRUCTURE, Deadlands.id("town")));
		helper.assertTrue(structure instanceof TownStructure, "the town structure is registered");
		Map<String, List<Identifier>> buildings = ((TownStructure) structure).buildings();
		helper.assertTrue(buildings.getOrDefault("residential", List.of()).size() >= 5, "towns have houses to choose from");
		var manager = helper.getLevel().getServer().getStructureManager();
		int count = 0;
		for (List<Identifier> ids : buildings.values()) {
			for (Identifier id : ids) {
				StructureTemplate template = manager.get(id).orElse(null);
				helper.assertTrue(template != null, "building template loads: " + id);
				helper.assertTrue(template.getSize().getX() > 0 && template.getSize().getX() <= 14, "fits the plot frontage: " + id);
				helper.assertTrue(template.getSize().getZ() > 0 && template.getSize().getZ() <= 14, "fits the plot depth: " + id);
				count++;
			}
		}
		helper.assertTrue(count >= 25, "at least 25 town buildings, found " + count);
		helper.succeed();
	}

	@GameTest
	public void townLootTablesParse(GameTestHelper helper) {
		var registries = helper.getLevel().getServer().reloadableRegistries();
		for (String name : TOWN_LOOT) {
			ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, Deadlands.id("containers/" + name));
			helper.assertTrue(registries.getLootTable(key) != LootTable.EMPTY, "Loot table missing or broken: " + key.identifier());
		}
		helper.succeed();
	}

	@GameTest
	public void townIdentityIsStable(GameTestHelper helper) {
		TownSite site = new TownSite(3, -2, 104, -61, 1672, 70, -968, TownSize.SMALL_TOWN, TownCondition.OVERRUN);
		TownIdentity first = TownIdentity.of(12345L, site);
		helper.assertValueEqual(first, TownIdentity.of(12345L, site), "same seed and site, same town");
		helper.assertTrue(!first.name().isBlank(), "towns have names");
		helper.assertTrue(first.population() >= 700 && first.population() <= 4850, "small towns had hundreds to thousands of people");
		helper.assertValueEqual(TownIdentity.lootKey(TownCondition.MILITARY), "town.deadlands.loot.untouched", "military towns were barely looted");
		helper.assertValueEqual(TownIdentity.lootKey(TownCondition.SURVIVOR), "town.deadlands.loot.stripped", "survivor towns were stripped");
		helper.succeed();
	}

	@GameTest
	public void citiesAndWesternTownsHaveTheirBuildings(GameTestHelper helper) {
		var structures = helper.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Structure structure = structures.getValue(ResourceKey.create(Registries.STRUCTURE, Deadlands.id("town")));
		helper.assertTrue(structure instanceof TownStructure, "the town structure is registered");
		Map<String, List<Identifier>> buildings = ((TownStructure) structure).buildings();
		helper.assertTrue(buildings.getOrDefault("city", List.of()).size() >= 8, "cities have their own buildings");
		helper.assertTrue(buildings.getOrDefault("western", List.of()).size() >= 6, "western towns have their own buildings");
		for (TownSize size : TownSize.values()) {
			helper.assertTrue(TownSize.CITY.radius() >= size.radius(), "cities are the biggest towns");
		}
		helper.assertTrue(TownSize.CITY.major() && TownSize.CITY.sideStreets(), "cities are major towns with a street grid");
		TownSite site = new TownSite(9, 4, 300, -61, 1672, 70, -968, TownSize.CITY, TownCondition.ABANDONED);
		helper.assertTrue(TownIdentity.of(777L, site).population() >= 24000, "cities had tens of thousands of people");
		helper.succeed();
	}

	@GameTest
	public void sitesAreRegisteredAndTheirTemplatesLoad(GameTestHelper helper) {
		var structures = helper.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (String site : SITES) {
			helper.assertTrue(structures.get(ResourceKey.create(Registries.STRUCTURE, Deadlands.id(site))).isPresent(), "site structure registered: " + site);
		}
		var manager = helper.getLevel().getServer().getStructureManager();
		for (String name : SITE_TEMPLATES) {
			StructureTemplate template = manager.get(Deadlands.id("site/" + name)).orElse(null);
			helper.assertTrue(template != null && template.getSize().getX() > 0, "site template loads: " + name);
		}
		var placed = helper.getLevel().registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
		helper.assertTrue(placed.get(ResourceKey.create(Registries.PLACED_FEATURE, Deadlands.id("rail_network"))).isPresent(), "railways are placed");
		helper.assertTrue(placed.get(ResourceKey.create(Registries.PLACED_FEATURE, Deadlands.id("road_network"))).isPresent(), "roads are placed");
		helper.succeed();
	}

	@GameTest
	public void thePowerGridHasItsParts(GameTestHelper helper) {
		helper.assertTrue(BuiltInRegistries.ITEM.containsKey(Deadlands.id("fuse")), "fuses are registered");
		helper.assertValueEqual(BuiltInRegistries.ITEM.getKey(DLItems.FUSE), Deadlands.id("fuse"), "the fuse item");
		helper.assertTrue(DLDecor.get(DecorKind.POWER_GENERATOR) != null, "generators are registered");
		helper.assertTrue(DLDecor.get(DecorKind.STREET_LAMP_HEAD).defaultBlockState().hasProperty(com.deadlands.block.LampHeadBlock.LIT),
			"street lamps can light up");
		helper.succeed();
	}
}
