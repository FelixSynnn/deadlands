package com.deadlands.test;

import com.deadlands.Deadlands;
import com.deadlands.container.ContainerKind;
import com.deadlands.container.ContainerLock;
import com.deadlands.container.LootContainerBlockEntity;
import com.deadlands.registry.DLBlocks;
import com.deadlands.registry.DLContainers;
import com.deadlands.registry.DLDataComponents;
import com.deadlands.registry.DLItems;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;

/** Milestone 2 content checks: block assets, loot tables, locks and structure templates. */
public class DeadlandsContentGameTest {
	@GameTest
	public void everyBlockHasStateModelNameAndLoot(GameTestHelper helper) throws Exception {
		ModContainer mod = FabricLoader.getInstance().getModContainer(Deadlands.MOD_ID).orElseThrow();
		JsonObject lang = JsonParser.parseString(Files.readString(mod.findPath("assets/deadlands/lang/en_us.json").orElseThrow(), StandardCharsets.UTF_8)).getAsJsonObject();
		for (BlockItem item : DLItems.blockItems()) {
			Block block = item.getBlock();
			String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
			helper.assertTrue(mod.findPath("assets/deadlands/blockstates/" + name + ".json").isPresent(), "Missing blockstate for " + name);
			helper.assertTrue(mod.findPath("assets/deadlands/items/" + name + ".json").isPresent(), "Missing item model for " + name);
			helper.assertTrue(lang.has("block.deadlands." + name), "Missing English name for " + name);
			if (block.getLootTable().isPresent()) {
				helper.assertTrue(mod.findPath("data/deadlands/loot_table/blocks/" + name + ".json").isPresent(), "Missing loot table for " + name);
			}
		}
		helper.succeed();
	}

	@GameTest
	public void allLootTablesParse(GameTestHelper helper) {
		var registries = helper.getLevel().getServer().reloadableRegistries();
		List<ResourceKey<LootTable>> keys = new ArrayList<>();
		for (ContainerKind kind : ContainerKind.values()) {
			keys.add(kind.lootTable());
			keys.add(kind.rareLootTable());
		}
		keys.add(ResourceKey.create(Registries.LOOT_TABLE, Deadlands.id("containers/ranger_locker")));
		for (Block block : DLBlocks.all()) {
			block.getLootTable().ifPresent(keys::add);
		}
		for (ResourceKey<LootTable> key : keys) {
			helper.assertTrue(registries.getLootTable(key) != LootTable.EMPTY, "Loot table missing or broken: " + key.identifier());
		}
		helper.succeed();
	}

	@GameTest
	public void locksNeedTheRightKey(GameTestHelper helper) {
		ItemStack ranger = new ItemStack(DLItems.KEY);
		ranger.set(DLDataComponents.KEY_ID, "ranger_station");
		ItemStack other = new ItemStack(DLItems.KEY);
		other.set(DLDataComponents.KEY_ID, "pharmacy");
		ContainerLock keyLock = ContainerLock.key("ranger_station");
		helper.assertTrue(keyLock.opensWith(ranger), "matching key opens");
		helper.assertFalse(keyLock.opensWith(other), "other key does not open");
		helper.assertFalse(keyLock.opensWith(new ItemStack(DLItems.KEY)), "blank key does not open");
		ContainerLock cardLock = ContainerLock.keycard(2);
		helper.assertFalse(cardLock.opensWith(new ItemStack(DLItems.KEYCARD_GREEN)), "level 1 card is not enough");
		helper.assertTrue(cardLock.opensWith(new ItemStack(DLItems.KEYCARD_BLUE)), "level 2 card opens");
		helper.assertTrue(cardLock.opensWith(new ItemStack(DLItems.KEYCARD_RED)), "level 3 card opens");
		helper.succeed();
	}

	@GameTest
	public void lockedContainersResistSurvivalPlayers(GameTestHelper helper) {
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel, DLContainers.MEDICAL_CABINET.defaultBlockState());
		LootContainerBlockEntity cabinet = helper.getBlockEntity(rel, LootContainerBlockEntity.class);
		BlockPos pos = helper.absolutePos(rel);
		Player survivor = helper.makeMockPlayer(GameType.SURVIVAL);
		Player builder = helper.makeMockPlayer(GameType.CREATIVE);
		float open = helper.getLevel().getBlockState(pos).getDestroyProgress(survivor, helper.getLevel(), pos);
		helper.assertTrue(open > 0.0F, "an unlocked cabinet can be broken");
		cabinet.setLock(ContainerLock.key("ranger_station"));
		helper.assertValueEqual(helper.getLevel().getBlockState(pos).getDestroyProgress(survivor, helper.getLevel(), pos), 0.0F,
			"a locked cabinet resists a survival player");
		helper.assertTrue(helper.getLevel().getBlockState(pos).getDestroyProgress(builder, helper.getLevel(), pos) > 0.0F,
			"creative players can still remove a locked cabinet");
		helper.assertTrue(cabinet.getUpdateTag(helper.getLevel().registryAccess()).contains("deadlands_lock"), "clients are told about the lock");
		helper.assertFalse(cabinet.getUpdateTag(helper.getLevel().registryAccess()).contains("Items"), "clients are not told the contents");
		cabinet.setLock(ContainerLock.NONE);
		helper.assertValueEqual(helper.getLevel().getBlockState(pos).getDestroyProgress(survivor, helper.getLevel(), pos), open,
			"unlocking makes it breakable again");
		helper.succeed();
	}

	@GameTest
	public void structureTemplatesHoldLockedLoot(GameTestHelper helper) {
		var manager = helper.getLevel().getServer().getStructureManager();
		StructureTemplate shack = manager.get(Deadlands.id("roadside_shack")).orElse(null);
		StructureTemplate cache = manager.get(Deadlands.id("supply_cache")).orElse(null);
		helper.assertTrue(shack != null && cache != null, "structure templates load");
		var cabinets = shack.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), DLContainers.MEDICAL_CABINET);
		helper.assertValueEqual(cabinets.size(), 1, "shack has one medical cabinet");
		helper.assertTrue(cabinets.getFirst().nbt() != null && cabinets.getFirst().nbt().contains("deadlands_lock"), "shack cabinet is locked");
		var lockers = shack.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), DLContainers.SUPPLY_LOCKER);
		helper.assertValueEqual(lockers.getFirst().nbt().getStringOr("LootTable", ""), "deadlands:containers/ranger_locker", "locker holds the key");
		helper.assertValueEqual(cache.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), DLContainers.MILITARY_CRATE).size(), 3, "cache has three crates");
		var structures = helper.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE);
		helper.assertTrue(structures.get(ResourceKey.create(Registries.STRUCTURE, Deadlands.id("roadside_shack"))).isPresent(), "worldgen structure registered");
		helper.succeed();
	}
}
