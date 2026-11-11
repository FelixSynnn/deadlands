package com.deadlands.test;

import com.deadlands.Deadlands;
import com.deadlands.client.ClientState;
import com.deadlands.client.input.DLKeyMappings;
import com.deadlands.client.ui.DeadlandsTitleScreen;
import com.deadlands.client.ui.JournalScreen;
import com.deadlands.client.ui.SettingsScreen;
import com.deadlands.container.LootContainerBlockEntity;
import java.util.List;
import com.deadlands.persistence.DeadlandsWorldData;
import com.deadlands.registry.DLItems;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

/**
 * End-to-end client test (run with {@code gradlew runClientGameTest}). Boots the real client,
 * drives it with real input, and writes screenshots to the run directory's screenshots folder.
 */
@SuppressWarnings("UnstableApiUsage")
public class DeadlandsClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		// 1. The DEADLANDS title screen replaces vanilla's.
		context.waitForScreen(DeadlandsTitleScreen.class);
		context.waitTicks(50);
		shot(context, "deadlands-01-title");
		AudioScenes.menu(context);

		// 2. Settings screen (opened from the title screen as a player would).
		context.setScreen(() -> new SettingsScreen(Minecraft.getInstance().screen));
		context.waitTicks(5);
		shot(context, "deadlands-02-settings");
		context.setScreen(TitleScreen::new);
		context.waitForScreen(DeadlandsTitleScreen.class);

		// 3. A new singleplayer world: arrival card, journal given, record created.
		TestWorldSave save;
		long processedDay;
		MilestoneTwoScenes.Result scenes;
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(true).create()) {
			save = world.getWorldSave();
			world.getClientWorld().waitForChunksRender();
			context.waitTicks(30);
			shot(context, "deadlands-03-arrival-day-card");
			// Milestones 8 and 9: the opening introduction, then the opening cutscene; a click skips each.
			skipOpening(context, "deadlands-03c-harley-speaks");
			shot(context, "deadlands-03b-harley-camp");

			check(world.getServer().computeOnServer(server -> DeadlandsWorldData.get(server).players().size()) == 1,
				"the arriving survivor gets a world record");
			check(journalCount(context) == 1, "the arriving survivor receives exactly one Survivor's Journal");
			check(context.computeOnClient(client -> ClientState.status().isPresent()), "the client received the world status");

			// Open the journal with the real key binding, then click the THREATS tab with the real mouse.
			context.getInput().pressKey(DLKeyMappings.JOURNAL);
			context.waitForScreen(JournalScreen.class);
			context.waitTicks(10);
			shot(context, "deadlands-04-journal-log");
			clickTab(context, 1);
			context.waitTicks(5);
			check("THREATS".equals(context.computeOnClient(client -> ((JournalScreen) client.screen).currentTabName())), "clicking the tab opens THREATS");
			shot(context, "deadlands-05-journal-threats");
			context.getInput().pressKey(DLKeyMappings.JOURNAL);
			context.waitForScreen(null);

			// Advance to the next morning: the clock processes the day and announces it.
			long before = world.getServer().computeOnServer(server -> DeadlandsWorldData.get(server).lastProcessedDay());
			world.getServer().runCommand("deadlands day next");
			context.waitTicks(40);
			shot(context, "deadlands-06-new-day-card");
			processedDay = world.getServer().computeOnServer(server -> DeadlandsWorldData.get(server).lastProcessedDay());
			check(processedDay == before + 1, "the world clock processed exactly one new day (" + before + " -> " + processedDay + ")");

			// Milestone 2: materials, decals, containers (real right-clicks, a lock and its key), structures.
			scenes = MilestoneTwoScenes.run(context, world);
		}

		// 4. Reload the saved world: progress persists and the journal is not handed out again.
		try (TestSingleplayerContext world = save.open()) {
			world.getClientWorld().waitForChunksRender();
			context.waitTicks(20);
			long reloaded = world.getServer().computeOnServer(server -> DeadlandsWorldData.get(server).lastProcessedDay());
			check(reloaded == processedDay, "processed day survives save and reload (" + processedDay + " vs " + reloaded + ")");
			check(world.getServer().computeOnServer(server -> DeadlandsWorldData.get(server).chronicle().size()) >= 1,
				"the world chronicle survives save and reload");
			check(journalCount(context) == 1, "rejoining does not hand out a second journal");
			List<String> crateAfter = world.getServer().computeOnServer(s -> MilestoneTwoScenes.contents(s, scenes.crate()));
			check(crateAfter.equals(scenes.crateContents()), "container contents survive save and reload without re-rolling");
			check(world.getServer().computeOnServer(s -> s.overworld().getBlockEntity(scenes.shackCabinet()) instanceof LootContainerBlockEntity c
				&& c.lock().isLocked() && c.getLootTable() != null), "structure locks and unrolled loot survive save and reload");
			shot(context, "deadlands-07-reloaded");
		}

		// 5. A normal world (fixed seed, structures on): the ruins generate on their own.
		MilestoneTwoScenes.naturalStructures(context);

		// 5b. Stalker crash regression: entity events, a real pounce, repeated spawning (own world, so
		// its night, difficulty and game mode cannot touch the worlds above).
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(true).create()) {
			world.getClientWorld().waitForChunksRender();
			skipOpening(context, null);
			StalkerScenes.run(context, world);
		}

		// 5c. The soundtrack, the soundscape and the infected's voices in a real client.
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(true).create()) {
			world.getClientWorld().waitForChunksRender();
			skipOpening(context, null);
			AudioScenes.run(context, world);
			LookScenes.infected(context, world);
			LookScenes.weapons(context, world);
			LookScenes.blocks(context, world);
			LookScenes.embers(context, world);
			PerformanceScenes.crowd(context, world);
			PerformanceScenes.reload(context, world);
		}

		// 6. Multiplayer: a dedicated server with the mod, joined by this client.
		try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
			try (TestServerConnection connection = server.connect()) {
				connection.getClientWorld().waitForChunksRender();
				context.waitTicks(40);
				check(context.computeOnClient(client -> ClientState.status().isPresent()), "a dedicated server sends the world status");
				check(journalCount(context) == 1, "a dedicated server gives the journal on first join");
				shot(context, "deadlands-08-multiplayer");
			}
		}
		Deadlands.LOGGER.info("DEADLANDS client game test passed");
	}

	/**
	 * Click through the introduction and the opening cutscene of a new world, as a player in a hurry
	 * would, and close Harley's greeting (screenshotted as {@code dialogueShot} unless it is null).
	 */
	static void skipOpening(ClientGameTestContext context, String dialogueShot) {
		int quiet = 0;
		for (int tick = 0; tick < 600 && quiet < 60; tick += 5) {
			String screen = context.computeOnClient(client -> client.screen == null ? "" : client.screen.getClass().getSimpleName());
			if (screen.equals("DialogueScreen")) {
				// Harley speaks once the opening is over (Milestone 8): look, then close it.
				if (dialogueShot != null) {
					shot(context, dialogueShot);
				}
				context.setScreen(() -> null);
				quiet = 0;
			} else if (screen.equals("IntroScreen") || screen.equals("CutsceneScreen")) {
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
				quiet = 0;
			} else {
				quiet += 5; // the cutscene starts a moment after the introduction ends
			}
			context.waitTicks(5);
		}
		String left = context.computeOnClient(client -> client.screen == null ? "" : client.screen.getClass().getName());
		if (!left.isEmpty()) {
			shot(context, "deadlands-03x-opening-stuck");
		}
		check(left.isEmpty(), "the opening can be skipped back to the game (still open: " + left + ")");
	}

	private static void shot(ClientGameTestContext context, String name) {
		Path path = context.takeScreenshot(name);
		Deadlands.LOGGER.info("Screenshot: {}", path.toAbsolutePath());
	}

	private static int journalCount(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			Inventory inventory = client.player.getInventory();
			int count = 0;
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				if (inventory.getItem(i).is(DLItems.SURVIVOR_JOURNAL)) {
					count += inventory.getItem(i).getCount();
				}
			}
			return count;
		});
	}

	private static void clickTab(ClientGameTestContext context, int index) {
		double[] pos = context.computeOnClient(client -> {
			float[] gui = ((JournalScreen) client.screen).tabCenter(index);
			double scale = client.getWindow().getGuiScale();
			return new double[] {gui[0] * scale, gui[1] * scale};
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.waitTick();
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("DEADLANDS client test failed: " + what);
		}
		Deadlands.LOGGER.info("PASS: {}", what);
	}
}
