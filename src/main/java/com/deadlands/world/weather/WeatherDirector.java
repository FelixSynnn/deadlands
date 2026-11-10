package com.deadlands.world.weather;

import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import com.deadlands.network.WeatherPayload;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * DEADLANDS weather on top of Minecraft's rain, thunder and snow ({@code deadlands_weather}).
 *
 * <ul>
 *   <li><b>Fog</b> rolls in, mostly around dawn, thinning what can be seen to a few dozen blocks.</li>
 *   <li><b>Strong wind</b> comes with storms or on its own: louder wind, drifting debris.</li>
 *   <li><b>Dust storms</b> in dry country (the client shows dust only where the land is dry, and
 *       wind elsewhere): brown haze and blowing grit.</li>
 * </ul>
 *
 * Every half day the server decides what comes next, weighted by {@code weather.fogFrequency},
 * {@code weather.stormFrequency} and {@code weather.dustStorms}; the state eases in and out over a
 * minute and is sent to every survivor. Storm frequency also scales how often Minecraft's own rain and
 * thunder start (0 keeps the sky clear). It changes how far you can see, not how the infected hunt.
 */
public final class WeatherDirector {
	public enum Kind {
		CLEAR, FOG, WIND, DUST;

		public static Kind byIndex(int index) {
			Kind[] kinds = values();
			return kinds[Math.floorMod(index, kinds.length)];
		}
	}

	private static final int DECIDE_TICKS = 12000;
	private static final int SYNC_TICKS = 100;

	/** The saved state. */
	public static final class State extends SavedData {
		public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("kind", 0).forGetter(s -> s.kind.ordinal()),
			Codec.LONG.optionalFieldOf("until", 0L).forGetter(s -> s.until),
			Codec.FLOAT.optionalFieldOf("strength", 0.0F).forGetter(s -> s.strength)
		).apply(instance, (kind, until, strength) -> new State(Kind.byIndex(kind), until, strength)));
		public static final SavedDataType<State> TYPE = new SavedDataType<>("deadlands_weather", State::new, CODEC, null);

		Kind kind = Kind.CLEAR;
		long until;
		float strength;

		public State() {
		}

		private State(Kind kind, long until, float strength) {
			this.kind = kind;
			this.until = until;
			this.strength = strength;
		}

		public Kind kind() {
			return kind;
		}

		public float strength() {
			return strength;
		}
	}

	private WeatherDirector() {
	}

	public static State state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(State.TYPE);
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 11) {
				tick(server);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> send(server, handler.getPlayer())));
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		State state = state(server);
		long now = level.getGameTime();
		if (now >= state.until) {
			decide(level, state, now);
			state.setDirty();
			broadcast(server);
		} else if (server.getTickCount() % SYNC_TICKS == 11) {
			broadcast(server);
		}
	}

	private static void decide(ServerLevel level, State state, long now) {
		DeadlandsConfig.Weather config = ConfigManager.get().weather;
		RandomSource random = level.getRandom();
		long tod = Math.floorMod(level.getDayTime(), 24000L);
		boolean morning = tod > 22000L || tod < 2000L;
		double fog = 0.18 * config.fogFrequency * (morning ? 2.0 : 1.0);
		double wind = 0.14 * config.stormFrequency * (level.isRaining() ? 2.5 : 1.0);
		double dust = config.dustStorms ? 0.08 * config.stormFrequency : 0.0;
		double roll = random.nextDouble();
		if (roll < fog) {
			state.kind = Kind.FOG;
		} else if (roll < fog + wind) {
			state.kind = Kind.WIND;
		} else if (roll < fog + wind + dust) {
			state.kind = Kind.DUST;
		} else {
			state.kind = Kind.CLEAR;
		}
		state.strength = state.kind == Kind.CLEAR ? 0.0F : 0.5F + random.nextFloat() * 0.5F;
		state.until = now + DECIDE_TICKS / 2 + random.nextInt(DECIDE_TICKS);
		// Storm frequency 0 keeps the sky clear; above 1, rain starts sooner.
		if (config.stormFrequency <= 0.0) {
			level.setWeatherParameters(DECIDE_TICKS * 2, 0, false, false);
		} else if (!level.isRaining() && random.nextDouble() < 0.1 * (config.stormFrequency - 1.0)) {
			level.setWeatherParameters(0, 6000 + random.nextInt(6000), true, random.nextDouble() < 0.3);
		}
	}

	public static void broadcast(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			send(server, player);
		}
	}

	private static void send(MinecraftServer server, ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, WeatherPayload.TYPE)) {
			State state = state(server);
			ServerPlayNetworking.send(player, new WeatherPayload(state.kind.ordinal(), state.strength));
		}
	}

	/** Set the weather by command: a kind for a while. */
	public static void force(MinecraftServer server, Kind kind, int ticks) {
		State state = state(server);
		state.kind = kind;
		state.strength = kind == Kind.CLEAR ? 0.0F : 1.0F;
		state.until = server.overworld().getGameTime() + Math.max(200, ticks);
		state.setDirty();
		broadcast(server);
	}
}
