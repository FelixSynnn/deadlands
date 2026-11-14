package com.deadlands.world.disaster;

import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import com.deadlands.cutscene.Cutscenes;
import com.deadlands.entity.MythosEntity;
import com.deadlands.registry.DLMythos;
import com.deadlands.registry.DLParticles;
import com.deadlands.registry.DLSounds;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Mythos' tornado: a storm of static far bigger than any twister or the Green Tornado, that brings him down where it
 * touches the ground.
 *
 * <p><b>When</b> (tracked per world, saved): night 1 is the night of day 1 and is never one. On night
 * {@code mythos.guaranteedNight} (2: the night after day 1) it is certain; on every night after that, a
 * {@code mythos.nightlyChance} (15%) roll at nightfall. A night missed while the server was down still gets its
 * guarantee the next night. It comes one to four minutes after nightfall, for a survivor in the overworld.
 *
 * <p><b>How</b>: a warning (20 s: a message, a low cue, the sky filling with static), then the funnel forms 30 to 45
 * blocks from them and grows (10 s), then touches down: Mythos comes out of it (the arrival cutscene for those near)
 * and the funnel keeps turning slowly around where he landed, pulling and lifting, tearing up loose blocks (glass,
 * leaves, plants, wool, fences, panes...) where mobGriefing and {@code mythos.tornadoDestroys} allow. When the fight is
 * over (he is killed, or nobody has been within {@code mythos.escapeDistance} blocks for 30 s, and then he leaves too)
 * it breaks up over 6 seconds.
 */
public final class MythosTornado {
	private static final int TICK_EVERY = 2;
	private static final int WARNING = 400;
	private static final int FORMING = 200;
	private static final int DISSIPATE = 120;
	private static final DustParticleOptions MAGENTA = new DustParticleOptions(0xFF4FD6, 3.0F);
	private static final DustParticleOptions VIOLET = new DustParticleOptions(0x5A1AA8, 3.5F);
	private static final DustParticleOptions BLACK = new DustParticleOptions(0x0D0A12, 4.0F);

	/** The saved state: which night was last decided, whether the guarantee came, and the storm in progress. */
	public static final class State extends SavedData {
		public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.LONG.optionalFieldOf("last_night", 0L).forGetter(s -> s.lastNight),
			Codec.BOOL.optionalFieldOf("guaranteed_done", false).forGetter(s -> s.guaranteedDone),
			Codec.INT.optionalFieldOf("stage", 0).forGetter(s -> s.stage),
			Codec.LONG.optionalFieldOf("stage_at", 0L).forGetter(s -> s.stageAt),
			Codec.DOUBLE.optionalFieldOf("x", 0.0).forGetter(s -> s.x),
			Codec.DOUBLE.optionalFieldOf("z", 0.0).forGetter(s -> s.z),
			Codec.DOUBLE.optionalFieldOf("home_x", 0.0).forGetter(s -> s.homeX),
			Codec.DOUBLE.optionalFieldOf("home_z", 0.0).forGetter(s -> s.homeZ),
			Codec.STRING.optionalFieldOf("mythos", "").forGetter(s -> s.mythos),
			Codec.STRING.optionalFieldOf("player", "").forGetter(s -> s.player),
			Codec.INT.optionalFieldOf("away", 0).forGetter(s -> s.away)
		).apply(instance, State::new));
		public static final SavedDataType<State> TYPE = new SavedDataType<>("deadlands_mythos", State::new, CODEC, null);

		long lastNight;
		boolean guaranteedDone;
		/** 0 none, 1 scheduled, 2 warning, 3 forming, 4 down (the fight), 5 breaking up. */
		int stage;
		/** When the current stage began (scheduled: when the warning starts). */
		long stageAt;
		double x;
		double z;
		double homeX;
		double homeZ;
		String mythos = "";
		String player = "";
		/** Ticks nobody has been near during the fight. */
		int away;

		public State() {
		}

		private State(long lastNight, boolean guaranteedDone, int stage, long stageAt, double x, double z, double homeX, double homeZ, String mythos,
			String player, int away) {
			this.lastNight = lastNight;
			this.guaranteedDone = guaranteedDone;
			this.stage = stage;
			this.stageAt = stageAt;
			this.x = x;
			this.z = z;
			this.homeX = homeX;
			this.homeZ = homeZ;
			this.mythos = mythos;
			this.player = player;
			this.away = away;
		}
	}

	private MythosTornado() {
	}

	public static State state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(State.TYPE);
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % TICK_EVERY == 0) {
				tick(server);
			}
		});
	}

	/** Night number for a day time: night 1 falls on day 1 (day time 13000 to 23000 of the first day). */
	static long night(long dayTime) {
		return Math.floorDiv(dayTime, 24000L) + 1L;
	}

	/** Whether a storm comes on this night: never on night 1, certain on the guaranteed night (or the first after it if that was missed), a roll after. */
	static boolean comes(long night, boolean guaranteedDone, int guaranteedNight, double chance, RandomSource random) {
		if (night < 2 || night < guaranteedNight) {
			return false;
		}
		if (!guaranteedDone) {
			return true;
		}
		return random.nextDouble() < chance;
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		DeadlandsConfig.Mythos config = ConfigManager.get().mythos;
		State state = state(server);
		long now = level.getGameTime();
		if (!config.enabled || level.getDifficulty() == Difficulty.PEACEFUL) {
			if (state.stage > 0 && state.stage < 5) {
				begin(state, 5, now); // switched off mid-storm: it breaks up
			}
			if (state.stage != 5) {
				return;
			}
		}
		// Nightfall: decide this night.
		long dayTime = level.getDayTime();
		long time = Math.floorMod(dayTime, 24000L);
		long night = night(dayTime);
		if (time >= 13000L && time < 23000L && night > state.lastNight && state.stage == 0) {
			state.lastNight = night;
			state.setDirty();
			if (config.enabled && comes(night, state.guaranteedDone, config.guaranteedNight, config.nightlyChance, level.getRandom())) {
				begin(state, 1, now + 1200 + level.getRandom().nextInt(3600));
			}
		}
		switch (state.stage) {
			case 1 -> {
				if (time < 13000L || time >= 22500L) {
					begin(state, 0, now); // dawn came first (nobody was out): it doesn't come by day; a guarantee carries to the next night
					return;
				}
				if (now >= state.stageAt) {
					ServerPlayer target = pick(level);
					if (target == null) {
						return; // nobody out: wait for someone
					}
					double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
					double distance = 30.0 + level.getRandom().nextDouble() * 15.0;
					state.x = state.homeX = target.getX() + Math.cos(angle) * distance;
					state.z = state.homeZ = target.getZ() + Math.sin(angle) * distance;
					state.player = target.getStringUUID();
					begin(state, 2, now);
					warn(level, state);
				}
			}
			case 2 -> {
				sky(level, state, (now - state.stageAt) / (float) WARNING);
				if (now - state.stageAt >= WARNING) {
					begin(state, 3, now);
				}
			}
			case 3 -> {
				float grown = (now - state.stageAt) / (float) FORMING;
				funnel(level, state, Mth.clamp(grown, 0.05F, 1.0F), false, 0.3F);
				if (now - state.stageAt >= FORMING) {
					touchDown(level, state);
					begin(state, 4, now);
				}
			}
			case 4 -> {
				// Drift slowly around where he came down.
				double a = (now - state.stageAt) * 0.004;
				state.x = state.homeX + Math.cos(a) * 9.0;
				state.z = state.homeZ + Math.sin(a * 1.3) * 9.0;
				state.setDirty();
				boolean calm = now - state.stageAt < 120; // the arrival cutscene: no pull
				funnel(level, state, 1.0F, !calm, calm ? 0.0F : 1.0F);
				fight(level, state, config);
			}
			case 5 -> {
				float left = 1.0F - (now - state.stageAt) / (float) DISSIPATE;
				if (left <= 0.0F) {
					begin(state, 0, now);
					state.mythos = "";
					state.player = "";
					state.away = 0;
				} else {
					funnel(level, state, left, false, 0.0F);
				}
			}
			default -> {
			}
		}
	}

	private static void begin(State state, int stage, long at) {
		state.stage = stage;
		state.stageAt = at;
		state.setDirty();
	}

	private static @Nullable ServerPlayer pick(ServerLevel level) {
		List<ServerPlayer> out = level.players().stream().filter(p -> !p.isSpectator() && !p.isCreative() && p.isAlive()).toList();
		return out.isEmpty() ? null : out.get(level.getRandom().nextInt(out.size()));
	}

	private static void tell(ServerLevel level, State state, String key, ChatFormatting colour, double radius) {
		Component line = Component.translatable(key).withStyle(colour);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(state.x, player.getY(), state.z) < radius * radius) {
				player.sendSystemMessage(line);
				player.connection.send(new ClientboundSetActionBarTextPacket(line));
			}
		}
	}

	private static void warn(ServerLevel level, State state) {
		tell(level, state, "boss.deadlands.mythos.warning", ChatFormatting.LIGHT_PURPLE, 256.0);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(state.x, player.getY(), state.z) < 256.0 * 256.0) {
				player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(DLSounds.MYTHOS_TORNADO_WARNING, SoundSource.WEATHER,
					player.getX(), player.getY(), player.getZ(), 1.6F, 1.0F, level.getRandom().nextLong()));
			}
		}
	}

	/** The warning: the sky over the place fills with static and dark cloud. */
	private static void sky(ServerLevel level, State state, float progress) {
		RandomSource random = level.getRandom();
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(state.x), Mth.floor(state.z));
		for (ServerPlayer player : near(level, state, 200.0)) {
			for (int i = 0; i < 6 + Math.round(progress * 14); i++) {
				double r = random.nextDouble() * 30.0;
				double a = random.nextDouble() * Math.PI * 2.0;
				level.sendParticles(player, i % 2 == 0 ? BLACK : VIOLET, true, true, state.x + Math.cos(a) * r, ground + 60 + random.nextDouble() * 10,
					state.z + Math.sin(a) * r, 1, 1.0, 0.5, 1.0, 0.0);
			}
			level.sendParticles(player, DLParticles.GLITCH, true, true, state.x, ground + 62, state.z, 4, 14.0, 3.0, 14.0, 0.02);
		}
	}

	/**
	 * The funnel: up to 80 blocks high, widening to about 30 across at the top (a twister is 34 high and about 20), black
	 * and violet cloud with magenta static and glitch running up it. With {@code destructive}, it pulls, lifts and tears.
	 */
	private static void funnel(ServerLevel level, State state, float size, boolean destructive, float pull) {
		RandomSource random = level.getRandom();
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(state.x), Mth.floor(state.z));
		if (!level.isLoaded(BlockPos.containing(state.x, ground, state.z))) {
			return;
		}
		double height = 80.0 * size;
		double lowest = 80.0 - height; // forming: it comes down from the cloud
		for (ServerPlayer player : near(level, state, 240.0)) {
			for (int i = 0; i < 40; i++) {
				double h = lowest + random.nextDouble() * height;
				double r = (1.5 + h * 0.19) * Math.max(0.3, size);
				double a = random.nextDouble() * Math.PI * 2.0 + h * 0.15;
				var particle = i % 5 == 0 ? MAGENTA : i % 3 == 0 ? VIOLET : BLACK;
				level.sendParticles(player, particle, true, true, state.x + Math.cos(a) * r, ground + h, state.z + Math.sin(a) * r, 1,
					-Math.sin(a) * 0.3, 0.05, Math.cos(a) * 0.3, 0.02);
			}
			level.sendParticles(player, DLParticles.GLITCH, true, true, state.x, ground + lowest + height * 0.5, state.z, 10, 5.0, height * 0.4, 5.0,
				0.05);
			if (lowest < 1.0) {
				level.sendParticles(player, ParticleTypes.CAMPFIRE_COSY_SMOKE, true, true, state.x, ground + 0.5, state.z, 6, 4.0, 0.3, 4.0, 0.06);
			}
		}
		if (pull <= 0.0F) {
			return;
		}
		Vec3 eye = new Vec3(state.x, ground, state.z);
		double reach = 26.0;
		for (Entity entity : level.getEntities((Entity) null, new AABB(eye, eye).inflate(reach, 40.0, reach),
			e -> e instanceof LivingEntity && e.isAlive() && !(e instanceof MythosEntity) && !(e instanceof com.deadlands.entity.SubAgentEntity))) {
			if (entity instanceof ServerPlayer player && (player.isCreative() || player.isSpectator())) {
				continue;
			}
			Vec3 to = eye.subtract(entity.position());
			double flat = Math.sqrt(to.x * to.x + to.z * to.z);
			double strength = (1.0 - flat / reach) * 0.08 * pull;
			if (strength <= 0.0) {
				continue;
			}
			Vec3 in = new Vec3(to.x, 0.0, to.z).normalize();
			entity.push(in.x * strength - in.z * strength * 1.3, flat < 6.0 ? 0.08 : 0.0, in.z * strength + in.x * strength * 1.3);
			entity.hurtMarked = true;
			if (flat < 4.0 && random.nextInt(10) == 0) {
				entity.hurtServer(level, level.damageSources().generic(), 1.5F);
			}
		}
		if (destructive && ConfigManager.get().mythos.tornadoDestroys && level.getGameRules().get(GameRules.MOB_GRIEFING) && random.nextInt(2) == 0) {
			BlockPos at = BlockPos.containing(state.x + random.nextGaussian() * 4.0, ground + random.nextInt(8) - 2, state.z + random.nextGaussian() * 4.0);
			BlockState block = level.getBlockState(at);
			if (loose(level, at, block)) {
				level.destroyBlock(at, true);
			}
		}
	}

	/** What the storm tears up: light, loose things only; never containers, never anything killer-proof. */
	private static boolean loose(ServerLevel level, BlockPos at, BlockState block) {
		if (block.isAir() || block.hasBlockEntity() || block.is(com.deadlands.registry.DLTags.KILLER_PROOF)) {
			return false;
		}
		float hardness = block.getDestroySpeed(level, at);
		return hardness >= 0.0F && (hardness < 0.8F || block.is(BlockTags.LEAVES) || block.is(BlockTags.WOOL) || block.is(BlockTags.FENCES)
			|| block.is(BlockTags.IMPERMEABLE));
	}

	private static List<ServerPlayer> near(ServerLevel level, State state, double radius) {
		return level.players().stream().filter(p -> p.distanceToSqr(state.x, p.getY(), state.z) < radius * radius).toList();
	}

	/** It touches down: Mythos comes out of it. */
	private static void touchDown(ServerLevel level, State state) {
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(state.x), Mth.floor(state.z));
		BlockPos probe = BlockPos.containing(state.x, ground, state.z);
		BlockPos at = com.deadlands.entity.ai.Stalking.standable(level, probe, 5);
		if (at == null) {
			at = probe;
		}
		MythosEntity mythos = DLMythos.MYTHOS.create(level, EntitySpawnReason.EVENT);
		if (mythos == null) {
			return;
		}
		ServerPlayer facing = level.getServer().getPlayerList().getPlayer(parse(state.player));
		float yaw = facing == null ? 0.0F
			: (float) (Mth.atan2(facing.getZ() - (at.getZ() + 0.5), facing.getX() - (at.getX() + 0.5)) * Mth.RAD_TO_DEG) - 90.0F;
		mythos.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
		mythos.arrive();
		level.addFreshEntity(mythos);
		state.mythos = mythos.getStringUUID();
		state.guaranteedDone = true; // the guarantee is spent only once he has actually come
		state.away = 0;
		state.setDirty();
		level.sendParticles(DLParticles.GLITCH, mythos.getX(), mythos.getY() + 1.0, mythos.getZ(), 120, 2.0, 1.0, 2.0, 0.2);
		tell(level, state, "boss.deadlands.mythos.arrives", ChatFormatting.DARK_PURPLE, 160.0);
		if (ConfigManager.get().mythos.cutscenes) {
			for (ServerPlayer player : near(level, state, 64.0)) {
				Cutscenes.replay(player, "mythos_arrival", mythos.position(), yaw);
			}
		}
	}

	/** During the fight: has he gone (killed or unloaded), or has everyone got away? */
	private static void fight(ServerLevel level, State state, DeadlandsConfig.Mythos config) {
		UUID id = parse(state.mythos);
		Entity mythos = id == null ? null : level.getEntity(id);
		// Escape is measured from him while he's there (a fight can drift away from the funnel), else from the funnel.
		double cx = mythos != null ? mythos.getX() : state.x;
		double cz = mythos != null ? mythos.getZ() : state.z;
		double r2 = (double) config.escapeDistance * config.escapeDistance;
		boolean someoneNear = level.players().stream().anyMatch(p -> !p.isSpectator() && !p.isCreative() && p.isAlive()
			&& p.distanceToSqr(cx, p.getY(), cz) < r2);
		state.away = someoneNear ? 0 : state.away + TICK_EVERY;
		state.setDirty();
		if (state.away >= 600) {
			if (mythos instanceof MythosEntity m && m.isAlive()) {
				m.leave(level);
			}
			tell(level, state, "boss.deadlands.mythos.escaped", ChatFormatting.GRAY, 400.0);
			begin(state, 5, level.getGameTime());
		} else if (mythos == null && someoneNear && level.getGameTime() - state.stageAt > 200) {
			begin(state, 5, level.getGameTime()); // he's gone (an unloaded or removed body): the storm goes too
		}
	}

	/** Called when Mythos dies ({@code defeated}) or leaves. */
	public static void mythosGone(ServerLevel level, boolean defeated) {
		State state = state(level.getServer());
		if (state.stage == 4) {
			if (defeated) {
				tell(level, state, "boss.deadlands.mythos.defeated", ChatFormatting.LIGHT_PURPLE, 200.0);
			}
			begin(state, 5, level.getGameTime());
		}
	}

	private static @Nullable UUID parse(String s) {
		try {
			return s == null || s.isEmpty() ? null : UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
