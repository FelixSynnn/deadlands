package com.deadlands.test;

import com.deadlands.Deadlands;
import com.deadlands.block.KeycardReaderBlock;
import com.deadlands.entity.BossEntity;
import com.deadlands.progression.BossKind;
import com.deadlands.registry.DLBosses;
import com.deadlands.registry.DLStory;
import com.deadlands.story.Endings;
import com.deadlands.story.Lore;
import com.deadlands.story.LoreEntry;
import com.deadlands.story.Quests;
import com.deadlands.story.Radio;
import com.deadlands.story.StoryPlaces;
import com.deadlands.story.StoryState;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Milestone 8 checks: the lore catalogue and its text, documents, story state and its saving, the
 * quests' conditions, the endings' conditions, the radio's text, keycard readers and the bosses.
 *
 * <p>Written while the project owner asked for no test runs; run in every build since the
 * atmosphere overhaul.
 */
public class DeadlandsStoryGameTest {
	private static JsonObject lang() throws Exception {
		ModContainer mod = FabricLoader.getInstance().getModContainer(Deadlands.MOD_ID).orElseThrow();
		return JsonParser.parseString(Files.readString(mod.findPath("assets/deadlands/lang/en_us.json").orElseThrow(), StandardCharsets.UTF_8)).getAsJsonObject();
	}

	/** A story state that has read these entries, made the way a saved one is loaded. */
	private static StoryState readAll(List<String> ids) {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		for (String id : ids) {
			list.add(StringTag.valueOf(id));
		}
		tag.put("discovered", list);
		return StoryState.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
	}

	@GameTest
	public void loreIsCompleteAndWritten(GameTestHelper helper) throws Exception {
		JsonObject lang = lang();
		helper.assertTrue(Lore.all().size() >= 100, "at least a hundred lore entries");
		Set<String> ids = new HashSet<>();
		for (LoreEntry entry : Lore.all()) {
			helper.assertTrue(ids.add(entry.id()), "lore ids are unique: " + entry.id());
			helper.assertTrue(lang.has(Lore.titleKey(entry.id())) && lang.has(Lore.bodyKey(entry.id())), "title and text for " + entry.id());
			helper.assertTrue(lang.has(entry.category().translationKey()), "category name for " + entry.id());
			helper.assertFalse(entry.places().isEmpty(), "somewhere to find " + entry.id());
			if (!entry.series().isEmpty()) {
				List<LoreEntry> series = Lore.series(entry.series());
				helper.assertTrue(entry.part() >= 1 && entry.part() <= series.size(), "part number within its series: " + entry.id());
			}
			if (entry.places().contains("trail")) {
				helper.assertTrue(lang.get(Lore.bodyKey(entry.id())).getAsString().contains("%1$s"), "a trail note names its place: " + entry.id());
			}
		}
		helper.assertValueEqual(Lore.series("p0").size(), 6, "six parts to Patient Zero's records");
		helper.assertTrue(Lore.series("harley_research").size() >= Quests.NOTES_NEEDED, "enough of Harley's notes to finish the quest");
		helper.succeed();
	}

	@GameTest
	public void documentsCarryTheirEntryAndTurnUpWhereTheyBelong(GameTestHelper helper) {
		ItemStack document = Lore.document("med_sato_1");
		helper.assertTrue(document.is(DLStory.DOCUMENT), "a document item");
		helper.assertValueEqual(document.get(DLStory.LORE), "med_sato_1", "it knows which entry it is");
		helper.assertTrue(Lore.placesFor("deadlands:containers/clinic").contains("medical"), "a clinic cabinet is a medical place");
		helper.assertTrue(Lore.placesFor("deadlands:containers/school_locker").contains("school"), "a school locker is a school");
		LoreEntry picked = Lore.pick(Set.of("school", "any"), null, net.minecraft.util.RandomSource.create(3L));
		helper.assertTrue(picked != null && (picked.places().contains("school") || picked.places().contains("any")), "what turns up in a school fits a school");
		helper.assertTrue(Lore.pick(Set.of("trail"), null, net.minecraft.util.RandomSource.create(3L)) != null, "trail notes exist");
		helper.succeed();
	}

	@GameTest
	public void storyStateIsSaved(GameTestHelper helper) {
		StoryState story = new StoryState();
		story.set(StoryState.MET + "harley");
		story.deliver(DLStory.idOf(DLStory.MEDICAL_SAMPLES));
		story.setPlace(StoryPlaces.Place.MILITARY_FACILITY, new BlockPos(400, 70, -900));
		story.bossDefeated(BossKind.WARDEN);
		story.fitHeliPart(DLStory.idOf(DLStory.AVIONICS));
		story.addTower(new BlockPos(10, 80, 10));
		story.setCureReadyDay(42L);
		Tag tag = StoryState.CODEC.encodeStart(NbtOps.INSTANCE, story).getOrThrow();
		StoryState loaded = StoryState.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
		helper.assertTrue(loaded.has(StoryState.MET + "harley"), "flags after loading");
		helper.assertTrue(loaded.delivered(DLStory.idOf(DLStory.MEDICAL_SAMPLES)), "deliveries after loading");
		helper.assertValueEqual(loaded.place(StoryPlaces.Place.MILITARY_FACILITY), new BlockPos(400, 70, -900), "places after loading");
		helper.assertValueEqual(loaded.bossDefeats(BossKind.WARDEN), 1, "bosses after loading");
		helper.assertTrue(loaded.has(StoryState.BOSS + "warden"), "a defeated boss is a flag too");
		helper.assertTrue(loaded.heliParts().contains(DLStory.idOf(DLStory.AVIONICS)), "helicopter parts after loading");
		helper.assertValueEqual(loaded.towers().size(), 1, "towers after loading");
		helper.assertValueEqual(loaded.cureReadyDay(), 42L, "cure day after loading");
		helper.assertFalse(loaded.addTower(new BlockPos(10, 80, 10)), "a tower is only counted once");
		helper.succeed();
	}

	@GameTest
	public void questsFollowTheStory(GameTestHelper helper) {
		StoryState story = readAll(List.of("harley_research_1", "harley_research_2", "harley_research_3", "harley_research_4"));
		helper.assertValueEqual(story.seriesRead("harley_research"), 4, "notes read");
		helper.assertFalse(Quests.missingResearchComplete(story), "places still to investigate");
		for (StoryPlaces.Place place : List.of(StoryPlaces.Place.HOSPITAL, StoryPlaces.Place.RESEARCH_FACILITY, StoryPlaces.Place.EVACUATION_SITE)) {
			story.set(StoryState.INVESTIGATED + place.id());
		}
		helper.assertFalse(Quests.missingResearchComplete(story), "the field case is still missing");
		story.deliver(DLStory.idOf(DLStory.FIELD_CASE));
		helper.assertTrue(Quests.missingResearchComplete(story), "the missing research is found");
		for (Item item : DLStory.cureItems()) {
			story.deliver(DLStory.idOf(item));
		}
		helper.assertFalse(Quests.cureComplete(story), "the cure needs two trophies");
		story.deliver(DLStory.idOf(DLStory.FUSED_TISSUE));
		story.deliver(DLStory.idOf(DLStory.SERUM_VIAL));
		helper.assertTrue(Quests.cureComplete(story), "everything for the cure");
		helper.assertFalse(story.helicopterReady(), "no parts fitted yet");
		for (Item part : DLStory.helicopterParts()) {
			story.fitHeliPart(DLStory.idOf(part));
		}
		helper.assertTrue(story.helicopterReady(), "four parts make it fly");
		helper.succeed();
	}

	@GameTest
	public void theSecretEndingNeedsEverything(GameTestHelper helper) {
		StoryState story = readAll(List.of("p0_1", "p0_2", "p0_3", "p0_4", "p0_5"));
		story.bossDefeated(BossKind.PATIENT_ZERO);
		story.deliver(DLStory.idOf(DLStory.PATIENT_ZERO_BLOOD));
		helper.assertFalse(Endings.secretReady(story), "five of six records is not enough");
		StoryState all = readAll(List.of("p0_1", "p0_2", "p0_3", "p0_4", "p0_5", "p0_6"));
		all.bossDefeated(BossKind.PATIENT_ZERO);
		helper.assertFalse(Endings.secretReady(all), "the blood must reach Harley");
		all.deliver(DLStory.idOf(DLStory.PATIENT_ZERO_BLOOD));
		helper.assertTrue(Endings.secretReady(all), "everything: the secret ending opens");
		all.setEnding(Endings.EXTRACTION);
		helper.assertFalse(Endings.secretReady(all), "not after another ending");
		helper.succeed();
	}

	@GameTest
	public void radioAndStoryTextExist(GameTestHelper helper) throws Exception {
		JsonObject lang = lang();
		for (int i = 0; i < Radio.STATIONS.length; i++) {
			helper.assertTrue(lang.has("radio.deadlands.station." + i), "station name " + i);
		}
		for (String ending : List.of(Endings.EXTRACTION, Endings.RESCUE, Endings.STAY, Endings.PATIENT_ZERO)) {
			helper.assertTrue(lang.has("ending.deadlands." + ending + ".title") && lang.has("ending.deadlands." + ending + ".body"), "ending text " + ending);
		}
		for (StoryPlaces.Place place : StoryPlaces.Place.values()) {
			helper.assertTrue(lang.has("story.deadlands.place." + place.id()), "place name " + place.id());
		}
		for (int i = 0; i < 7; i++) {
			helper.assertTrue(lang.has("intro.deadlands.line." + i), "opening line " + i);
		}
		helper.succeed();
	}

	@GameTest
	public void keycardReadersHaveThreeLevels(GameTestHelper helper) {
		helper.assertTrue(KeycardReaderBlock.LEVEL.getPossibleValues().containsAll(List.of(1, 2, 3)), "levels 1 to 3");
		helper.setBlock(new BlockPos(1, 1, 1), DLStory.KEYCARD_READER.defaultBlockState().setValue(KeycardReaderBlock.LEVEL, 3));
		helper.assertValueEqual(helper.getBlockState(new BlockPos(1, 1, 1)).getValue(KeycardReaderBlock.LEVEL), 3, "a red reader stays red");
		helper.succeed();
	}

	@GameTest
	public void bossesAreRegisteredAndScale(GameTestHelper helper) {
		ModContainer mod = FabricLoader.getInstance().getModContainer(Deadlands.MOD_ID).orElseThrow();
		for (BossKind kind : BossKind.values()) {
			helper.assertTrue(DLBosses.type(kind) != null, "an entity type for " + kind.id());
			helper.assertValueEqual(DLBosses.kindOf(DLBosses.type(kind)), kind, "the type knows its boss");
			helper.assertTrue(mod.findPath("assets/deadlands/textures/entity/boss/" + kind.id() + ".png").isPresent(), "texture for " + kind.id());
		}
		BossEntity warden = DLBosses.type(BossKind.WARDEN).create(helper.getLevel(), EntitySpawnReason.COMMAND);
		helper.assertTrue(warden != null, "a boss can be created");
		double base = warden.getAttributeValue(Attributes.MAX_HEALTH);
		warden.prepare(1, 0L);
		helper.assertTrue(warden.getAttributeValue(Attributes.MAX_HEALTH) > base * 1.4, "a second pass is half again as strong");
		helper.assertValueEqual(warden.tier(), 1, "tier");
		helper.assertValueEqual(DLStory.trophies().size(), BossKind.values().length, "a trophy for every boss");
		helper.succeed();
	}
}
