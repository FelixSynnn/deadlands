package com.deadlands.network;

import com.deadlands.config.ConfigManager;
import com.deadlands.persistence.DeadlandsWorldData;
import com.deadlands.persistence.PlayerRecord;
import com.deadlands.progression.ScheduleSettings;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Registers DEADLANDS packets and sends them. The server is authoritative: clients only ever
 * receive state, and the single client-to-server packet merely asks for a fresh copy.
 */
public final class DLNetworking {
	public static final int CHRONICLE_LINES_SYNCED = 16;

	private static volatile @Nullable MinecraftServer activeServer;

	private DLNetworking() {
	}

	public static void init() {
		PayloadTypeRegistry.playS2C().register(WorldStatusPayload.TYPE, WorldStatusPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ChaseMusicPayload.TYPE, ChaseMusicPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(HorrorScarePayload.TYPE, HorrorScarePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(MissionSyncPayload.TYPE, MissionSyncPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(HorrorBusyPayload.TYPE, HorrorBusyPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(DayBeginPayload.TYPE, DayBeginPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(TownCardPayload.TYPE, TownCardPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(HordeEventPayload.TYPE, HordeEventPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SurvivalPayload.TYPE, SurvivalPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SkillsPayload.TYPE, SkillsPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(DialoguePayload.TYPE, DialoguePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(JournalPayload.TYPE, JournalPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ArchivePayload.TYPE, ArchivePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(BossEventPayload.TYPE, BossEventPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(EndingChoicePayload.TYPE, EndingChoicePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(EndingPayload.TYPE, EndingPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(WeatherPayload.TYPE, WeatherPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(WorldEventPayload.TYPE, WorldEventPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(LocationPayload.TYPE, LocationPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CutscenePayload.TYPE, CutscenePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(EndingChosenPayload.TYPE, EndingChosenPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(IntroPayload.TYPE, IntroPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(RadioPayload.TYPE, RadioPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(IntroDonePayload.TYPE, IntroDonePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(RadioTunePayload.TYPE, RadioTunePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DialogueChoicePayload.TYPE, DialogueChoicePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(VehicleControlPayload.TYPE, VehicleControlPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(RequestStatusPayload.TYPE, RequestStatusPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(RequestStatusPayload.TYPE, (payload, context) -> {
			sendStatus(context.player());
			sendSkills(context.player());
			com.deadlands.progression.Tasks.sync(context.player(), true);
			com.deadlands.story.Archive.sync(context.player());
		});
		ServerPlayNetworking.registerGlobalReceiver(VehicleControlPayload.TYPE, (payload, context) -> {
			if (context.player().getVehicle() instanceof com.deadlands.vehicle.VehicleEntity vehicle) {
				vehicle.control(context.player(), payload.action());
			}
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> activeServer = server);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> activeServer = null);
		// Settings edited in-game (singleplayer) or reloaded by command reach every client.
		ConfigManager.addListener(config -> {
			MinecraftServer server = activeServer;
			if (server != null) {
				server.execute(() -> broadcastStatus(server));
			}
		});
	}

	/** The survivor's own skill experience, for their journal. */
	public static void sendSkills(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, SkillsPayload.TYPE)) {
			ServerPlayNetworking.send(player, SkillsPayload.of(com.deadlands.skill.Skills.get(player)));
		}
	}

	public static void sendStatus(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, WorldStatusPayload.TYPE)) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		DeadlandsWorldData data = DeadlandsWorldData.get(server);
		long dayIndex = server.overworld().getDayCount();
		PlayerRecord record = data.player(player.getUUID()).orElse(null);
		ServerPlayNetworking.send(player, new WorldStatusPayload(
			dayIndex + 1,
			ScheduleSettings.fromConfig(ConfigManager.get()),
			record == null ? 0 : record.firstJoinDay() + 1,
			record == null || record.lastDeathDay() < 0 ? 0 : record.lastDeathDay() + 1,
			record == null ? 0 : record.deaths(),
			record == null ? 0 : record.daysSurvived(dayIndex),
			record == null ? 0 : record.infectedKilled(),
			data.recentChronicle(CHRONICLE_LINES_SYNCED)
		));
	}

	public static void broadcastStatus(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			sendStatus(player);
		}
	}

	public static void sendDayBegin(ServerPlayer player, long day, boolean firstArrival) {
		if (ServerPlayNetworking.canSend(player, DayBeginPayload.TYPE)) {
			ServerPlayNetworking.send(player, new DayBeginPayload(day, firstArrival));
		}
	}

	public static void sendTownCard(ServerPlayer player, TownCardPayload card) {
		if (ServerPlayNetworking.canSend(player, TownCardPayload.TYPE)) {
			ServerPlayNetworking.send(player, card);
		}
	}

	public static void sendHorde(ServerPlayer player, HordeEventPayload payload) {
		if (ServerPlayNetworking.canSend(player, HordeEventPayload.TYPE)) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	/** To every player in the overworld, where hordes happen. */
	public static void broadcastHorde(MinecraftServer server, HordeEventPayload payload) {
		for (ServerPlayer player : server.overworld().players()) {
			sendHorde(player, payload);
		}
	}

	public static void broadcastDayBegin(MinecraftServer server, long day) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			sendDayBegin(player, day, false);
		}
	}
}
