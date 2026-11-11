package com.deadlands.test;

import com.deadlands.Deadlands;
import com.deadlands.block.SpawnMarkerBlock;
import com.deadlands.npc.Access;
import com.deadlands.npc.Settlements;
import com.deadlands.world.town.TownCondition;
import com.deadlands.world.town.TownSite;
import com.deadlands.world.town.TownSize;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import com.deadlands.npc.Faction;
import com.deadlands.npc.NpcCharacter;
import com.deadlands.npc.NpcEntity;
import com.deadlands.npc.NpcRole;
import com.deadlands.npc.NpcSpawns;
import com.deadlands.npc.NpcTrades;
import com.deadlands.npc.NpcWorldData;
import com.deadlands.npc.Reputation;
import com.deadlands.registry.DLMarkers;
import com.deadlands.registry.DLWeapons;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Milestone 7 checks: roles, factions and standing, what survivors trade, the recurring characters,
 * spawn markers, the text and skins survivors need, settlements and their far-away simulation, who
 * may enter them, and survivors' belongings and people.
 *
 * <p>Written while the project owner asked for no test runs; run in every build since the
 * atmosphere overhaul.
 */
public class DeadlandsNpcGameTest {
	@GameTest
	public void standingFollowsReputation(GameTestHelper helper) {
		helper.assertValueEqual(Reputation.Standing.of(-100), Reputation.Standing.HOSTILE, "-100");
		helper.assertValueEqual(Reputation.Standing.of(-30), Reputation.Standing.HOSTILE, "-30 is still hostile");
		helper.assertValueEqual(Reputation.Standing.of(-1), Reputation.Standing.WARY, "-1");
		helper.assertValueEqual(Reputation.Standing.of(0), Reputation.Standing.NEUTRAL, "0");
		helper.assertValueEqual(Reputation.Standing.of(30), Reputation.Standing.FRIENDLY, "30");
		helper.assertValueEqual(Reputation.Standing.of(70), Reputation.Standing.TRUSTED, "70");
		helper.assertTrue(Reputation.Standing.TRUSTED.price() < Reputation.Standing.NEUTRAL.price(), "friends pay less");
		helper.assertTrue(Reputation.Standing.WARY.price() > Reputation.Standing.NEUTRAL.price(), "the wary are charged more");
		helper.assertValueEqual(Reputation.Standing.of(Faction.BANDITS.startingReputation()), Reputation.Standing.HOSTILE, "bandits start hostile");
		Reputation.Data data = new Reputation.Data();
		data.set(Faction.SETTLERS, 500);
		Tag tag = Reputation.Data.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
		helper.assertValueEqual(Reputation.Data.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow().get(Faction.SETTLERS), 100, "reputation is capped and saved");
		helper.assertTrue(Faction.BANDITS.hostileTo(Faction.SETTLERS) && !Faction.SETTLERS.hostileTo(Faction.MILITARY), "bandits are at war with everyone else");
		helper.succeed();
	}

	@GameTest
	public void tradersDealInRounds(GameTestHelper helper) {
		for (NpcRole role : NpcRole.values()) {
			if (!role.trades()) {
				continue;
			}
			MerchantOffers offers = NpcTrades.offers(role, RandomSource.create(7L));
			helper.assertTrue(offers.size() >= 5, role.id() + " offers at least five trades");
			for (MerchantOffer offer : offers) {
				boolean sells = offer.getBaseCostA().is(DLWeapons.PISTOL_AMMO);
				boolean buys = offer.getResult().is(DLWeapons.PISTOL_AMMO);
				helper.assertTrue(sells != buys, role.id() + ": every trade has rounds on exactly one side");
			}
		}
		helper.succeed();
	}

	@GameTest
	public void survivorsKeepWhoTheyAre(GameTestHelper helper) {
		NpcEntity doctor = NpcSpawns.create(helper.getLevel(), NpcRole.DOCTOR, Faction.SETTLERS, null, helper.absolutePos(new BlockPos(1, 1, 1)), 0.0F);
		helper.assertTrue(doctor != null, "a survivor can be created");
		helper.assertValueEqual(doctor.role(), NpcRole.DOCTOR, "role");
		helper.assertValueEqual(doctor.faction(), Faction.SETTLERS, "faction");
		helper.assertTrue(doctor.hasCustomName() && doctor.getName().getString().contains(" "), "survivors have a first name and a surname");
		helper.assertTrue(doctor.home() != null, "survivors have a home");
		helper.assertFalse(doctor.getOffers().isEmpty(), "a doctor has something to sell");
		NpcEntity felix = NpcSpawns.place(helper.getLevel(), NpcCharacter.FELIXSYN, helper.absolutePos(new BlockPos(2, 1, 2)), 0.0F);
		helper.assertValueEqual(felix.character(), NpcCharacter.FELIXSYN, "a recurring character knows who they are");
		helper.assertValueEqual(felix.getName().getString(), "FelixSyn", "and keeps their name");
		helper.assertTrue(NpcWorldData.get(helper.getLevel().getServer()).isPlaced(NpcCharacter.FELIXSYN), "the world remembers they were placed");
		helper.succeed();
	}

	@GameTest
	public void markersBecomeWhatTheyStandFor(GameTestHelper helper) {
		BlockPos rel = new BlockPos(2, 1, 2);
		helper.setBlock(rel, DLMarkers.SPAWN_MARKER.defaultBlockState().setValue(SpawnMarkerBlock.SPAWN, SpawnMarkerBlock.Spawn.NPC_TRADER));
		SpawnMarkerBlock.resolve(helper.getLevel(), helper.absolutePos(rel), helper.getBlockState(rel));
		helper.assertTrue(helper.getBlockState(rel).isAir(), "the marker is gone");
		helper.assertFalse(helper.getEntities(DLMarkers.SURVIVOR).isEmpty(), "and a survivor stands there");
		for (SpawnMarkerBlock.Spawn spawn : SpawnMarkerBlock.Spawn.values()) {
			if (spawn.name().startsWith("NPC_")) {
				NpcRole.valueOf(spawn.name().substring(4)); // every survivor marker names a real role
			}
		}
		helper.succeed();
	}

	@GameTest
	public void survivorsHaveTheirTextAndSkins(GameTestHelper helper) throws Exception {
		ModContainer mod = FabricLoader.getInstance().getModContainer(Deadlands.MOD_ID).orElseThrow();
		JsonObject lang = JsonParser.parseString(Files.readString(mod.findPath("assets/deadlands/lang/en_us.json").orElseThrow(), StandardCharsets.UTF_8)).getAsJsonObject();
		for (NpcRole role : NpcRole.values()) {
			helper.assertTrue(lang.has(role.translationKey()), "name for role " + role.id());
			for (int look = 0; look < NpcEntity.SKINS_PER_ROLE; look++) {
				String path = "assets/deadlands/textures/entity/survivor/" + role.id() + "_" + look + ".png";
				helper.assertTrue(mod.findPath(path).isPresent(), "Missing skin " + path);
			}
		}
		for (Faction faction : Faction.values()) {
			helper.assertTrue(lang.has(faction.translationKey()), "name for faction " + faction.id());
		}
		for (Reputation.Standing standing : Reputation.Standing.values()) {
			helper.assertTrue(lang.has(standing.translationKey()), "name for standing " + standing);
		}
		for (String mood : new String[] {"wary", "neutral", "friendly"}) {
			for (int temperament = 0; temperament < NpcEntity.TEMPERAMENTS; temperament++) {
				helper.assertTrue(lang.has("npc.deadlands.greeting." + mood + "." + temperament), "greeting " + mood + " " + temperament);
			}
		}
		for (NpcCharacter character : NpcCharacter.values()) {
			helper.assertTrue(lang.has("npc.deadlands.character." + character.id() + ".greeting"), "greeting for " + character.id());
			for (int part = 0; part < character.storyLines(); part++) {
				helper.assertTrue(lang.has("npc.deadlands.character." + character.id() + ".story." + part), "story " + part + " for " + character.id());
			}
			helper.assertTrue(mod.findPath("assets/deadlands/textures/entity/survivor/character/" + character.id() + ".png").isPresent(),
				"placeholder skin for " + character.id());
		}
		helper.succeed();
	}

	@GameTest
	public void settlementsLiveAndDieByTheNumbers(GameTestHelper helper) {
		Settlements settlements = new Settlements();
		TownSite town = new TownSite(0, 0, 1000, 1000, 16008, 70, 16008, TownSize.SMALL_TOWN, TownCondition.SURVIVOR);
		Settlements.Settlement settlement = settlements.register(town);
		settlements.register(town);
		helper.assertValueEqual(settlement.population(), 2, "each resident who appears is counted");
		helper.assertValueEqual(settlement.faction(), Faction.SETTLERS, "a survivor town is a settler settlement");
		helper.assertTrue(settlements.of(town.key()) == settlement, "one record per town");
		settlements.give(settlement, -1000, 0);
		helper.assertValueEqual(settlement.suppliesLevel(), "none", "empty stores");
		helper.assertValueEqual(settlements.priceFactor(town.key()), 1.5F, "empty stores make traders dearer");
		settlements.give(settlement, 1000, 0);
		helper.assertValueEqual(settlement.supplies(), Settlements.MAX_SUPPLIES, "stores are capped");
		settlements.simulate(helper.getLevel().getServer(), 400L, 10);
		helper.assertTrue(settlement.population() >= 0 && settlement.population() <= 2, "nobody is invented by the simulation (newcomers wait to walk in)");
		helper.assertValueEqual(settlement.fallen(), settlement.population() == 0, "a settlement falls exactly when nobody is left");
		settlements.residentDied(town.key());
		settlements.residentDied(town.key());
		helper.assertTrue(settlement.fallen(), "losing everyone ends it");
		helper.assertTrue(settlements.claimLoss(town.key(), true), "in a fallen settlement even the leader is gone");
		helper.assertTrue(Settlements.settled(TownCondition.MILITARY) && !Settlements.settled(TownCondition.OVERRUN), "only held towns are settlements");
		helper.succeed();
	}

	@GameTest
	public void accessFollowsStandingAndCurfew(GameTestHelper helper) {
		helper.assertTrue(Access.unwelcome(Faction.SETTLERS, Reputation.Standing.WARY, false), "the wary are turned away");
		helper.assertFalse(Access.unwelcome(Faction.SETTLERS, Reputation.Standing.NEUTRAL, true), "settlers keep no curfew");
		helper.assertTrue(Access.unwelcome(Faction.MILITARY, Reputation.Standing.NEUTRAL, true), "the military keeps a curfew after dark");
		helper.assertFalse(Access.unwelcome(Faction.MILITARY, Reputation.Standing.FRIENDLY, true), "friends may stay after dark");
		helper.assertFalse(Access.unwelcome(Faction.MILITARY, Reputation.Standing.NEUTRAL, false), "by day anyone neutral may come in");
		helper.succeed();
	}

	@GameTest
	public void survivorsHaveTheirPeopleAndBelongings(GameTestHelper helper) {
		NpcEntity a = NpcSpawns.create(helper.getLevel(), NpcRole.SETTLER, Faction.SETTLERS, null, helper.absolutePos(new BlockPos(1, 1, 1)), 0.0F);
		NpcEntity b = NpcSpawns.create(helper.getLevel(), NpcRole.FARMER, Faction.SETTLERS, null, helper.absolutePos(new BlockPos(3, 1, 3)), 0.0F);
		NpcEntity.link(a, b, 2);
		String surname = a.getName().getString().split(" ", 2)[1];
		helper.assertTrue(b.getName().getString().endsWith(" " + surname), "siblings share a surname");
		helper.assertValueEqual(a.relative(), b.getUUID(), "each knows the other");
		helper.assertValueEqual(b.relative(), a.getUUID(), "both ways");
		helper.assertValueEqual(b.relation(), 2, "as siblings");
		helper.assertFalse(a.inventory().isEmpty(), "survivors carry things of their own");
		NpcEntity guard = NpcSpawns.create(helper.getLevel(), NpcRole.GUARD, Faction.SETTLERS, null, helper.absolutePos(new BlockPos(1, 1, 3)), 0.0F);
		helper.assertFalse(guard.getMainHandItem().isEmpty(), "a guard is armed");
		var registries = helper.getLevel().registryAccess();
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		b.saveWithoutId(output);
		NpcEntity copy = DLMarkers.SURVIVOR.create(helper.getLevel(), EntitySpawnReason.LOAD);
		copy.load(TagValueInput.create(ProblemReporter.DISCARDING, registries, output.buildResult()));
		helper.assertValueEqual(copy.relation(), 2, "relationship after loading");
		helper.assertValueEqual(copy.relativeName(), a.getName().getString(), "who they are related to after loading");
		helper.assertValueEqual(copy.inventory().getItem(0).getCount(), b.inventory().getItem(0).getCount(), "belongings after loading");
		a.die(helper.getLevel().damageSources().generic());
		helper.assertTrue(b.grieving(), "losing a sibling leaves them grieving");
		helper.succeed();
	}

	@GameTest
	public void theWorldRemembersWhereCharactersAre(GameTestHelper helper) {
		NpcWorldData data = new NpcWorldData();
		BlockPos where = new BlockPos(120, 64, -340);
		data.markPlaced(NpcCharacter.IMLILYLOL, where);
		Tag tag = NpcWorldData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
		NpcWorldData loaded = NpcWorldData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
		helper.assertTrue(loaded.isPlaced(NpcCharacter.IMLILYLOL), "placed after loading");
		helper.assertValueEqual(loaded.position(NpcCharacter.IMLILYLOL), where, "position after loading");
		helper.assertTrue(loaded.position(NpcCharacter.HARLEY) == null, "nobody else has a position yet");
		helper.succeed();
	}
}
