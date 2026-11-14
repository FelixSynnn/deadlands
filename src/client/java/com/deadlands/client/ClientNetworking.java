package com.deadlands.client;

import com.deadlands.client.hud.DayCardHud;
import com.deadlands.client.hud.HordeHud;
import com.deadlands.client.hud.TownCardHud;
import com.deadlands.network.DayBeginPayload;
import com.deadlands.network.HordeEventPayload;
import com.deadlands.network.RequestStatusPayload;
import com.deadlands.network.SurvivalPayload;
import com.deadlands.survival.LocalSurvival;
import com.deadlands.network.TownCardPayload;
import com.deadlands.network.WorldStatusPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client packet handlers (Fabric runs them on the client thread). */
public final class ClientNetworking {
	private ClientNetworking() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(WorldStatusPayload.TYPE, (payload, context) -> ClientState.setStatus(payload));
		ClientPlayNetworking.registerGlobalReceiver(DayBeginPayload.TYPE, (payload, context) -> DayCardHud.show(payload.day(), payload.firstArrival()));
		ClientPlayNetworking.registerGlobalReceiver(TownCardPayload.TYPE, (payload, context) -> TownCardHud.show(payload));
		ClientPlayNetworking.registerGlobalReceiver(HordeEventPayload.TYPE, (payload, context) -> HordeHud.handle(payload));
		ClientPlayNetworking.registerGlobalReceiver(com.deadlands.network.DialoguePayload.TYPE,
			(payload, context) -> com.deadlands.client.ui.DialogueScreen.show(payload));
		ClientPlayNetworking.registerGlobalReceiver(SurvivalPayload.TYPE, (payload, context) -> LocalSurvival.set(payload));
		ClientPlayNetworking.registerGlobalReceiver(com.deadlands.network.JournalPayload.TYPE, (payload, context) -> ClientState.setJournal(payload));
		ClientPlayNetworking.registerGlobalReceiver(com.deadlands.network.EndingChoicePayload.TYPE,
			(payload, context) -> context.client().setScreen(new com.deadlands.client.ui.EndingScreen.Choice(payload)));
		ClientPlayNetworking.registerGlobalReceiver(com.deadlands.network.StoryPayload.TYPE,
			(payload, context) -> com.deadlands.client.ui.JournalScreen.story(payload));
		ClientPlayNetworking.registerGlobalReceiver(com.deadlands.network.EndingPayload.TYPE,
			(payload, context) -> context.client().setScreen(new com.deadlands.client.ui.EndingScreen(payload)));
		ClientPlayNetworking.registerGlobalReceiver(com.deadlands.network.BossEventPayload.TYPE,
			(payload, context) -> com.deadlands.client.hud.BossCardHud.handle(payload));
		ClientPlayNetworking.registerGlobalReceiver(com.deadlands.network.ArchivePayload.TYPE, (payload, context) -> ClientState.setArchive(payload));
		ClientPlayNetworking.registerGlobalReceiver(com.deadlands.network.RadioPayload.TYPE,
			(payload, context) -> com.deadlands.client.ui.RadioScreen.receive(payload));
		ClientPlayNetworking.registerGlobalReceiver(com.deadlands.network.IntroPayload.TYPE,
			(payload, context) -> context.client().setScreen(new com.deadlands.client.ui.IntroScreen(payload.harleyNear(),
				new net.minecraft.world.phys.Vec3(payload.x(), payload.y(), payload.z()))));
		ClientPlayNetworking.registerGlobalReceiver(com.deadlands.network.HorrorScarePayload.TYPE,
			(payload, context) -> com.deadlands.client.horror.ScarePlayer.onScare(payload));
		ClientPlayNetworking.registerGlobalReceiver(com.deadlands.network.AchievementSecretsPayload.TYPE,
			(payload, context) -> com.deadlands.client.achievement.DLAchievementsScreen.secrets(payload.ids()));
		ClientPlayNetworking.registerGlobalReceiver(com.deadlands.network.MissionSyncPayload.TYPE,
			(payload, context) -> com.deadlands.client.mission.ClientMissions.update(payload));
		ClientPlayNetworking.registerGlobalReceiver(com.deadlands.network.ChaseMusicPayload.TYPE,
			(payload, context) -> com.deadlands.client.sound.MusicDirector.onChase(payload.chased()));
		ClientPlayNetworking.registerGlobalReceiver(com.deadlands.network.WeatherPayload.TYPE,
			(payload, context) -> com.deadlands.client.weather.ClientWeather.set(payload));
		ClientPlayNetworking.registerGlobalReceiver(com.deadlands.network.LocationPayload.TYPE,
			(payload, context) -> com.deadlands.client.sound.AmbienceDirector.set(payload));
		ClientPlayNetworking.registerGlobalReceiver(com.deadlands.network.DisasterPayload.TYPE,
			(payload, context) -> com.deadlands.client.weather.ClientDisaster.update(payload));
		ClientPlayNetworking.registerGlobalReceiver(com.deadlands.network.CutscenePayload.TYPE,
			(payload, context) -> {
				try {
					com.deadlands.client.cutscene.CutscenePlayer.start(payload);
				} catch (RuntimeException failure) {
					com.deadlands.Deadlands.LOGGER.error("DEADLANDS: cutscene {} failed to start; skipped", payload.id(), failure);
					com.deadlands.client.cutscene.CutscenePlayer.stop();
				}
			});
		ClientPlayNetworking.registerGlobalReceiver(com.deadlands.network.WorldEventPayload.TYPE,
			(payload, context) -> com.deadlands.client.hud.BossCardHud.event(payload));
		ClientPlayNetworking.registerGlobalReceiver(com.deadlands.network.SkillsPayload.TYPE, (payload, context) -> ClientState.setSkills(payload));
	}

	public static void requestStatus() {
		if (ClientPlayNetworking.canSend(RequestStatusPayload.TYPE)) {
			ClientPlayNetworking.send(RequestStatusPayload.INSTANCE);
		}
	}
}
