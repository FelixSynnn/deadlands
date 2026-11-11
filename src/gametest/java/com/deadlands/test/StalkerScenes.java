package com.deadlands.test;

import com.deadlands.Deadlands;
import com.deadlands.entity.InfectedEntity;
import com.deadlands.entity.StalkerEntity;
import com.deadlands.registry.DLEntities;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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

/**
 * Regression scenes for the Stalker crash: a pounce was sent as entity event 63, which the client
 * treats as the Sniffer's digging sound and casts the entity to {@code Sniffer}
 * ({@code ClassCastException: StalkerEntity cannot be cast to class_8153}). Over a real connection
 * to a real client:
 *
 * <ol>
 *   <li>every infected event is sent to standing stalkers, and each starts its animation on the client;</li>
 *   <li>a stalker hunting a survivor really pounces (its AI sends the event), with its sound;</li>
 *   <li>stalkers are spawned, sent every event and removed, five times over.</li>
 * </ol>
 * The client must stay up throughout.
 */
final class StalkerScenes {
	private static final byte[] EVENTS = {InfectedEntity.EVENT_ATTACK, InfectedEntity.EVENT_SLAM, InfectedEntity.EVENT_POUNCE,
		InfectedEntity.EVENT_SCREAM, InfectedEntity.EVENT_EMERGE};

	private StalkerScenes() {
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext world) {
		TestServerContext server = world.getServer();
		List<String> heard = new CopyOnWriteArrayList<>();
		SoundEventListener listener = (sound, events, range) -> heard.add(String.valueOf(sound.getIdentifier()));
		context.runOnClient(client -> client.getSoundManager().addListener(listener));
		try {
			// Night, normal difficulty, survival: stalkers hunt. Harley stays at her camp by the spawn.
			server.runCommand("difficulty normal");
			server.runCommand("time set 18000");
			server.runCommand("weather clear");
			server.runCommand("gamemode survival @p");
			server.runCommand("effect give @p minecraft:resistance 600 4 true");
			server.runCommand("effect give @p minecraft:regeneration 600 4 true");
			server.runCommand("effect give @p minecraft:night_vision 600 0 true");
			server.runCommand("tp @p ~120 ~ ~ 0 8");
			context.waitTicks(20);
			world.getClientWorld().waitForChunksRender();
			context.waitTicks(10);

			// 1. Standing stalkers (no AI) receive every infected event over the network.
			int[] still = server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				int[] ids = new int[4];
				for (int i = 0; i < ids.length; i++) {
					ids[i] = spawn(s.overworld(), p.getX() - 3.0 + i * 2.0, p.getY(), p.getZ() + 7.0, true).getId();
				}
				return ids;
			});
			context.waitTicks(10);
			check(seen(context, still) == still.length, "the client sees the standing stalkers");
			// As the scream goal does: the screaming flag first (the client stops a scream without it), then the events.
			server.runOnServer(s -> {
				for (int id : still) {
					if (s.overworld().getEntity(id) instanceof InfectedEntity infected) {
						infected.setFlag(InfectedEntity.FLAG_SCREAMING, true);
					}
				}
				sendEvents(s, still);
			});
			context.waitTicks(4);
			String missing = context.computeOnClient(client -> {
				StringBuilder out = new StringBuilder();
				for (int id : still) {
					if (!(client.level.getEntity(id) instanceof StalkerEntity stalker)) {
						out.append(" #").append(id).append(" gone");
						continue;
					}
					if (!stalker.attackAnimationState.isStarted()) {
						out.append(" #").append(id).append(" attack");
					}
					if (!stalker.slamAnimationState.isStarted()) {
						out.append(" #").append(id).append(" slam");
					}
					if (!stalker.pounceAnimationState.isStarted()) {
						out.append(" #").append(id).append(" pounce");
					}
					if (!stalker.screamAnimationState.isStarted()) {
						out.append(" #").append(id).append(" scream");
					}
					if (!stalker.emergeAnimationState.isStarted()) {
						out.append(" #").append(id).append(" emerge");
					}
				}
				return out.toString();
			});
			check(missing.isEmpty(), "every infected entity event reaches the client's stalkers and starts its animation, without a crash"
				+ (missing.isEmpty() ? "" : " (not started:" + missing + ")"));
			shot(context, "deadlands-30-stalkers-events");
			server.runOnServer(s -> discard(s, still));

			// 2. A stalker hunting the survivor pounces by itself.
			int hunter = server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				StalkerEntity stalker = spawn(s.overworld(), p.getX() + 0.5, p.getY(), p.getZ() + 5.0, false);
				stalker.setTarget(p);
				return stalker.getId();
			});
			boolean pounced = false;
			for (int t = 0; t < 400 && !pounced; t += 5) {
				context.waitTicks(5);
				pounced = context.computeOnClient(client -> client.level.getEntity(hunter) instanceof StalkerEntity stalker
					&& stalker.pounceAnimationState.isStarted());
			}
			check(pounced, "a stalker hunting a survivor pounces, and the client plays the pounce");
			context.waitTicks(6);
			shot(context, "deadlands-31-stalker-pounce");
			context.waitTicks(30);
			check(heard.contains(Deadlands.id("infected.stalker.pounce").toString()), "the client plays the stalker's pounce sound");
			check(context.computeOnClient(client -> client.player != null && client.level != null), "the client is still in the world after the pounce");
			server.runOnServer(s -> discard(s, new int[] {hunter}));

			// 3. Spawn, send every event, remove: five times over.
			for (int cycle = 1; cycle <= 5; cycle++) {
				int[] wave = server.computeOnServer(s -> {
					ServerPlayer p = player(s);
					int[] ids = new int[10];
					for (int i = 0; i < ids.length; i++) {
						double angle = Math.PI * 2.0 * i / ids.length;
						double radius = 8.0 + (i % 3) * 3.0;
						StalkerEntity stalker = spawn(s.overworld(), p.getX() + Math.cos(angle) * radius, p.getY(), p.getZ() + Math.sin(angle) * radius,
							false);
						stalker.setTarget(p);
						ids[i] = stalker.getId();
					}
					return ids;
				});
				context.waitTicks(10);
				int visible = seen(context, wave);
				check(visible == wave.length, "cycle " + cycle + ": the client sees all " + wave.length + " stalkers (" + visible + ")");
				server.runOnServer(s -> sendEvents(s, wave));
				context.waitTicks(25);
				server.runOnServer(s -> discard(s, wave));
				context.waitTicks(5);
				int left = seen(context, wave);
				check(left == 0, "cycle " + cycle + ": removed stalkers are gone on the client (" + left + " left)");
			}
			check(context.computeOnClient(client -> client.player != null && client.player.isAlive()), "the survivor came through the stalker scenes");
		} finally {
			context.runOnClient(client -> client.getSoundManager().removeListener(listener));
			server.runCommand("gamemode creative @p");
			server.runCommand("time set 6000");
		}
	}

	private static StalkerEntity spawn(ServerLevel level, double x, double y, double z, boolean still) {
		StalkerEntity stalker = DLEntities.STALKER.create(level, EntitySpawnReason.COMMAND);
		if (stalker == null) {
			throw new AssertionError("DEADLANDS client test failed: a stalker could not be created");
		}
		stalker.snapTo(x, y, z, 180.0F, 0.0F);
		stalker.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(x, y, z)), EntitySpawnReason.COMMAND, null);
		stalker.setNoAi(still);
		stalker.setPersistenceRequired();
		level.addFreshEntity(stalker);
		return stalker;
	}

	private static void sendEvents(MinecraftServer server, int[] ids) {
		ServerLevel level = server.overworld();
		for (int id : ids) {
			Entity entity = level.getEntity(id);
			if (entity != null) {
				for (byte event : EVENTS) {
					level.broadcastEntityEvent(entity, event);
				}
			}
		}
	}

	private static void discard(MinecraftServer server, int[] ids) {
		for (int id : ids) {
			Entity entity = server.overworld().getEntity(id);
			if (entity != null) {
				entity.discard();
			}
		}
	}

	private static int seen(ClientGameTestContext context, int[] ids) {
		return context.computeOnClient(client -> {
			int count = 0;
			for (int id : ids) {
				if (client.level.getEntity(id) instanceof StalkerEntity) {
					count++;
				}
			}
			return count;
		});
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void shot(ClientGameTestContext context, String name) {
		Path path = context.takeScreenshot(name);
		Deadlands.LOGGER.info("Screenshot: {}", path.toAbsolutePath());
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("DEADLANDS client test failed: " + what);
		}
		Deadlands.LOGGER.info("PASS: {}", what);
	}
}
