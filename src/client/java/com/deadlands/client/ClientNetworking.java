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
		ClientPlayNetworking.registerGlobalReceiver(com.deadlands.network.WeatherPayload.TYPE,
			(payload, context) -> com.deadlands.client.weather.ClientWeather.set(payload));
		ClientPlayNetworking.registerGlobalReceiver(com.deadlands.network.LocationPayload.TYPE,
			(payload, context) -> com.deadlands.client.sound.AmbienceDirector.set(payload));
		ClientPlayNetworking.registerGlobalReceiver(com.deadlands.network.CutscenePayload.TYPE,
			(payload, context) -> com.deadlands.client.cutscene.CutscenePlayer.start(payload));
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
