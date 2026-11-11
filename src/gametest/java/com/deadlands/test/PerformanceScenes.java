package com.deadlands.test;

import com.deadlands.Deadlands;
import com.deadlands.entity.InfectedEntity;
import com.deadlands.registry.DLEntities;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.LightLayer;

/**
 * A crowd: forty infected hunting the survivor for ten seconds, with the music director, the
 * soundscape and every voice running. Records the frame rate, the live heap and the infected's
 * sounds, and checks that the voice budget holds (a crowd must not become a wall of noise) and that
 * the game stays playable. Then F3+T in the same world.
 */
final class PerformanceScenes {
	private static final int CROWD = 40;
	/** The sounds InfectedVoices speaks (idle, alert, chase, breath, distant and muffled calls). */
	private static final Pattern VOICE = Pattern.compile("deadlands:infected\\.([a-z_]+\\.(ambient|alert|chase)|breath|distant|muffled)");

	private PerformanceScenes() {
	}

	static void crowd(ClientGameTestContext context, TestSingleplayerContext world) {
		TestServerContext server = world.getServer();
		// Every infected sound, by name (footsteps and attack swings included), and the voices on
		// their own: the voices are what InfectedVoices budgets, at most six at once.
		Map<String, Integer> heard = new ConcurrentHashMap<>();
		List<SoundInstance> voiceSounds = Collections.synchronizedList(new ArrayList<>());
		SoundEventListener listener = (sound, events, range) -> {
			String id = String.valueOf(sound.getIdentifier());
			if (id.startsWith("deadlands:infected.")) {
				heard.merge(id.substring("deadlands:".length()), 1, Integer::sum);
				if (VOICE.matcher(id).matches()) {
					voiceSounds.add(sound);
				}
			}
		};
		server.runCommand("difficulty normal");
		server.runCommand("time set 18000");
		server.runCommand("gamemode survival @p");
		server.runCommand("effect give @p minecraft:resistance 300 4 true");
		server.runCommand("effect give @p minecraft:regeneration 300 4 true");
		List<Integer> ids = server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
			ServerLevel level = s.overworld();
			List<Integer> out = new ArrayList<>();
			for (int i = 0; i < CROWD; i++) {
				double angle = Math.PI * 2.0 * i / CROWD;
				double radius = 8.0 + (i % 5) * 3.0;
				InfectedEntity walker = DLEntities.WALKER.create(level, EntitySpawnReason.COMMAND);
				walker.snapTo(p.getX() + Math.cos(angle) * radius, p.getY(), p.getZ() + Math.sin(angle) * radius, 0.0F, 0.0F);
				walker.finalizeSpawn(level, level.getCurrentDifficultyAt(p.blockPosition()), EntitySpawnReason.COMMAND, null);
				walker.setPersistenceRequired();
				level.addFreshEntity(walker);
				walker.setTarget(p);
				out.add(walker.getId());
			}
			return out;
		});
		context.waitTicks(20);
		long memoryBefore = liveHeap();
		context.runOnClient(client -> client.getSoundManager().addListener(listener));
		int frames = 0;
		int samples = 0;
		int lowest = Integer.MAX_VALUE;
		int mostVoices = 0;
		for (int t = 2; t <= 200; t += 2) {
			context.waitTicks(2);
			mostVoices = Math.max(mostVoices, context.computeOnClient(client -> {
				synchronized (voiceSounds) {
					return (int) voiceSounds.stream().filter(sound -> client.getSoundManager().isActive(sound)).count();
				}
			}));
			if (t % 20 == 0) {
				int fps = context.computeOnClient(client -> client.getFps());
				frames += fps;
				samples++;
				lowest = Math.min(lowest, fps);
			}
		}
		context.runOnClient(client -> client.getSoundManager().removeListener(listener));
		long memoryAfter = liveHeap();
		int average = frames / Math.max(1, samples);
		int voices = voiceSounds.size();
		int all = heard.values().stream().mapToInt(Integer::intValue).sum();
		String byName = heard.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
			.map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(", "));
		int seen = context.computeOnClient(client -> {
			int count = 0;
			for (int id : ids) {
				if (client.level.getEntity(id) instanceof InfectedEntity) {
					count++;
				}
			}
			return count;
		});
		Deadlands.LOGGER.info("PERFORMANCE: {} infected hunting for 10 s: {} fps on average (lowest {}); {} voices started, at most {} at once; "
			+ "{} infected sounds in all ({}); live heap {} MB -> {} MB", seen, average, lowest, voices, mostVoices, all, byName,
			memoryBefore >> 20, memoryAfter >> 20);
		check(seen >= CROWD - 2, "the crowd of " + CROWD + " infected is on the client (" + seen + ")");
		check(voices >= 3, "a hunting crowd is heard (" + voices + " voices in 10 s)");
		check(mostVoices <= 6, "forty hunting infected never speak more than six at once (at most " + mostVoices + ", " + voices + " voices in 10 s)");
		check(average >= 20, "the game stays playable with forty infected hunting (" + average + " fps on average, lowest " + lowest + ")");
		server.runOnServer(s -> {
			for (int id : ids) {
				Entity e = s.overworld().getEntity(id);
				if (e != null) {
					e.discard();
				}
			}
		});
		server.runCommand("gamemode creative @p");
		server.runCommand("time set 6000");
	}

	/**
	 * F3+T in a world: every DEADLANDS texture, model, sound and the music reload without an error,
	 * and the same view looks the same afterwards (missing textures or a stale light map would not).
	 */
	static void reload(ClientGameTestContext context, TestSingleplayerContext world) {
		TestServerContext server = world.getServer();
		// A clear view in daylight: the crowd may have pushed the survivor against a wall.
		double[] view = LookScenes.blockView();
		server.runCommand("gamemode spectator @p");
		server.runCommand("time set 6000");
		server.runCommand(String.format(java.util.Locale.ROOT, "tp @p %.2f %.2f %.2f 0 18", view[0], view[1], view[2]));
		context.runOnClient(client -> client.options.hideGui = true);
		context.waitTicks(20);
		world.getClientWorld().waitForChunksRender();
		light(context, "before the reload");
		double before = brightness(context.takeScreenshot("deadlands-46a-before-reload"));
		java.util.concurrent.CompletableFuture<Void> future = context.computeOnClient(client -> client.reloadResourcePacks());
		context.waitFor(client -> future.isDone(), 6000);
		check(future.isDone() && !future.isCompletedExceptionally(), "resources reload cleanly in a world (F3+T)");
		// The loading screen fades out with the game paused; then the chunks are rebuilt.
		context.waitFor(client -> client.getOverlay() == null, 600);
		context.waitTicks(20);
		world.getClientWorld().waitForChunksRender();
		light(context, "after the reload");
		check(context.computeOnClient(client -> client.level != null && client.player != null), "the world carries on after the reload");
		double after = brightness(context.takeScreenshot("deadlands-46-after-reload"));
		check(Math.abs(after - before) <= before * 0.15, String.format(java.util.Locale.ROOT,
			"the world looks the same after the reload (mean brightness %.1f before, %.1f after)", before, after));
		context.runOnClient(client -> client.options.hideGui = false);
		server.runCommand("gamemode creative @p");
	}

	/** Mean brightness of a screenshot, 0 to 255. */
	private static double brightness(Path png) {
		try {
			BufferedImage image = ImageIO.read(png.toFile());
			long sum = 0;
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					int rgb = image.getRGB(x, y);
					sum += ((rgb >> 16) & 255) + ((rgb >> 8) & 255) + (rgb & 255);
				}
			}
			return sum / (3.0 * image.getWidth() * image.getHeight());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** What decides how bright the world is drawn, for the log. */
	private static void light(ClientGameTestContext context, String when) {
		String state = context.computeOnClient(client -> {
			float sky = client.gameRenderer.getMainCamera().attributeProbe().getValue(EnvironmentAttributes.SKY_LIGHT_FACTOR, 1.0F);
			BlockPos head = client.player.blockPosition().above();
			return String.format(java.util.Locale.ROOT,
				"day time %d, paused %s, overlay %s, sky light factor %.3f, sky light %d, block light %d, rain %.2f, thunder %.2f",
				client.level.getDayTime(), client.isPaused(), client.getOverlay() == null ? "none" : client.getOverlay().getClass().getSimpleName(), sky,
				client.level.getBrightness(LightLayer.SKY, head), client.level.getBrightness(LightLayer.BLOCK, head), client.level.getRainLevel(1.0F),
				client.level.getThunderLevel(1.0F));
		});
		Deadlands.LOGGER.info("LIGHT {}: {}", when, state);
	}

	/** Heap in use after a collection, so garbage waiting to be collected is not counted. */
	private static long liveHeap() {
		Runtime runtime = Runtime.getRuntime();
		System.gc();
		return runtime.totalMemory() - runtime.freeMemory();
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("DEADLANDS client test failed: " + what);
		}
		Deadlands.LOGGER.info("PASS: {}", what);
	}
}
