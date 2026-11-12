package com.deadlands.world.disaster;

import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import com.deadlands.network.DisasterPayload;
import com.deadlands.registry.DLSounds;
import com.deadlands.world.weather.WeatherDirector;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Runs the disasters ({@link Disaster}, {@code deadlands_disasters}). On average every
 * {@code disasters.averageDaysBetween} days (never before day {@code disasters.firstDay}), one is picked
 * by weight from those switched on and suited to where the survivors are (no whiteout in warm country,
 * no dust wall in cold or wet country). A warning comes first (a sound and a line on screen and in chat),
 * then the disaster itself, then the all clear. Only one at a time, in the overworld.
 *
 * <p>World-changing effects need {@code disasters.destructive} (off by default): the twister tearing up
 * leaves, glass and plants; flood water in low ground (cleared again when the flood ends); the tremor
 * shaking loose gravel, sand and cobblestone; burning, block-breaking impacts from the sky; snow
 * building up in the whiteout; caustic rain burning grass to dirt; lightning that starts fires. Without
 * it the same disasters hurt, slow, drag and blind, but leave the world as it was.
 */
public final class DisasterDirector {
	private static final int TICK_EVERY = 5;

	/** The saved state. */
	public static final class State extends SavedData {
		public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.LONG.optionalFieldOf("next_at", -1L).forGetter(s -> s.nextAt),
			Codec.INT.optionalFieldOf("disaster", -1).forGetter(s -> s.disaster),
			Codec.INT.optionalFieldOf("phase", 0).forGetter(s -> s.phase),
			Codec.LONG.optionalFieldOf("phase_until", 0L).forGetter(s -> s.phaseUntil),
			Codec.LONG.optionalFieldOf("started", 0L).forGetter(s -> s.started),
			Codec.DOUBLE.optionalFieldOf("x", 0.0).forGetter(s -> s.x),
			Codec.DOUBLE.optionalFieldOf("z", 0.0).forGetter(s -> s.z),
			Codec.DOUBLE.optionalFieldOf("heading", 0.0).forGetter(s -> s.heading)
		).apply(instance, State::new));
		public static final SavedDataType<State> TYPE = new SavedDataType<>("deadlands_disasters", State::new, CODEC, null);

		long nextAt = -1L;
		int disaster = -1;
		/** 0 none, 1 warning, 2 under way. */
		int phase;
		long phaseUntil;
		long started;
		/** The twister's position and heading. */
		double x;
		double z;
		double heading;

		public State() {
		}

		private State(long nextAt, int disaster, int phase, long phaseUntil, long started, double x, double z, double heading) {
			this.nextAt = nextAt;
			this.disaster = disaster;
			this.phase = phase;
			this.phaseUntil = phaseUntil;
			this.started = started;
			this.x = x;
			this.z = z;
			this.heading = heading;
		}

		public @Nullable Disaster current() {
			return phase == 0 ? null : Disaster.byIndex(disaster);
		}

		public boolean warning() {
			return phase == 1;
		}
	}

	/** Flood water placed this session (destructive floods), taken away when the flood ends. */
	private static final Set<BlockPos> FLOOD_WATER = new HashSet<>();

	private DisasterDirector() {
	}

	public static State state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(State.TYPE);
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % TICK_EVERY == 3) {
				tick(server);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> send(server, handler.getPlayer())));
	}

	// ------------------------------------------------------------------------------ scheduling

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		State state = state(server);
		DeadlandsConfig.Disasters config = ConfigManager.get().disasters;
		long now = level.getGameTime();
		List<ServerPlayer> players = players(level);
		if (state.nextAt < 0L) {
			schedule(state, now, level.getRandom(), config);
		}
		if (state.phase == 0) {
			if (!config.enabled || players.isEmpty() || now < state.nextAt || level.getDayTime() / 24000L + 1L < config.firstDay) {
				return;
			}
			Disaster pick = pick(level, players, config);
			if (pick == null) {
				schedule(state, now, level.getRandom(), config);
				return;
			}
			warn(level, state, pick, now, players);
			return;
		}
		Disaster disaster = state.current();
		if (disaster == null || !config.enabled || !disaster.enabled(config)) {
			end(level, state, disaster, now, false);
			return;
		}
		if (state.phase == 1) {
			if (now >= state.phaseUntil) {
				begin(level, state, disaster, now, players);
			}
			return;
		}
		if (now >= state.phaseUntil) {
			end(level, state, disaster, now, true);
			return;
		}
		float strength = strength(state, now);
		for (ServerPlayer player : players) {
			act(level, state, disaster, player, strength, config.destructive);
		}
		if (disaster == Disaster.TWISTER) {
			twister(level, state, strength, config.destructive);
		}
		if (server.getTickCount() % 60 == 3) {
			broadcast(server);
		}
	}

	private static void schedule(State state, long now, RandomSource random, DeadlandsConfig.Disasters config) {
		double days = Math.max(1, config.averageDaysBetween);
		state.nextAt = now + (long) (24000L * days * (0.5 + random.nextDouble()));
		state.setDirty();
	}

	private static @Nullable Disaster pick(ServerLevel level, List<ServerPlayer> players, DeadlandsConfig.Disasters config) {
		ServerPlayer anchor = players.get(level.getRandom().nextInt(players.size()));
		float temperature = level.getBiome(anchor.blockPosition()).value().getBaseTemperature();
		List<Disaster> options = new ArrayList<>();
		int total = 0;
		for (Disaster disaster : Disaster.values()) {
			if (!disaster.enabled(config)) {
				continue;
			}
			if (disaster == Disaster.WHITEOUT && temperature > 0.45F || disaster == Disaster.DUST_WALL && temperature < 0.9F) {
				continue;
			}
			options.add(disaster);
			total += disaster.weight();
		}
		if (total <= 0) {
			return null;
		}
		int roll = level.getRandom().nextInt(total);
		for (Disaster disaster : options) {
			roll -= disaster.weight();
			if (roll < 0) {
				return disaster;
			}
		}
		return options.getLast();
	}

	// ------------------------------------------------------------------------------ phases

	private static void warn(ServerLevel level, State state, Disaster disaster, long now, List<ServerPlayer> players) {
		state.disaster = disaster.ordinal();
		state.phase = 1;
		state.phaseUntil = now + disaster.warnSeconds() * 20L;
		ServerPlayer anchor = players.get(level.getRandom().nextInt(players.size()));
		double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
		state.x = anchor.getX() + Math.cos(angle) * 70.0;
		state.z = anchor.getZ() + Math.sin(angle) * 70.0;
		state.heading = angle + Math.PI + (level.getRandom().nextDouble() - 0.5);
		state.setDirty();
		Component line = Component.translatable("disaster.deadlands." + disaster.id() + ".warning").withStyle(ChatFormatting.GOLD);
		for (ServerPlayer player : players) {
			player.sendSystemMessage(line);
			player.connection.send(new ClientboundSetActionBarTextPacket(line));
			sound(player, warningSound(disaster), 1.0F);
		}
		broadcast(level.getServer());
	}

	private static void begin(ServerLevel level, State state, Disaster disaster, long now, List<ServerPlayer> players) {
		RandomSource random = level.getRandom();
		int seconds = disaster.minSeconds() + random.nextInt(disaster.maxSeconds() - disaster.minSeconds() + 1);
		state.phase = 2;
		state.started = now;
		state.phaseUntil = now + seconds * 20L;
		state.setDirty();
		if (disaster.sky() != null) {
			WeatherDirector.force(level.getServer(), disaster.sky(), seconds * 20 + 200);
		}
		if (disaster == Disaster.STATIC_STORM) {
			level.setWeatherParameters(0, seconds * 20 + 200, true, true);
		}
		Component line = Component.translatable("disaster.deadlands." + disaster.id() + ".start").withStyle(ChatFormatting.RED);
		for (ServerPlayer player : players) {
			player.sendSystemMessage(line);
			player.connection.send(new ClientboundSetActionBarTextPacket(line));
		}
		broadcast(level.getServer());
	}

	private static void end(ServerLevel level, State state, @Nullable Disaster disaster, long now, boolean announce) {
		if (disaster == Disaster.FLASH_FLOOD) {
			drain(level);
		}
		if (disaster != null && disaster.sky() != null && state.phase == 2) {
			WeatherDirector.force(level.getServer(), WeatherDirector.Kind.CLEAR, 200);
		}
		if (announce && disaster != null) {
			Component line = Component.translatable("disaster.deadlands.all_clear", Component.translatable("disaster.deadlands." + disaster.id()))
				.withStyle(ChatFormatting.GREEN);
			for (ServerPlayer player : players(level)) {
				player.connection.send(new ClientboundSetActionBarTextPacket(line));
			}
		}
		state.phase = 0;
		state.disaster = -1;
		schedule(state, now, level.getRandom(), ConfigManager.get().disasters);
		broadcast(level.getServer());
	}

	/** 0 to 1: building over the first fifth, holding, easing over the last fifth (with gusts). */
	private static float strength(State state, long now) {
		float length = Math.max(1.0F, state.phaseUntil - state.started);
		float t = (now - state.started) / length;
		float ramp = Mth.clamp(Math.min(t, 1.0F - t) * 5.0F, 0.0F, 1.0F);
		return Mth.clamp(ramp * (0.8F + 0.2F * Mth.sin(now * 0.05F)), 0.0F, 1.0F);
	}

	// ------------------------------------------------------------------------------ effects, per survivor

	private static void act(ServerLevel level, State state, Disaster disaster, ServerPlayer player, float strength, boolean destructive) {
		if (strength <= 0.01F) {
			return;
		}
		RandomSource random = level.getRandom();
		BlockPos feet = player.blockPosition();
		boolean outside = level.canSeeSky(BlockPos.containing(player.getEyePosition()));
		if (random.nextInt(40) == 0) {
			sound(player, loopSound(disaster), 0.6F + 0.4F * strength);
		}
		switch (disaster) {
			case TWISTER -> {
				// The twister itself is moved and drawn once ({@link #twister}); here, debris in the air.
				if (outside && random.nextInt(3) == 0) {
					level.sendParticles(player, ParticleTypes.CLOUD, false, false, player.getX() + random.nextGaussian() * 6, player.getY() + 2 + random.nextDouble() * 4,
						player.getZ() + random.nextGaussian() * 6, 2, 0.3, 0.3, 0.3, 0.2);
				}
			}
			case FLASH_FLOOD -> {
				int surface = level.getSeaLevel() + 3;
				boolean low = feet.getY() <= surface || player.isInWater();
				if (low && outside) {
					player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, strength > 0.6F ? 1 : 0, true, false));
					Vec3 push = new Vec3(Math.cos(state.heading), 0.0, Math.sin(state.heading)).scale(0.035 * strength);
					player.push(push.x, 0.0, push.z);
					player.hurtMarked = true;
				}
				if (destructive && random.nextInt(4) == 0) {
					flood(level, player, random);
				}
			}
			case TREMOR -> {
				// Shaking comes in waves: strong for a few seconds, then quiet.
				boolean wave = Mth.sin((level.getGameTime() - state.started) * 0.06F) > -0.2F;
				if (wave && player.onGround()) {
					if (random.nextInt(6) == 0) {
						player.push((random.nextDouble() - 0.5) * 0.12 * strength, 0.04 * strength, (random.nextDouble() - 0.5) * 0.12 * strength);
						player.hurtMarked = true;
					}
					if (random.nextInt(3) == 0) {
						BlockState under = level.getBlockState(feet.below());
						level.sendParticles(player, new BlockParticleOption(ParticleTypes.FALLING_DUST, under.isAir() ? Blocks.STONE.defaultBlockState() : under),
							false, false, player.getX(), player.getY() + 3, player.getZ(), 6, 3, 1, 3, 0.0);
					}
					if (destructive && random.nextInt(10) == 0) {
						loosen(level, feet, random);
					}
				}
			}
			case SKYFALL -> {
				if (random.nextInt(Math.max(4, (int) (14 - 8 * strength))) == 0) {
					impact(level, player, random, destructive);
				}
			}
			case WHITEOUT -> {
				if (outside) {
					player.setTicksFrozen(Math.min(player.getTicksRequiredToFreeze() + 40, player.getTicksFrozen() + (int) (4 + 6 * strength)));
					player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0, true, false));
					level.sendParticles(player, ParticleTypes.SNOWFLAKE, false, false, player.getX(), player.getY() + 2, player.getZ(), 12, 5, 2, 5, 0.05);
					if (destructive && random.nextInt(8) == 0) {
						snow(level, player, random);
					}
				}
			}
			case DUST_WALL -> {
				if (outside) {
					player.causeFoodExhaustion(0.02F * strength * TICK_EVERY);
					player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0, true, false));
					if (strength > 0.7F && random.nextInt(30) == 0) {
						player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, true, false));
					}
				}
			}
			case CAUSTIC_RAIN -> {
				if (outside && random.nextInt(12) == 0) {
					ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
					if (!helmet.isEmpty()) {
						helmet.hurtAndBreak(1, player, EquipmentSlot.HEAD);
					} else {
						player.hurtServer(level, level.damageSources().magic(), 1.0F);
						sound(player, DLSounds.DISASTER_CAUSTIC_BURN.value(), 0.7F);
					}
				}
				if (destructive && random.nextInt(6) == 0) {
					BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
						feet.offset(random.nextInt(17) - 8, 0, random.nextInt(17) - 8)).below();
					if (level.getBlockState(ground).is(Blocks.GRASS_BLOCK)) {
						level.setBlockAndUpdate(ground, Blocks.DIRT.defaultBlockState());
					}
				}
			}
			case STATIC_STORM -> {
				if (random.nextInt(Math.max(6, (int) (24 - 14 * strength))) == 0) {
					strike(level, player, random, destructive);
				}
			}
		}
	}

	// ------------------------------------------------------------------------------ the twister

	private static void twister(ServerLevel level, State state, float strength, boolean destructive) {
		RandomSource random = level.getRandom();
		state.heading += (random.nextDouble() - 0.5) * 0.15;
		state.x += Math.cos(state.heading) * 0.55;
		state.z += Math.sin(state.heading) * 0.55;
		state.setDirty();
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(state.x), Mth.floor(state.z));
		if (!level.isLoaded(BlockPos.containing(state.x, ground, state.z))) {
			return;
		}
		// The funnel: a column that widens with height, spinning.
		for (int i = 0; i < 18; i++) {
			double h = random.nextDouble() * 34.0;
			double r = 1.0 + h * 0.28;
			double a = random.nextDouble() * Math.PI * 2.0;
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, state.x + Math.cos(a) * r, ground + h, state.z + Math.sin(a) * r, 1,
				-Math.sin(a) * 0.2, 0.05, Math.cos(a) * 0.2, 0.02);
		}
		level.sendParticles(ParticleTypes.CLOUD, state.x, ground + 0.5, state.z, 6, 2.0, 0.2, 2.0, 0.15);
		Vec3 eye = new Vec3(state.x, ground, state.z);
		double reach = 18.0 * (0.5 + 0.5 * strength);
		for (Entity entity : level.getEntities((Entity) null, new AABB(eye, eye).inflate(reach, 30.0, reach), e -> e instanceof LivingEntity && e.isAlive())) {
			if (entity instanceof ServerPlayer player && (player.isCreative() || player.isSpectator())) {
				continue;
			}
			Vec3 to = eye.subtract(entity.position());
			double flat = Math.sqrt(to.x * to.x + to.z * to.z);
			double pull = (1.0 - flat / reach) * 0.11 * strength;
			if (pull <= 0.0) {
				continue;
			}
			// Drawn in, swung round and lifted.
			Vec3 in = new Vec3(to.x, 0.0, to.z).normalize();
			Vec3 round = new Vec3(-in.z, 0.0, in.x);
			entity.push(in.x * pull + round.x * pull * 1.2, flat < 5.0 ? 0.09 * strength : 0.0, in.z * pull + round.z * pull * 1.2);
			entity.hurtMarked = true;
			if (flat < 3.0 && random.nextInt(8) == 0) {
				entity.hurtServer(level, level.damageSources().generic(), 2.0F);
			}
		}
		if (destructive && random.nextInt(2) == 0) {
			BlockPos at = BlockPos.containing(state.x + random.nextGaussian() * 2.5, ground + random.nextInt(6) - 2, state.z + random.nextGaussian() * 2.5);
			BlockState block = level.getBlockState(at);
			if (!block.isAir() && block.getDestroySpeed(level, at) >= 0.0F && block.getDestroySpeed(level, at) < 0.8F) {
				level.destroyBlock(at, true);
			}
		}
	}

	// ------------------------------------------------------------------------------ helpers

	private static void impact(ServerLevel level, ServerPlayer player, RandomSource random, boolean destructive) {
		double angle = random.nextDouble() * Math.PI * 2.0;
		double distance = 6.0 + random.nextDouble() * 34.0;
		double x = player.getX() + Math.cos(angle) * distance;
		double z = player.getZ() + Math.sin(angle) * distance;
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
		// The streak down, then the hit.
		for (int i = 0; i < 24; i++) {
			double up = i * 2.5;
			level.sendParticles(ParticleTypes.FLAME, x + up * 0.4, y + up, z + up * 0.2, 2, 0.15, 0.15, 0.15, 0.01);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, x + up * 0.4, y + up, z + up * 0.2, 1, 0.2, 0.2, 0.2, 0.0);
		}
		level.explode(null, x, y + 0.5, z, destructive ? 2.6F : 2.0F, destructive, destructive ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
	}

	private static void strike(ServerLevel level, ServerPlayer player, RandomSource random, boolean destructive) {
		double angle = random.nextDouble() * Math.PI * 2.0;
		double distance = 4.0 + random.nextDouble() * 40.0;
		double x = player.getX() + Math.cos(angle) * distance;
		double z = player.getZ() + Math.sin(angle) * distance;
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
		if (bolt == null) {
			return;
		}
		bolt.snapTo(new Vec3(x, y, z));
		bolt.setVisualOnly(!destructive); // a real bolt starts fires
		level.addFreshEntity(bolt);
		if (!destructive) {
			// The same shock without the fire.
			for (Entity entity : level.getEntities((Entity) null, new AABB(x - 3, y - 2, z - 3, x + 3, y + 4, z + 3), e -> e instanceof LivingEntity)) {
				entity.hurtServer(level, level.damageSources().lightningBolt(), 5.0F);
			}
		}
	}

	private static void flood(ServerLevel level, ServerPlayer player, RandomSource random) {
		BlockPos at = player.blockPosition().offset(random.nextInt(25) - 12, 0, random.nextInt(25) - 12);
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, at);
		if (top.getY() > level.getSeaLevel() + 3 || !level.getBlockState(top).isAir() || !level.getBlockState(top.below()).isSolid()) {
			return;
		}
		level.setBlockAndUpdate(top, Blocks.WATER.defaultBlockState());
		FLOOD_WATER.add(top.immutable());
	}

	private static void drain(ServerLevel level) {
		for (BlockPos pos : FLOOD_WATER) {
			if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.WATER)) {
				level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			}
		}
		FLOOD_WATER.clear();
	}

	private static void loosen(ServerLevel level, BlockPos feet, RandomSource random) {
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feet.offset(random.nextInt(13) - 6, 0, random.nextInt(13) - 6)).below();
		Block block = level.getBlockState(at).getBlock();
		if (block == Blocks.COBBLESTONE || block == Blocks.STONE_BRICKS) {
			level.setBlockAndUpdate(at, (block == Blocks.STONE_BRICKS ? Blocks.CRACKED_STONE_BRICKS : Blocks.GRAVEL).defaultBlockState());
		} else if (block == Blocks.GLASS || block == Blocks.GLASS_PANE) {
			level.destroyBlock(at, false);
		}
	}

	private static void snow(ServerLevel level, ServerPlayer player, RandomSource random) {
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, player.blockPosition().offset(random.nextInt(17) - 8, 0, random.nextInt(17) - 8));
		if (level.getBlockState(at).isAir() && Blocks.SNOW.defaultBlockState().canSurvive(level, at)) {
			level.setBlockAndUpdate(at, Blocks.SNOW.defaultBlockState());
		}
	}

	private static SoundEvent warningSound(Disaster disaster) {
		return DLSounds.DISASTER_WARNINGS.get(disaster.ordinal()).value();
	}

	private static SoundEvent loopSound(Disaster disaster) {
		return DLSounds.DISASTER_SOUNDS.get(disaster.ordinal()).value();
	}

	private static void sound(ServerPlayer player, SoundEvent sound, float volume) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.WEATHER, volume, 1.0F);
	}

	private static List<ServerPlayer> players(ServerLevel level) {
		return level.players().stream().filter(p -> !p.isSpectator() && p.isAlive()).toList();
	}

	public static void broadcast(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			send(server, player);
		}
	}

	private static void send(MinecraftServer server, ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, DisasterPayload.TYPE)) {
			return;
		}
		State state = state(server);
		Disaster disaster = state.current();
		boolean here = disaster != null && player.level() == server.overworld();
		float strength = here && state.phase == 2 ? strength(state, server.overworld().getGameTime()) : 0.0F;
		ServerPlayNetworking.send(player, new DisasterPayload(here ? disaster.ordinal() : -1, state.warning(), strength));
	}
}
