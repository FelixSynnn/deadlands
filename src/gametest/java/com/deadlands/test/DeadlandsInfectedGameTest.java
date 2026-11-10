package com.deadlands.test;

import com.deadlands.Deadlands;
import com.deadlands.entity.InfectedEntity;
import com.deadlands.entity.InfectedKind;
import com.deadlands.entity.InfectedVariants;
import com.deadlands.horde.HordeDirector;
import com.deadlands.nest.NestBlockEntity;
import com.deadlands.nest.NestKind;
import com.deadlands.noise.Loudness;
import com.deadlands.persistence.DeadlandsWorldData;
import com.deadlands.persistence.HordeRecord;
import com.deadlands.persistence.MigrationRecord;
import com.deadlands.registry.DLEntities;
import com.deadlands.registry.DLNests;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Milestone 4 checks: the infected kinds and their looks, saving infected and nests, the horde mix,
 * and horde, migration and power state in the world save.
 *
 * <p>Written without being run (the project owner asked for no test runs during Milestones 3 and 4).
 */
public class DeadlandsInfectedGameTest {
	@GameTest
	@SuppressWarnings("unchecked")
	public void infectedKindsAreRegistered(GameTestHelper helper) {
		int looks = 0;
		for (InfectedKind kind : InfectedKind.values()) {
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Deadlands.id(kind.id()));
			helper.assertTrue(type == DLEntities.type(kind), "entity type registered: " + kind.id());
			helper.assertValueEqual(InfectedKind.of(type), kind, "the type maps back to its kind");
			helper.assertTrue(DefaultAttributes.hasSupplier(type), "attributes registered for " + kind.id());
			double health = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type).getBaseValue(Attributes.MAX_HEALTH);
			helper.assertValueEqual(health, kind.health(), "max health of " + kind.id());
			helper.assertTrue(BuiltInRegistries.ITEM.containsKey(Deadlands.id(kind.id() + "_spawn_egg")), "spawn egg for " + kind.id());
			helper.assertFalse(kind.variants().isEmpty(), "every kind has at least one look: " + kind.id());
			looks += kind.variants().size();
		}
		helper.assertTrue(looks >= 50 && looks <= 60, "50 to 60 infected looks, found " + looks);
		helper.succeed();
	}

	@GameTest
	public void infectedLooksHaveTexturesAndLoot(GameTestHelper helper) {
		ModContainer mod = FabricLoader.getInstance().getModContainer(Deadlands.MOD_ID).orElseThrow();
		var registries = helper.getLevel().getServer().reloadableRegistries();
		Set<String> outfits = new HashSet<>();
		for (InfectedKind kind : InfectedKind.values()) {
			for (InfectedVariants.Variant variant : kind.variants()) {
				String path = "assets/deadlands/textures/entity/infected/" + kind.id() + "/" + variant.name() + ".png";
				helper.assertTrue(mod.findPath(path).isPresent(), "Missing texture " + path);
				outfits.add(variant.outfit());
			}
			ResourceKey<LootTable> drops = ResourceKey.create(Registries.LOOT_TABLE, Deadlands.id("entities/" + kind.id()));
			helper.assertTrue(registries.getLootTable(drops) != LootTable.EMPTY, "Loot table missing or broken: " + drops.identifier());
		}
		for (String outfit : outfits) {
			ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, Deadlands.id("entities/outfit/" + outfit));
			helper.assertTrue(registries.getLootTable(key) != LootTable.EMPTY, "Outfit loot table missing or broken: " + key.identifier());
		}
		helper.succeed();
	}

	@GameTest
	public void infectedKeepTheirLookAndOrdersThroughSaveAndLoad(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		InfectedEntity walker = DLEntities.WALKER.create(level, EntitySpawnReason.COMMAND);
		helper.assertTrue(walker != null, "a walker can be created");
		walker.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
		walker.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.COMMAND, null);
		helper.assertTrue(walker.variantIndex() >= 0 && walker.variantIndex() < InfectedKind.WALKER.variants().size(), "a walker gets one of its looks");
		walker.setHordeDay(15);
		walker.setMigrationId(7);
		walker.setHomeNest(pos.above(2));

		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		walker.saveWithoutId(output);
		CompoundTag saved = output.buildResult();
		InfectedEntity copy = DLEntities.WALKER.create(level, EntitySpawnReason.LOAD);
		copy.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
		helper.assertValueEqual(copy.variantIndex(), walker.variantIndex(), "look after loading");
		helper.assertValueEqual(copy.buildScale(), walker.buildScale(), "build after loading");
		helper.assertValueEqual(copy.heightScale(), walker.heightScale(), "height after loading");
		helper.assertValueEqual(copy.hordeDay(), 15L, "horde night after loading");
		helper.assertValueEqual(copy.migrationId(), 7, "migration after loading");
		helper.assertValueEqual(copy.homeNest(), pos.above(2), "home nest after loading");
		helper.assertTrue(copy.isHordeMember(), "horde members stay horde members");
		helper.succeed();
	}

	@GameTest
	public void nestsKeepTheirStateAndTakeInfectedBack(GameTestHelper helper) {
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel, DLNests.nest(NestKind.RESIDENTIAL).defaultBlockState());
		NestBlockEntity nest = helper.getBlockEntity(rel, NestBlockEntity.class);
		helper.assertValueEqual(nest.kind(), NestKind.RESIDENTIAL, "the nest knows its kind");

		CompoundTag state = new CompoundTag();
		state.putInt("Population", 2);
		state.putFloat("Alert", 2.5F);
		state.putLong("CooldownUntil", 1234L);
		state.putLong("LastGrowth", 99L);
		state.putInt("Released", 4);
		var registries = helper.getLevel().registryAccess();
		nest.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, state));
		helper.assertValueEqual(nest.population(), 2, "population after loading");
		CompoundTag saved = nest.saveWithFullMetadata(registries);
		helper.assertValueEqual(saved.getIntOr("Population", -1), 2, "population is saved");
		helper.assertValueEqual(saved.getLongOr("CooldownUntil", 0L), 1234L, "cooldown is saved");
		helper.assertValueEqual(saved.getIntOr("Released", 0), 4, "release count is saved");

		InfectedEntity walker = helper.spawn(DLEntities.WALKER, new BlockPos(2, 1, 1));
		helper.assertTrue(nest.absorb(walker), "a nest with room takes an infected back");
		helper.assertValueEqual(nest.population(), 3, "population after taking one back");
		helper.assertTrue(walker.isRemoved(), "the infected went inside");
		helper.succeed();
	}

	@GameTest
	public void hordesAreMostlyWalkersAndRunners(GameTestHelper helper) {
		RandomSource random = RandomSource.create(4242L);
		Map<InfectedKind, Integer> first = new EnumMap<>(InfectedKind.class);
		Map<InfectedKind, Integer> late = new EnumMap<>(InfectedKind.class);
		int rolls = 4000;
		for (int i = 0; i < rolls; i++) {
			first.merge(HordeDirector.pickKind(random, 1), 1, Integer::sum);
			late.merge(HordeDirector.pickKind(random, 10), 1, Integer::sum);
		}
		int common = first.getOrDefault(InfectedKind.WALKER, 0) + first.getOrDefault(InfectedKind.RUNNER, 0);
		helper.assertTrue(common >= rolls * 0.7, "walkers and runners make up most of a horde: " + first);
		for (InfectedKind special : new InfectedKind[] {InfectedKind.SCREAMER, InfectedKind.STALKER, InfectedKind.CHARRED}) {
			helper.assertFalse(first.containsKey(special) || late.containsKey(special), "hordes leave " + special.id() + " to the night");
		}
		helper.assertTrue(late.getOrDefault(InfectedKind.BRUTE, 0) > first.getOrDefault(InfectedKind.BRUTE, 0),
			"later hordes bring more brutes: " + first.get(InfectedKind.BRUTE) + " then " + late.get(InfectedKind.BRUTE));
		helper.succeed();
	}

	@GameTest
	public void hordeMigrationAndPowerSurviveSaveAndLoad(GameTestHelper helper) {
		DeadlandsWorldData data = new DeadlandsWorldData();
		data.setHorde(HordeRecord.begin(15L, 40, 1.5F).withSpawned(22).withKilled(9));
		int id = data.nextMigrationId();
		data.putMigration(new MigrationRecord(id, 100.5, -40.0, 900.0, 300.0, 18, 2400L));
		long town = 123_456_789L;
		helper.assertTrue(data.powerTown(town), "restoring a town's power the first time counts");
		helper.assertFalse(data.powerTown(town), "a town is only powered once");

		Tag tag = DeadlandsWorldData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
		DeadlandsWorldData loaded = DeadlandsWorldData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
		helper.assertValueEqual(loaded, data, "world data after a save/load round trip");
		helper.assertValueEqual(loaded.horde().orElseThrow().remaining(), 31, "infected still to beat after loading");
		helper.assertValueEqual(loaded.migration(id).orElseThrow().count(), 18, "migration size after loading");
		helper.assertTrue(loaded.isTownPowered(town), "powered towns stay powered");
		helper.assertValueEqual(loaded.nextMigrationId(), id + 1, "migration ids keep counting after loading");
		helper.succeed();
	}

	@GameTest
	public void louderNoisesCarryFarther(GameTestHelper helper) {
		Loudness[] levels = Loudness.values();
		for (int i = 1; i < levels.length; i++) {
			helper.assertTrue(levels[i].radius() > levels[i - 1].radius(), levels[i] + " carries farther than " + levels[i - 1]);
			helper.assertTrue(levels[i].priority() > levels[i - 1].priority(), levels[i] + " outranks " + levels[i - 1]);
		}
		helper.assertTrue(Loudness.EXPLOSION.radius() >= 96.0, "explosions are heard at least 96 blocks away");
		helper.succeed();
	}
}
