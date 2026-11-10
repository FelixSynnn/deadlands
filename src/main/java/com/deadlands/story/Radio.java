package com.deadlands.story;

import com.deadlands.config.ConfigManager;
import com.deadlands.network.RadioPayload;
import com.deadlands.network.RadioTunePayload;
import com.deadlands.npc.NpcCharacter;
import com.deadlands.npc.NpcWorldData;
import com.deadlands.npc.Settlements;
import com.deadlands.progression.EncounterSchedule;
import com.deadlands.progression.ScheduleSettings;
import com.deadlands.registry.DLStory;
import com.deadlands.world.town.TownIdentity;
import com.deadlands.world.town.TownLookup;
import com.deadlands.world.town.TownSite;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * What the radio picks up. Five stations; what each carries depends on the day, the story and how
 * many radio towers survivors have repaired. Signal strength is 35, plus 20 for each tower (up to
 * 100), less underground; the client garbles text in proportion to a weak signal.
 *
 * <ul>
 *   <li><b>87.5 Emergency.</b> The automated broadcast: shelter advice, then tonight's and
 *       tomorrow's threats (a horde, something worse).</li>
 *   <li><b>91.2 Survivors.</b> Settlements calling the roll (who, how many, how their stores are),
 *       distress calls with coordinates from any that lost people, and the recurring characters
 *       calling from wherever they are.</li>
 *   <li><b>96.4 Military.</b> Too faint until a tower is repaired. Then: Site Calder's automated recall
 *       with its grid reference, contact reports days before each boss, Camp Meridian's last loop.</li>
 *   <li><b>103.8 Unknown.</b> Needs two towers. Something is speaking. With enough of Patient Zero's
 *       records read, it says where Kessler's Wing D is.</li>
 *   <li><b>108.0 Static.</b> Mostly noise. A song, sometimes. With three towers, a numbers station.</li>
 * </ul>
 */
public final class Radio {
	public static final String[] STATIONS = {"87.5", "91.2", "96.4", "103.8", "108.0"};

	private Radio() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(RadioTunePayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (player.getInventory().contains(new net.minecraft.world.item.ItemStack(DLStory.RADIO))) {
				int station = Math.floorMod(payload.station(), STATIONS.length);
				ServerPlayNetworking.send(player, new RadioPayload(station, transmission(player, station), signal(player, station)));
			}
		});
	}

	public static int signal(ServerPlayer player, int station) {
		StoryState story = StoryState.get(player.level().getServer());
		int signal = Math.min(100, 35 + 20 * story.towers().size());
		ServerLevel level = player.level();
		if (player.getY() < level.getSeaLevel() - 8 && !level.canSeeSky(player.blockPosition())) {
			signal -= 30;
		}
		if (station == 2 && story.towers().isEmpty()) {
			signal = Math.min(signal, 25);
		}
		return Math.max(5, signal);
	}

	public static List<Component> transmission(ServerPlayer player, int station) {
		ServerLevel level = player.level();
		StoryState story = StoryState.get(level.getServer());
		long day = level.getDayTime() / 24000L + 1L;
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("radio.deadlands.station." + station));
		switch (station) {
			case 0 -> emergency(lines, level, day);
			case 1 -> survivors(lines, player, story);
			case 2 -> military(lines, player, story, day);
			case 3 -> unknown(lines, story, day);
			default -> noise(lines, player, story);
		}
		return lines;
	}

	private static void emergency(List<Component> lines, ServerLevel level, long day) {
		lines.add(Component.translatable(day <= 3 ? "radio.deadlands.emergency.shelter" : "radio.deadlands.emergency.automated"));
		ScheduleSettings schedule = ScheduleSettings.fromConfig(ConfigManager.get());
		EncounterSchedule.Encounter tonight = EncounterSchedule.forDay(day, schedule);
		EncounterSchedule.Encounter tomorrow = EncounterSchedule.forDay(day + 1, schedule);
		if (tonight.kind() == EncounterSchedule.Kind.HORDE) {
			lines.add(Component.translatable("radio.deadlands.emergency.horde_tonight"));
		} else if (tonight.kind() == EncounterSchedule.Kind.BOSS) {
			lines.add(Component.translatable("radio.deadlands.emergency.threat_tonight"));
		} else if (tomorrow.kind() != EncounterSchedule.Kind.NONE) {
			lines.add(Component.translatable("radio.deadlands.emergency.tomorrow"));
		}
		lines.add(Component.translatable("radio.deadlands.emergency.tip." + Math.floorMod(day, 6L)));
		for (com.deadlands.event.WorldEvents.Recent event : com.deadlands.event.WorldEvents.state(level.getServer()).recent()) {
			if (day - event.day() <= 1) {
				lines.add(Component.translatable("radio.deadlands.emergency.event", Component.translatable("hud.deadlands.event." + event.kind()), event.x(), event.z()));
			}
		}
	}

	private static void survivors(List<Component> lines, ServerPlayer player, StoryState story) {
		ServerLevel level = player.level();
		List<Settlements.Settlement> heard = new ArrayList<>(Settlements.get(level.getServer()).all());
		heard.removeIf(settlement -> !settlement.inhabited());
		heard.sort(Comparator.comparingDouble(settlement -> settlement.center().distSqr(player.blockPosition())));
		for (Settlements.Settlement settlement : heard.subList(0, Math.min(2, heard.size()))) {
			lines.add(Component.translatable("radio.deadlands.survivors.rollcall", placeName(level, settlement.center(), settlement.camp()),
				settlement.population(), Component.translatable("npc.deadlands.settlement.supplies." + settlement.suppliesLevel())));
		}
		for (Settlements.Settlement settlement : heard) {
			if ("attack_lost".equals(settlement.lastEvent())) {
				lines.add(Component.translatable("radio.deadlands.survivors.distress", placeName(level, settlement.center(), settlement.camp()),
					settlement.center().getX(), settlement.center().getZ()));
				break;
			}
		}
		NpcWorldData characters = NpcWorldData.get(level.getServer());
		for (NpcCharacter character : List.of(NpcCharacter.ITZBREADLOAF, NpcCharacter.IMLILYLOL, NpcCharacter.FELIXSYN)) {
			BlockPos where = characters.position(character);
			if (where != null && !story.has(StoryState.MET + character.id())) {
				lines.add(Component.translatable("radio.deadlands.survivors." + character.id(), placeName(level, where, false), where.getX(), where.getZ()));
			}
		}
		if (story.has(StoryState.LILY_DONE)) {
			lines.add(Component.translatable("radio.deadlands.survivors.lily_relay"));
		}
		if (lines.size() == 1) {
			lines.add(Component.translatable("radio.deadlands.survivors.quiet"));
		}
	}

	private static void military(List<Component> lines, ServerPlayer player, StoryState story, long day) {
		if (story.towers().isEmpty()) {
			lines.add(Component.translatable("radio.deadlands.military.faint"));
			return;
		}
		BlockPos calder = story.place(StoryPlaces.Place.MILITARY_FACILITY);
		if (calder != null) {
			lines.add(Component.translatable("radio.deadlands.military.recall", calder.getX(), calder.getZ()));
			if (story.set(StoryState.CALDER_KNOWN)) {
				player.displayClientMessage(Component.translatable("message.deadlands.story.calder_known"), false);
			}
		} else {
			lines.add(Component.translatable("radio.deadlands.military.recall_unknown"));
		}
		ScheduleSettings schedule = ScheduleSettings.fromConfig(ConfigManager.get());
		for (long ahead = 0; ahead <= 3; ahead++) {
			EncounterSchedule.Encounter encounter = EncounterSchedule.forDay(day + ahead, schedule);
			if (encounter.kind() == EncounterSchedule.Kind.BOSS && encounter.boss() != null) {
				lines.add(Component.translatable("radio.deadlands.military.contact", encounter.boss().displayName(), ahead));
				break;
			}
		}
		lines.add(Component.translatable(story.has(StoryState.BOSS + "warden") ? "radio.deadlands.military.hale_silent"
			: "radio.deadlands.military.meridian." + Math.floorMod(day, 3L)));
	}

	private static void unknown(List<Component> lines, StoryState story, long day) {
		if (story.towers().size() < 2) {
			lines.add(Component.translatable("radio.deadlands.static"));
			return;
		}
		if (story.has(StoryState.BOSS + "patient_zero")) {
			lines.add(Component.translatable("radio.deadlands.unknown.silent"));
			return;
		}
		lines.add(Component.translatable("radio.deadlands.unknown.whisper." + Math.floorMod(day, 4L)));
		BlockPos kessler = story.place(StoryPlaces.Place.RESEARCH_FACILITY);
		if (story.seriesRead("p0") >= 3 && kessler != null) {
			lines.add(Component.translatable("radio.deadlands.unknown.kessler", kessler.getX(), kessler.getZ()));
		}
	}

	private static void noise(List<Component> lines, ServerPlayer player, StoryState story) {
		lines.add(Component.translatable("radio.deadlands.static"));
		if (player.getRandom().nextInt(4) == 0) {
			lines.add(Component.translatable("radio.deadlands.static.song"));
		}
		BlockPos meridian = story.place(StoryPlaces.Place.EVACUATION_SITE);
		if (story.towers().size() >= 3 && meridian != null) {
			lines.add(Component.translatable("radio.deadlands.static.numbers", meridian.getX(), meridian.getZ()));
		}
	}

	private static Component placeName(ServerLevel level, BlockPos pos, boolean camp) {
		if (camp) {
			return Component.translatable("radio.deadlands.a_camp");
		}
		TownSite town = TownLookup.nearest(level, pos, 220.0).orElse(null);
		return town == null ? Component.translatable("radio.deadlands.open_country") : Component.literal(TownIdentity.of(level.getSeed(), town).name());
	}

	/** For the transmitter block: who to tell when a tower comes back. */
	public static void towerRepaired(ServerLevel level, @Nullable ServerPlayer by) {
		int towers = StoryState.get(level.getServer()).towers().size();
		for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
			player.displayClientMessage(Component.translatable("message.deadlands.radio.tower", towers), false);
		}
		if (by != null) {
			Quests.chronicle(by, "chronicle.deadlands.story.tower");
		}
	}
}
