package com.deadlands.test;

import com.deadlands.Deadlands;
import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import com.deadlands.event.DayChangeCallback;
import com.deadlands.persistence.ChronicleEntry;
import com.deadlands.persistence.DeadlandsWorldData;
import com.deadlands.persistence.PlayerRecord;
import com.deadlands.progression.BossKind;
import com.deadlands.progression.EncounterSchedule;
import com.deadlands.progression.EncounterSchedule.Encounter;
import com.deadlands.progression.EncounterSchedule.Kind;
import com.deadlands.progression.ScheduleSettings;
import com.deadlands.progression.TimeOfDay;
import com.deadlands.progression.WorldClock;
import com.deadlands.registry.DLCreativeTabs;
import com.deadlands.registry.DLItems;
import com.deadlands.registry.DLSounds;
import com.deadlands.registry.DLTab;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;

/**
 * Server game tests. They run on a real dedicated-server instance during {@code gradlew build},
 * so every build proves the mod boots server-side, registers its content, ships its assets and
 * keeps its persistence and schedule logic correct.
 */
public class DeadlandsServerGameTest {
	private static final Gson GSON = new GsonBuilder().create();
	private static final AtomicInteger DAY_EVENTS = new AtomicInteger();
	private static volatile boolean countingDays;

	static {
		DayChangeCallback.EVENT.register((server, previousDay, newDay, skipped) -> {
			if (countingDays) {
				DAY_EVENTS.incrementAndGet();
			}
		});
	}

	@GameTest
	public void contentIsRegistered(GameTestHelper helper) {
		helper.assertTrue(BuiltInRegistries.ITEM.containsKey(Deadlands.id("survivor_journal")), "Survivor's Journal is not registered");
		for (Holder.Reference<SoundEvent> sound : DLSounds.all()) {
			helper.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(sound.key()), "Sound event not registered: " + sound.key().identifier());
		}
		helper.assertFalse(DLCreativeTabs.registered().isEmpty(), "No creative tabs were registered");
		for (DLTab tab : DLCreativeTabs.registered()) {
			helper.assertTrue(BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(tab.key()), "Creative tab not registered: " + tab.id());
			helper.assertFalse(tab.entries().isEmpty(), "Registered creative tab is empty: " + tab.id());
		}
		helper.succeed();
	}

	@GameTest
	public void everyItemSoundAndSettingHasItsAssets(GameTestHelper helper) {
		ModContainer mod = FabricLoader.getInstance().getModContainer(Deadlands.MOD_ID).orElseThrow();
		JsonObject lang = json(helper, mod, "assets/deadlands/lang/en_us.json");
		JsonObject sounds = json(helper, mod, "assets/deadlands/sounds.json");

		for (Item item : DLItems.all()) {
			String name = BuiltInRegistries.ITEM.getKey(item).getPath();
			for (String path : List.of("assets/deadlands/items/" + name + ".json", "assets/deadlands/models/item/" + name + ".json",
				"assets/deadlands/textures/item/" + name + ".png")) {
				helper.assertTrue(mod.findPath(path).isPresent(), "Missing asset " + path);
			}
			// The key the game shows: block items (the security door) use their block's name.
			helper.assertTrue(lang.has(item.getDescriptionId()), "Missing English name for item " + name);
		}
		for (Holder.Reference<SoundEvent> sound : DLSounds.all()) {
			String event = sound.key().identifier().getPath();
			helper.assertTrue(sounds.has(event), "sounds.json has no entry for " + event);
			for (JsonElement entry : sounds.getAsJsonObject(event).getAsJsonArray("sounds")) {
				String file = (entry.isJsonObject() ? entry.getAsJsonObject().get("name") : entry).getAsString();
				String path = "assets/deadlands/sounds/" + file.substring(file.indexOf(':') + 1) + ".ogg";
				helper.assertTrue(mod.findPath(path).isPresent(), "Missing sound file " + path);
			}
		}
		for (DLTab tab : DLTab.values()) {
			helper.assertTrue(lang.has(tab.translationKey()), "Missing English name for creative tab " + tab.id());
		}
		for (ConfigManager.Section section : ConfigManager.layout()) {
			helper.assertTrue(lang.has(section.translationKey()), "Missing English name for settings section " + section.key());
			for (ConfigManager.Entry entry : section.entries()) {
				helper.assertTrue(lang.has(entry.translationKey()), "Missing English label for setting " + entry.translationKey());
			}
		}
		for (BossKind boss : BossKind.values()) {
			helper.assertTrue(lang.has("boss.deadlands." + boss.id()), "Missing English name for boss " + boss.id());
		}
		for (String texture : List.of("textures/gui/title/sky.png", "textures/gui/title/logo.png", "textures/gui/journal/book.png",
			"textures/gui/sprites/widget/panel.png", "icon.png")) {
			helper.assertTrue(mod.findPath("assets/deadlands/" + texture).isPresent(), "Missing texture " + texture);
		}
		helper.succeed();
	}

	@GameTest
	public void configRoundTripsAndClamps(GameTestHelper helper) {
		DeadlandsConfig defaults = new DeadlandsConfig();
		List<String> problems = new ArrayList<>();
		DeadlandsConfig parsed = ConfigManager.parse(ConfigManager.toJsonc(defaults), problems);
		helper.assertTrue(problems.isEmpty(), "Default config produced problems: " + problems);
		helper.assertValueEqual(GSON.toJson(parsed), GSON.toJson(defaults), "config written then read back");

		problems.clear();
		DeadlandsConfig clamped = ConfigManager.parse("""
			// comments are allowed
			{ "configVersion": 1, "hordes": { "intervalDays": 999, "growthPerHorde": -3.0, "enabled": "yes" },
			  "survival": { "difficulty": "nightmare" }, "mystery": {} }
			""", problems);
		helper.assertValueEqual(clamped.hordes.intervalDays, 60, "intervalDays clamped to its maximum");
		helper.assertValueEqual(clamped.hordes.growthPerHorde, 0.0, "growthPerHorde clamped to its minimum");
		helper.assertTrue(clamped.hordes.enabled, "an invalid boolean keeps its default");
		helper.assertValueEqual(clamped.survival.difficulty, DeadlandsConfig.SurvivalDifficulty.NIGHTMARE, "enum names are case-insensitive");
		helper.assertTrue(problems.size() >= 4, "out-of-range, invalid and unknown values are all reported: " + problems);
		helper.succeed();
	}

	@GameTest
	public void encounterScheduleFollowsTheDesign(GameTestHelper helper) {
		ScheduleSettings s = ScheduleSettings.DEFAULT;
		helper.assertValueEqual(EncounterSchedule.forDay(1, s).kind(), Kind.NONE, "day 1");
		Encounter day5 = EncounterSchedule.forDay(5, s);
		helper.assertValueEqual(day5.kind(), Kind.HORDE, "day 5 kind");
		helper.assertValueEqual(day5.hordeSize(), 30, "first horde is about 30 infected");
		BossKind[] expected = {BossKind.ABOMINATION, BossKind.BUTCHER, BossKind.WARDEN, BossKind.EXPERIMENT, BossKind.PATIENT_ZERO};
		for (int i = 0; i < expected.length; i++) {
			Encounter boss = EncounterSchedule.forDay((i + 1) * 10L, s);
			helper.assertValueEqual(boss.kind(), Kind.BOSS, "day " + (i + 1) * 10 + " replaces the horde with a boss");
			helper.assertValueEqual(boss.boss(), expected[i], "boss on day " + (i + 1) * 10);
			helper.assertValueEqual(boss.tier(), 0, "first pass is tier 0");
		}
		Encounter day60 = EncounterSchedule.forDay(60, s);
		helper.assertValueEqual(day60.boss(), BossKind.ABOMINATION, "day 60 starts the escalation pass");
		helper.assertValueEqual(day60.tier(), 1, "day 60 escalates");
		Encounter day15 = EncounterSchedule.forDay(15, s);
		helper.assertValueEqual(day15.hordeIndex(), 2, "day 15 is the second horde");
		helper.assertValueEqual(day15.hordeSize(), 35, "second horde grows by 15%");
		helper.assertValueEqual(EncounterSchedule.next(11, s).day(), 15L, "next encounter after day 11");
		helper.assertValueEqual(EncounterSchedule.upcoming(1, 4, s).stream().map(Encounter::day).toList(), List.of(5L, 10L, 15L, 20L), "first four encounters");
		helper.assertTrue(EncounterSchedule.forDay(10_000, s).hordeSize() <= s.hordeMaxSize(), "hordes respect the size cap");
		ScheduleSettings off = new ScheduleSettings(false, 5, 30, 0.15, 120, false, 10);
		helper.assertTrue(EncounterSchedule.next(1, off).isNone(), "nothing is scheduled when both systems are off");
		helper.assertValueEqual(TimeOfDay.clock(0), "06:00", "day time 0 is 06:00");
		helper.assertValueEqual(TimeOfDay.clock(18000), "00:00", "day time 18000 is midnight");
		helper.succeed();
	}

	@GameTest
	public void worldDataSurvivesSaveAndLoad(GameTestHelper helper) {
		DeadlandsWorldData data = new DeadlandsWorldData();
		data.setLastProcessedDay(41);
		UUID alex = UUID.fromString("00000000-0000-4000-8000-000000000001");
		PlayerRecord record = data.getOrCreatePlayer(alex, "Alex", 3);
		record.recordDeath(12);
		record.setJournalGiven(true);
		data.getOrCreatePlayer(UUID.fromString("00000000-0000-4000-8000-000000000002"), "Sam", 7);
		data.addChronicle(new ChronicleEntry(4, "chronicle.deadlands.arrived", List.of("Alex")), 64);
		data.addChronicle(new ChronicleEntry(13, "chronicle.deadlands.died", List.of("Alex", "9")), 64);

		Tag tag = DeadlandsWorldData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
		DeadlandsWorldData loaded = DeadlandsWorldData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
		helper.assertValueEqual(loaded, data, "world data after a save/load round trip");
		helper.assertValueEqual(loaded.player(alex).orElseThrow().daysSurvived(20), 8L, "days survived counts from the last death");

		DeadlandsWorldData capped = new DeadlandsWorldData();
		for (int i = 0; i < 20; i++) {
			capped.addChronicle(new ChronicleEntry(i, "chronicle.deadlands.arrived", List.of("p" + i)), 8);
		}
		helper.assertValueEqual(capped.chronicle().size(), 8, "chronicle keeps only the newest entries");
		helper.assertValueEqual(capped.chronicle().getFirst().day(), 12L, "oldest kept entry");
		helper.succeed();
	}

	@GameTest
	public void worldClockFiresOncePerDayAndHandlesRewinds(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		long originalTime = server.overworld().getDayTime();
		DeadlandsWorldData data = DeadlandsWorldData.get(server);
		try {
			WorldClock.tick(server);
			long today = server.overworld().getDayCount();
			helper.assertValueEqual(data.lastProcessedDay(), today, "clock is in sync before the test");

			DAY_EVENTS.set(0);
			countingDays = true;
			WorldClock.setDayTime(server, (today + 1) * WorldClock.TICKS_PER_DAY + 100);
			helper.assertTrue(WorldClock.tick(server), "a new day is announced");
			helper.assertFalse(WorldClock.tick(server), "the same day is never announced twice");
			helper.assertValueEqual(DAY_EVENTS.get(), 1, "DayChangeCallback fired exactly once");
			helper.assertValueEqual(data.lastProcessedDay(), today + 1, "processed day is persisted");

			WorldClock.setDayTime(server, (today + 4) * WorldClock.TICKS_PER_DAY);
			helper.assertTrue(WorldClock.tick(server), "jumping ahead announces the new day");
			helper.assertValueEqual(DAY_EVENTS.get(), 2, "one event for a multi-day jump");

			WorldClock.setDayTime(server, originalTime);
			helper.assertFalse(WorldClock.tick(server), "moving time backwards does not announce a day");
			helper.assertValueEqual(DAY_EVENTS.get(), 2, "no event on rewind");
			helper.assertValueEqual(data.lastProcessedDay(), server.overworld().getDayCount(), "rewind re-syncs the clock");
		} finally {
			countingDays = false;
			WorldClock.setDayTime(server, originalTime);
			WorldClock.tick(server);
		}
		helper.succeed();
	}

	private static JsonObject json(GameTestHelper helper, ModContainer mod, String path) {
		Path file = mod.findPath(path).orElse(null);
		helper.assertTrue(file != null, "Missing " + path);
		try {
			return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw helper.assertionException("Could not read %s: %s", path, e.getMessage());
		}
	}
}
