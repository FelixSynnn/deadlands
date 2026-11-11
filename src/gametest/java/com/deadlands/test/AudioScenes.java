package com.deadlands.test;

import com.deadlands.Deadlands;
import com.deadlands.client.sound.MenuAmbience;
import com.deadlands.client.sound.MusicDirector;
import com.deadlands.config.ConfigManager;
import com.deadlands.entity.InfectedEntity;
import com.deadlands.npc.Faction;
import com.deadlands.npc.NpcEntity;
import com.deadlands.npc.NpcRole;
import com.deadlands.npc.NpcSpawns;
import com.deadlands.registry.DLEntities;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The atmosphere overhaul's sound, checked in a real client with a real sound engine (every sound
 * the client starts is recorded by a listener, so this works with the volume down too):
 *
 * <ol>
 *   <li>the menu plays its wind-and-crows bed;</li>
 *   <li>in a world the DEADLANDS music director takes the music over and wants exploration by day;</li>
 *   <li>ambient one-shots play around the survivor in the open;</li>
 *   <li>rain on a metal roof starts the metal rain loop, on a wooden roof the roof rain loop;</li>
 *   <li>infected hunting the survivor turn the music to a danger track and cry out (alert) and snarl
 *   (chase); once they are gone the danger music lets go;</li>
 *   <li>friendly company calls for safe-zone music, and after dark the music wants night.</li>
 * </ol>
 */
final class AudioScenes {
	private AudioScenes() {
	}

	static void menu(ClientGameTestContext context) {
		context.waitTicks(30);
		check(context.computeOnClient(client -> MenuAmbience.playing()), "the main menu plays its wind-and-crows bed");
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext world) {
		TestServerContext server = world.getServer();
		List<String> heard = new CopyOnWriteArrayList<>();
		SoundEventListener listener = (sound, events, range) -> heard.add(String.valueOf(sound.getIdentifier()));
		context.runOnClient(client -> client.getSoundManager().addListener(listener));
		double density = context.computeOnClient(client -> ConfigManager.get().audio.ambienceDensity);
		try {
			server.runCommand("difficulty normal");
			server.runCommand("time set 1000");
			server.runCommand("weather clear");
			server.runCommand("gamemode survival @p");
			server.runCommand("effect give @p minecraft:resistance 900 4 true");
			server.runCommand("effect give @p minecraft:regeneration 900 4 true");
			server.runCommand("tp @p ~-120 ~ ~ 0 0");
			context.waitTicks(120);

			// 1. The director has the music; by day, in the open, it wants exploration.
			check(context.computeOnClient(client -> MusicDirector.ownsMusic()), "the DEADLANDS music director owns the in-game music");
			check(context.computeOnClient(client -> MusicDirector.wanted()) == MusicDirector.Mood.EXPLORE,
				"by day in the open the music wants exploration (" + context.computeOnClient(client -> MusicDirector.wanted()) + ")");

			// 2. Ambient one-shots around the survivor (twice as often as usual, to keep the test short).
			context.runOnClient(client -> ConfigManager.get().audio.ambienceDensity = 2.0);
			heard.clear();
			waitFor(context, 1400, () -> oneShots(heard).size() >= 2);
			check(oneShots(heard).size() >= 2, "ambient sounds play around the survivor in the open " + oneShots(heard));

			// 3. Rain on a roof: metal rings, wood drums.
			roof(server, Blocks.IRON_BLOCK);
			server.runCommand("weather rain");
			heard.clear();
			check(waitFor(context, 300, () -> heard.contains("deadlands:ambient.rain_metal_loop")), "rain on a metal roof rings (metal rain loop)");
			roof(server, Blocks.OAK_PLANKS);
			check(waitFor(context, 400, () -> heard.contains("deadlands:ambient.rain_roof_loop")), "rain on a wooden roof drums (roof rain loop)");
			roof(server, Blocks.AIR);
			server.runCommand("weather clear");

			// 4. Danger: three walkers hunting the survivor.
			heard.clear();
			int[] walkers = server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				int[] ids = new int[3];
				for (int i = 0; i < ids.length; i++) {
					InfectedEntity walker = DLEntities.WALKER.create(s.overworld(), EntitySpawnReason.COMMAND);
					walker.snapTo(p.getX() - 3.0 + i * 3.0, p.getY(), p.getZ() + 7.0, 180.0F, 0.0F);
					walker.finalizeSpawn(s.overworld(), s.overworld().getCurrentDifficultyAt(p.blockPosition()), EntitySpawnReason.COMMAND, null);
					walker.setPersistenceRequired();
					s.overworld().addFreshEntity(walker);
					walker.setTarget(p);
					ids[i] = walker.getId();
				}
				return ids;
			});
			check(waitFor(context, 400, () -> context.computeOnClient(client -> MusicDirector.playing()) == MusicDirector.Mood.DANGER),
				"hunting infected nearby turn the music to danger (threat " + context.computeOnClient(client -> MusicDirector.threat()) + ")");
			String track = context.computeOnClient(client -> MusicDirector.currentTrack());
			check(track.equals(Deadlands.id("music.pursuit").toString()) || track.equals(Deadlands.id("music.they_hear_you").toString()),
				"the danger music is a danger track (" + track + ")");
			check(waitFor(context, 60, () -> heard.contains(track)), "the danger track reaches the sound engine");
			check(heard.contains("deadlands:infected.walker.alert"), "an infected that takes a target cries out (alert voice)");
			check(waitFor(context, 300, () -> heard.contains("deadlands:infected.walker.chase")), "infected chasing the survivor snarl (chase voice)");

			// 5. Calm: they are gone, and after its calm delay the danger music lets go.
			server.runOnServer(s -> discard(s, walkers));
			check(waitFor(context, 900, () -> context.computeOnClient(client -> MusicDirector.playing()) != MusicDirector.Mood.DANGER),
				"with the infected gone, the danger music fades out");

			// 6. Moods: friendly company is a safe zone; night is night.
			int[] settlers = server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				int[] ids = new int[2];
				for (int i = 0; i < ids.length; i++) {
					NpcEntity npc = NpcSpawns.create(s.overworld(), NpcRole.SETTLER, Faction.SETTLERS, null, p.blockPosition().offset(3 + i, 0, 3), 0.0F);
					ids[i] = npc == null ? -1 : npc.getId();
				}
				return ids;
			});
			check(waitFor(context, 80, () -> context.computeOnClient(client -> MusicDirector.wanted()) == MusicDirector.Mood.SAFE),
				"two settlers close by call for safe-zone music (" + context.computeOnClient(client -> MusicDirector.wanted()) + ")");
			server.runOnServer(s -> discard(s, settlers));
			server.runCommand("time set 18000");
			check(waitFor(context, 80, () -> context.computeOnClient(client -> MusicDirector.wanted()) == MusicDirector.Mood.NIGHT),
				"after dark the music wants night (" + context.computeOnClient(client -> MusicDirector.wanted()) + ")");
		} finally {
			context.runOnClient(client -> {
				ConfigManager.get().audio.ambienceDensity = density;
				client.getSoundManager().removeListener(listener);
			});
			server.runCommand("weather clear");
			server.runCommand("gamemode creative @p");
			server.runCommand("time set 6000");
		}
	}

	private static Set<String> oneShots(List<String> heard) {
		Set<String> out = new TreeSet<>();
		for (String id : heard) {
			if (id.startsWith("deadlands:ambient.") && !id.endsWith("_loop")) {
				out.add(id);
			}
		}
		return out;
	}

	/** A 5x5 roof two blocks above the survivor's head (AIR removes it). */
	private static void roof(TestServerContext server, Block block) {
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			BlockPos above = player(s).blockPosition().above(4);
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					level.setBlockAndUpdate(above.offset(dx, 0, dz), block.defaultBlockState());
				}
			}
		});
	}

	private static boolean waitFor(ClientGameTestContext context, int ticks, BooleanSupplier condition) {
		for (int t = 0; t < ticks; t += 5) {
			if (condition.getAsBoolean()) {
				return true;
			}
			context.waitTicks(5);
		}
		return condition.getAsBoolean();
	}

	private static void discard(MinecraftServer server, int[] ids) {
		for (int id : ids) {
			Entity entity = id < 0 ? null : server.overworld().getEntity(id);
			if (entity != null) {
				entity.discard();
			}
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("DEADLANDS client test failed: " + what);
		}
		Deadlands.LOGGER.info("PASS: {}", what);
	}
}
