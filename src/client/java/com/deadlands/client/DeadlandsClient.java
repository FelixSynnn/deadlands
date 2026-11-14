package com.deadlands.client;

import com.deadlands.Deadlands;
import com.deadlands.bridge.ClientBridge;
import com.deadlands.client.hud.DayCardHud;
import com.deadlands.client.hud.HordeHud;
import com.deadlands.client.hud.SurvivalHud;
import com.deadlands.survival.LocalSurvival;
import com.deadlands.client.hud.TownCardHud;
import com.deadlands.client.input.DLKeyMappings;
import com.deadlands.client.ui.JournalScreen;
import com.deadlands.client.ui.TitleBackdrop;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import com.deadlands.client.render.LootContainerRenderer;
import com.deadlands.client.render.infected.InfectedLayers;
import com.deadlands.client.render.infected.InfectedRenderer;
import com.deadlands.client.sound.InfectedVoices;
import com.deadlands.entity.InfectedEntity;
import com.deadlands.entity.InfectedKind;
import com.deadlands.registry.DLEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import com.deadlands.client.ui.DLContainerScreen;
import com.deadlands.registry.DLBlockEntities;
import com.deadlands.registry.DLBlocks;
import com.deadlands.registry.DLMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;

/** DEADLANDS client entrypoint: input, HUD, client networking, entity renderers and the bridges for common code. */
public final class DeadlandsClient implements ClientModInitializer {
	private static final InfectedVoices VOICES = new InfectedVoices();

	@Override
	public void onInitializeClient() {
		DLKeyMappings.init();
		ClientNetworking.init();
		BlockRenderLayerMap.putBlock(DLBlocks.BROKEN_GLASS, ChunkSectionLayer.TRANSLUCENT);
		BlockRenderLayerMap.putBlock(DLBlocks.DIRTY_GLASS, ChunkSectionLayer.TRANSLUCENT);
		BlockRenderLayerMap.putBlock(DLBlocks.CRACKED_GLASS, ChunkSectionLayer.TRANSLUCENT);
		BlockRenderLayerMap.putBlock(DLBlocks.DUSTY_GLASS, ChunkSectionLayer.TRANSLUCENT);
		BlockRenderLayerMap.putBlock(DLBlocks.DEAD_LEAVES, ChunkSectionLayer.CUTOUT);
		DLBlocks.decals().forEach(decal -> BlockRenderLayerMap.putBlock(decal, ChunkSectionLayer.TRANSLUCENT));
		InfectedLayers.register();
		for (InfectedKind kind : InfectedKind.values()) {
			EntityRendererRegistry.<InfectedEntity>register(DLEntities.type(kind), context -> new InfectedRenderer(context, kind));
		}
		EntityRendererRegistry.register(com.deadlands.registry.DLMarkers.SURVIVOR, com.deadlands.client.render.NpcRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(com.deadlands.client.render.AsesinoGeometry.LAYER,
			com.deadlands.client.render.AsesinoGeometry::create);
		net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(com.deadlands.client.render.AberrantGeometry.LAYER,
			com.deadlands.client.render.AberrantGeometry::create);
		EntityRendererRegistry.register(com.deadlands.registry.DLAsesino.ASESINO, com.deadlands.client.render.AsesinoRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(com.deadlands.client.render.GuestGeometry.LAYER,
			com.deadlands.client.render.GuestGeometry::create);
		EntityRendererRegistry.register(com.deadlands.registry.DLGuest.GUEST, com.deadlands.client.render.GuestRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(com.deadlands.client.render.DotGeometry.LAYER,
			com.deadlands.client.render.DotGeometry::create);
		EntityRendererRegistry.register(com.deadlands.registry.DLDot.DOT, com.deadlands.client.render.DotRenderer::new);
		com.deadlands.client.horror.DotScareOverlay.init();
		com.deadlands.client.render.vehicle.VehicleRenderer.registerLayers();
		com.deadlands.client.render.boss.BossRenderer.registerLayers();
		com.deadlands.client.particle.DLParticleProviders.register();
		net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(com.deadlands.client.render.SupplyDropRenderer.LAYER,
			com.deadlands.client.render.SupplyDropGeometry::supply_drop);
		EntityRendererRegistry.register(com.deadlands.registry.DLEvents.SUPPLY_DROP, com.deadlands.client.render.SupplyDropRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(com.deadlands.client.render.HelicopterRenderer.LAYER,
			com.deadlands.client.render.HelicopterGeometry::helicopter);
		EntityRendererRegistry.register(com.deadlands.registry.DLStory.HELICOPTER, com.deadlands.client.render.HelicopterRenderer::new);
		for (com.deadlands.progression.BossKind boss : com.deadlands.progression.BossKind.values()) {
			EntityRendererRegistry.register(com.deadlands.registry.DLBosses.type(boss),
				context -> new com.deadlands.client.render.boss.BossRenderer(context, boss));
		}
		for (com.deadlands.vehicle.VehicleKind vehicle : com.deadlands.vehicle.VehicleKind.values()) {
			EntityRendererRegistry.register(com.deadlands.registry.DLVehicles.type(vehicle),
				context -> new com.deadlands.client.render.vehicle.VehicleRenderer(context, vehicle));
		}
		BlockEntityRenderers.register(DLBlockEntities.LOOT_CONTAINER, LootContainerRenderer::new);
		MenuScreens.register(DLMenus.LOOT_CONTAINER, DLContainerScreen::new);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Deadlands.id("day_card"), DayCardHud::render);
		// Darkness and the flashlight beam on the screen, for shader packs and Sodium (client/light/ShaderDarkness).
		HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, Deadlands.id("shader_darkness"), com.deadlands.client.light.ShaderDarkness::render);
		// Phase 4: opening a door, gate or trapdoor pushes with the hand in first person.
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClientSide() && hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
				net.minecraft.world.level.block.Block block = world.getBlockState(hit.getBlockPos()).getBlock();
				if (block instanceof net.minecraft.world.level.block.DoorBlock || block instanceof net.minecraft.world.level.block.TrapDoorBlock
					|| block instanceof net.minecraft.world.level.block.FenceGateBlock) {
					com.deadlands.client.firstperson.FirstPersonMotion.push();
				}
			}
			return net.minecraft.world.InteractionResult.PASS;
		});
		// Phase 4 jumpscares: a blink darkens the whole screen for a moment.
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Deadlands.id("scare_blink"), (graphics, delta) -> {
			float dark = com.deadlands.client.horror.ScarePlayer.blink();
			if (dark > 0.0F) {
				graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), net.minecraft.util.ARGB.color((int) (dark * 255.0F), 0, 0, 0));
			}
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Deadlands.id("town_card"), TownCardHud::render);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Deadlands.id("horde"), HordeHud::render);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Deadlands.id("boss_card"), com.deadlands.client.hud.BossCardHud::render);
		HudElementRegistry.replaceElement(VanillaHudElements.BOSS_BAR, vanilla -> com.deadlands.client.hud.BossBarHud::render);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Deadlands.id("mission_tracker"), com.deadlands.client.mission.MissionTrackerHud::render);
		// Cutscenes are client data (assets/<namespace>/cutscene/*.json).
		net.fabricmc.fabric.api.resource.v1.ResourceLoader.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
			.registerReloader(Deadlands.id("cutscene"), com.deadlands.client.cutscene.CutsceneLibrary.INSTANCE);
		// The pause screen's Missions tab, once the survivor has received a mission.
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof net.minecraft.client.gui.screens.PauseScreen && com.deadlands.client.mission.ClientMissions.received()) {
				net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen).add(new com.deadlands.client.ui.widget.DLButton(8, height - 28, 98, 20,
					net.minecraft.network.chat.Component.translatable("mission.deadlands.screen"),
					button -> client.setScreen(new com.deadlands.client.mission.MissionsScreen(screen))));
			}
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Deadlands.id("survival"), SurvivalHud::render);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Deadlands.id("vehicle"), com.deadlands.client.hud.VehicleHud::render);
		com.deadlands.client.hud.VitalsHud.register();
		ClientBridge.install(DeadlandsClient::openJournal);
		ClientBridge.installInfected(VOICES);
		ClientBridge.installEffects(new ClientBridge.EffectsHandler() {
			@Override
			public void recoil(float degrees) {
				net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
				if (player != null) {
					player.setXRot(Math.max(-90.0F, player.getXRot() - degrees));
					player.setYRot(player.getYRot() + (player.getRandom().nextFloat() - 0.5F) * degrees * 0.4F);
					com.deadlands.client.firstperson.FirstPersonMotion.kick(degrees);
				}
			}

			@Override
			public void reload(int ticks) {
				com.deadlands.client.firstperson.FirstPersonMotion.reload(ticks);
			}
		});
		ClientBridge.installStory(new ClientBridge.StoryHandler() {
			@Override
			public void openDocument(String loreId, java.util.List<String> args) {
				com.deadlands.client.ui.DocumentScreen.open(loreId, args);
			}

			@Override
			public void openRadio() {
				net.minecraft.client.Minecraft.getInstance().setScreen(new com.deadlands.client.ui.RadioScreen());
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			com.deadlands.client.sound.MenuMusic.tick(client);
			while (DLKeyMappings.JOURNAL.consumeClick()) {
				if (client.screen == null && client.player != null) {
					openJournal();
				}
			}
			while (DLKeyMappings.HORN.consumeClick()) {
				vehicleControl(client, com.deadlands.vehicle.VehicleEntity.ACTION_HORN);
			}
			while (DLKeyMappings.HEADLIGHTS.consumeClick()) {
				vehicleControl(client, com.deadlands.vehicle.VehicleEntity.ACTION_LIGHTS);
			}
			HordeHud.tick(client);
			com.deadlands.client.hud.BossCardHud.tick();
			com.deadlands.client.weather.ClientWeather.tick(client);
			com.deadlands.client.sound.AmbienceDirector.tick(client);
			com.deadlands.client.sound.MusicDirector.tick(client);
			com.deadlands.client.sound.MenuAmbience.tick(client);
			com.deadlands.client.sound.VehicleEngineSounds.tick(client);
			VOICES.tickAll(client);
			com.deadlands.client.horror.ScarePlayer.tick(client);
			com.deadlands.client.weather.ClientDisaster.tick(client);
			com.deadlands.client.sound.GuestMusic.tick(client);
			com.deadlands.client.sound.DotMusic.tick(client);
			com.deadlands.client.sound.AsesinoMusic.tick(client);
			com.deadlands.client.firstperson.FirstPersonMotion.tick(client);
		});
		com.deadlands.client.camera.PerspectiveLock.init();
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			TitleBackdrop.releaseTextures();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientState.clear();
			DayCardHud.reset();
			TownCardHud.reset();
			HordeHud.reset();
			com.deadlands.client.hud.BossCardHud.reset();
			com.deadlands.client.weather.ClientWeather.reset();
			com.deadlands.client.sound.AmbienceDirector.reset();
			com.deadlands.client.sound.MusicDirector.reset();
			com.deadlands.client.sound.AsesinoMusic.reset();
			com.deadlands.client.sound.GuestMusic.reset();
			com.deadlands.client.sound.DotMusic.reset();
			com.deadlands.client.sound.VehicleEngineSounds.reset();
			com.deadlands.client.horror.ScarePlayer.reset();
			com.deadlands.client.mission.ClientMissions.reset();
			SurvivalHud.reset();
			LocalSurvival.reset();
			VOICES.reset();
		});
		Deadlands.LOGGER.info("DEADLANDS client ready");
	}

	public static void openJournal() {
		Minecraft minecraft = Minecraft.getInstance();
		if (!(minecraft.screen instanceof JournalScreen)) {
			minecraft.setScreen(new JournalScreen());
		}
	}

	/** Ask the server to sound the horn or switch the lights, if the player is driving. */
	private static void vehicleControl(net.minecraft.client.Minecraft client, int action) {
		if (client.player != null && client.player.getVehicle() instanceof com.deadlands.vehicle.VehicleEntity vehicle
			&& vehicle.getControllingPassenger() == client.player
			&& net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(com.deadlands.network.VehicleControlPayload.TYPE)) {
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.deadlands.network.VehicleControlPayload(action));
		}
	}
}
