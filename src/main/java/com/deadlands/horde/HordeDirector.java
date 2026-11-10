package com.deadlands.horde;

import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import com.deadlands.entity.InfectedEntity;
import com.deadlands.entity.InfectedKind;
import com.deadlands.network.DLNetworking;
import com.deadlands.network.HordeEventPayload;
import com.deadlands.persistence.ChronicleEntry;
import com.deadlands.persistence.DeadlandsWorldData;
import com.deadlands.persistence.HordeRecord;
import com.deadlands.progression.EncounterSchedule;
import com.deadlands.progression.ScheduleSettings;
import com.deadlands.progression.WorldClock;
import com.deadlands.spawning.InfectedManager;
import com.deadlands.spawning.SpawnFinder;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

/**
 * Horde nights. On every horde day of the schedule ({@link EncounterSchedule}: every
 * {@code hordes.intervalDays} days, never on a boss day) the horde comes at dusk:
 *
 * <ol>
 *   <li><b>Start.</b> Size from the schedule, half again for each extra survivor online (capped
 *       at twice {@code hordes.maxSize}). A HORDE card and sting for everyone in the overworld, a
 *       chronicle line. The horde picks a side to come from.</li>
 *   <li><b>Waves.</b> Every ten seconds a wave of six to ten arrives from that side, between
 *       {@code spawnRadiusMin} and {@code spawnRadiusMax} blocks from one survivor (taking turns),
 *       never closer to anyone than the minimum. At most 60 members are alive at once; the rest
 *       wait their turn. Members know where the survivors are ({@code HuntGoal}) and break
 *       barricades on the way.</li>
 *   <li><b>End.</b> At dawn, or as soon as every member has arrived and died (the horde is
 *       broken). Survivors are released into the world as ordinary infected.</li>
 * </ol>
 * The current horde is saved in {@link DeadlandsWorldData}; members are saved with their chunks
 * and marked with the horde's day, so a restart mid-horde carries on where it was. Sleeping
 * through a horde night is not allowed ({@code hordes.preventSleep}).
 */
public final class HordeDirector {
	/** Time of day the horde comes (just after sunset) and leaves (first light). */
	public static final long DUSK = 12500L;
	public static final long DAWN = 23200L;
	private static final long WARNING = 11000L;
	private static final int WAVE_INTERVAL_TICKS = 200;
	private static final int ALIVE_CAP = 60;

	private static long nextWaveTick;
	private static int waveCursor;

	private HordeDirector() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 3) {
				tick(server);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof InfectedEntity infected && infected.isHordeMember()) {
				HordeRecord horde = DeadlandsWorldData.get(level.getServer()).horde().orElse(null);
				if (horde == null || !horde.active() || horde.day() != infected.hordeDay()) {
					infected.setHordeDay(0L); // a member of a horde that is over: just another infected now
				}
			}
		});
		EntitySleepEvents.ALLOW_SLEEPING.register((player, pos) -> sleepProblem(player));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> sendStatus(server, handler.getPlayer())));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			nextWaveTick = 0L;
			waveCursor = 0;
		});
	}

	private static long dayOf(long dayTime) {
		return Math.floorDiv(dayTime, WorldClock.TICKS_PER_DAY) + 1;
	}

	private static long timeOfDay(long dayTime) {
		return Math.floorMod(dayTime, WorldClock.TICKS_PER_DAY);
	}

	private static boolean spawningAllowed(ServerLevel level) {
		return level.getDifficulty() != Difficulty.PEACEFUL && level.getGameRules().get(GameRules.SPAWN_MONSTERS);
	}

	// ------------------------------------------------------------------------------------ ticking

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		DeadlandsWorldData data = DeadlandsWorldData.get(server);
		DeadlandsConfig config = ConfigManager.get();
		long dayTime = level.getDayTime();
		long day = dayOf(dayTime);
		long tod = timeOfDay(dayTime);
		HordeRecord horde = data.horde().orElse(null);

		if (horde != null && horde.active()) {
			if (day != horde.day() || tod >= DAWN || !config.hordes.enabled) {
				end(server, level, data, horde, false);
				return;
			}
			int alive = countMembers(level, horde.day());
			if (horde.spawned() >= horde.total() && alive == 0) {
				end(server, level, data, horde, true);
				return;
			}
			if (level.getGameTime() >= nextWaveTick && horde.spawned() < horde.total() && alive < ALIVE_CAP && spawningAllowed(level)) {
				horde = spawnWave(level, data, horde, alive, config.hordes);
			}
			DLNetworking.broadcastHorde(server, new HordeEventPayload(HordeEventPayload.STATUS, horde.day(), horde.total(), horde.remaining()));
			return;
		}
		if (!config.hordes.enabled || !spawningAllowed(level) || tod < DUSK || tod >= DAWN) {
			return;
		}
		EncounterSchedule.Encounter tonight = EncounterSchedule.forDay(day, ScheduleSettings.fromConfig(config));
		if (tonight.kind() == EncounterSchedule.Kind.HORDE && (horde == null || horde.day() != day)) {
			start(server, level, data, day, tonight.hordeSize());
		}
	}

	private static void start(MinecraftServer server, ServerLevel level, DeadlandsWorldData data, long day, int baseSize) {
		List<ServerPlayer> survivors = survivors(level);
		if (survivors.isEmpty()) {
			return; // nobody to come for; the horde waits until someone is in the overworld tonight
		}
		DeadlandsConfig config = ConfigManager.get();
		int total = (int) Math.min(config.hordes.maxSize * 2L, Math.round(baseSize * (1.0 + 0.5 * (survivors.size() - 1))));
		total = Math.max(1, total);
		float bearing = level.getRandom().nextFloat() * Mth.TWO_PI;
		HordeRecord record = HordeRecord.begin(day, total, bearing);
		data.setHorde(record);
		// The first horde a survivor faces gets a moment: the camera turns to where it is coming from (yaw carries the bearing, radians).
		for (ServerPlayer survivor : level.players()) {
			com.deadlands.cutscene.Cutscenes.play(survivor, com.deadlands.cutscene.Cutscenes.HORDE, survivor.position(), bearing);
		}
		data.addChronicle(new ChronicleEntry(day, "chronicle.deadlands.horde_began", List.of(Long.toString(day), Integer.toString(total))),
			config.general.chronicleLength);
		nextWaveTick = level.getGameTime() + 100L;
		DLNetworking.broadcastHorde(server, new HordeEventPayload(HordeEventPayload.START, day, total, total));
		DLNetworking.broadcastStatus(server);
	}

	private static HordeRecord spawnWave(ServerLevel level, DeadlandsWorldData data, HordeRecord horde, int alive, DeadlandsConfig.Hordes config) {
		List<ServerPlayer> survivors = survivors(level);
		nextWaveTick = level.getGameTime() + WAVE_INTERVAL_TICKS;
		if (survivors.isEmpty() || !InfectedManager.hordeHasRoom(level.getServer())) {
			return horde;
		}
		RandomSource random = level.getRandom();
		ServerPlayer target = survivors.get(Math.floorMod(waveCursor++, survivors.size()));
		int wanted = Math.min(6 + random.nextInt(5), Math.min(horde.total() - horde.spawned(), ALIVE_CAP - alive));
		double min = Math.min(config.spawnRadiusMin, config.spawnRadiusMax - 4);
		double max = Math.max(config.spawnRadiusMax, min + 4);
		InfectedEntity.GroupData pack = new InfectedEntity.GroupData(InfectedEntity.newPackId(random));
		int hordeIndex = Math.max(1, (int) (horde.day() / Math.max(1, ConfigManager.get().hordes.intervalDays)));
		int arrived = 0;
		for (int attempt = 0; attempt < wanted * 4 && arrived < wanted; attempt++) {
			BlockPos pos = SpawnFinder.towards(level, target.blockPosition(), horde.bearing(), Math.PI / 4.0, min, max, random, false);
			if (pos == null || !farFromEveryone(pos, survivors, min)) {
				continue;
			}
			InfectedEntity member = SpawnFinder.spawn(level, pickKind(random, hordeIndex), pos, EntitySpawnReason.EVENT, pack);
			if (member != null) {
				member.setHordeDay(horde.day());
				arrived++;
			}
		}
		if (arrived == 0) {
			nextWaveTick = level.getGameTime() + 40L; // no room on that side yet; try again soon
			return horde;
		}
		HordeRecord updated = horde.withSpawned(horde.spawned() + arrived);
		data.setHorde(updated);
		return updated;
	}

	/** Mostly Walkers and Runners, with more Brutes as the hordes grow. */
	public static InfectedKind pickKind(RandomSource random, int hordeIndex) {
		double runner = Mth.clamp(ConfigManager.get().zombies.runnerShare * 1.3, 0.0, 0.6);
		double brute = Math.min(0.12, 0.02 + 0.01 * hordeIndex);
		double roll = random.nextDouble();
		if (roll < runner) {
			return InfectedKind.RUNNER;
		}
		roll -= runner;
		if (roll < brute) {
			return InfectedKind.BRUTE;
		}
		roll -= brute;
		if (roll < 0.07) {
			return InfectedKind.CRAWLER;
		}
		if (roll < 0.11) {
			return InfectedKind.BLOATER;
		}
		if (roll < 0.15) {
			return InfectedKind.MILITARY;
		}
		return InfectedKind.WALKER;
	}

	private static void end(MinecraftServer server, ServerLevel level, DeadlandsWorldData data, HordeRecord horde, boolean broken) {
		for (InfectedEntity infected : InfectedManager.loaded(level)) {
			if (infected.hordeDay() == horde.day()) {
				infected.setHordeDay(0L);
			}
		}
		data.setHorde(horde.ended());
		int chronicleLength = ConfigManager.get().general.chronicleLength;
		if (broken) {
			data.addChronicle(new ChronicleEntry(horde.day(), "chronicle.deadlands.horde_broken",
				List.of(Long.toString(horde.day()), Integer.toString(horde.killed()))), chronicleLength);
		} else {
			data.addChronicle(new ChronicleEntry(horde.day(), "chronicle.deadlands.horde_survived",
				List.of(Long.toString(horde.day()), Integer.toString(horde.killed()), Integer.toString(horde.total()))), chronicleLength);
		}
		DLNetworking.broadcastHorde(server, new HordeEventPayload(broken ? HordeEventPayload.BROKEN : HordeEventPayload.DAWN,
			horde.day(), horde.total(), horde.remaining()));
		DLNetworking.broadcastStatus(server);
	}

	// ------------------------------------------------------------------------------------ queries and hooks

	public static @Nullable HordeRecord active(MinecraftServer server) {
		HordeRecord horde = DeadlandsWorldData.get(server).horde().orElse(null);
		return horde != null && horde.active() ? horde : null;
	}

	/** A member died: count it toward the horde's tally (called from {@code InfectedSystems}). */
	public static void onMemberDeath(MinecraftServer server, InfectedEntity infected) {
		DeadlandsWorldData data = DeadlandsWorldData.get(server);
		HordeRecord horde = data.horde().orElse(null);
		if (horde != null && horde.active() && infected.hordeDay() == horde.day()) {
			data.setHorde(horde.withKilled(horde.killed() + 1));
		}
	}

	public static int countMembers(ServerLevel level, long day) {
		int count = 0;
		for (InfectedEntity infected : InfectedManager.loaded(level)) {
			if (infected.isAlive() && infected.hordeDay() == day) {
				count++;
			}
		}
		return count;
	}

	private static List<ServerPlayer> survivors(ServerLevel level) {
		return level.players().stream().filter(p -> p.isAlive() && !p.isSpectator()).toList();
	}

	private static boolean farFromEveryone(BlockPos pos, List<ServerPlayer> players, double min) {
		for (ServerPlayer player : players) {
			if (player.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) < min * min) {
				return false;
			}
		}
		return true;
	}

	private static Player.@Nullable BedSleepingProblem sleepProblem(Player player) {
		if (!(player.level() instanceof ServerLevel level) || !ConfigManager.get().hordes.preventSleep || level != level.getServer().overworld()) {
			return null;
		}
		if (active(level.getServer()) != null) {
			return new Player.BedSleepingProblem(Component.translatable("message.deadlands.horde.no_sleep"));
		}
		long dayTime = level.getDayTime();
		long tod = timeOfDay(dayTime);
		EncounterSchedule.Encounter today = EncounterSchedule.forDay(dayOf(dayTime), ScheduleSettings.fromConfig(ConfigManager.get()));
		if (today.kind() == EncounterSchedule.Kind.HORDE && tod >= WARNING && tod < DAWN && ConfigManager.get().hordes.enabled) {
			return new Player.BedSleepingProblem(Component.translatable("message.deadlands.horde.no_sleep"));
		}
		return null;
	}

	public static void sendStatus(MinecraftServer server, ServerPlayer player) {
		HordeRecord horde = active(server);
		DLNetworking.sendHorde(player, horde == null ? HordeEventPayload.clear()
			: new HordeEventPayload(HordeEventPayload.STATUS, horde.day(), horde.total(), horde.remaining()));
	}

	// ------------------------------------------------------------------------------------ commands

	/** Start tonight's horde now (or a horde of the next scheduled size if today has none). */
	public static boolean forceStart(MinecraftServer server) {
		if (active(server) != null) {
			return false;
		}
		ServerLevel level = server.overworld();
		long day = dayOf(level.getDayTime());
		ScheduleSettings settings = ScheduleSettings.fromConfig(ConfigManager.get());
		EncounterSchedule.Encounter today = EncounterSchedule.forDay(day, settings);
		int size = today.kind() == EncounterSchedule.Kind.HORDE ? today.hordeSize()
			: Math.max(1, EncounterSchedule.next(day, settings).hordeSize() > 0 ? EncounterSchedule.next(day, settings).hordeSize() : settings.hordeBaseSize());
		start(server, level, DeadlandsWorldData.get(server), day, size);
		return active(server) != null;
	}

	public static boolean forceStop(MinecraftServer server) {
		HordeRecord horde = active(server);
		if (horde == null) {
			return false;
		}
		end(server, server.overworld(), DeadlandsWorldData.get(server), horde, false);
		return true;
	}
}
