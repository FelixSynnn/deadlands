package com.deadlands.horde;

import com.deadlands.config.ConfigManager;
import com.deadlands.entity.BossEntity;
import com.deadlands.entity.InfectedEntity;
import com.deadlands.entity.InfectedKind;
import com.deadlands.network.BossEventPayload;
import com.deadlands.persistence.ChronicleEntry;
import com.deadlands.persistence.DeadlandsWorldData;
import com.deadlands.progression.BossKind;
import com.deadlands.progression.EncounterSchedule;
import com.deadlands.progression.ScheduleSettings;
import com.deadlands.progression.WorldClock;
import com.deadlands.registry.DLBosses;
import com.deadlands.spawning.SpawnFinder;
import com.deadlands.story.StoryState;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * Boss nights. On every boss day of the schedule (every {@code bosses.intervalDays} days, in place of
 * the horde) the boss comes at dusk: the next of the roster ({@link BossKind}), at its escalation tier.
 *
 * <ol>
 *   <li><b>Start.</b> It appears 40 to 56 blocks from one survivor (never on top of anyone) with an
 *       escort of infected, four plus two per tier. Everyone in the overworld gets its title card,
 *       a sting and a chronicle line; it has a boss bar.</li>
 *   <li><b>The night.</b> It hunts. Nobody can sleep while it lives.</li>
 *   <li><b>End.</b> Killed: its trophy and lore drop, the story records it, a card says so. Still
 *       alive at dawn: it retreats (it is removed, and comes back on a later pass of the roster).</li>
 * </ol>
 *
 * The encounter is saved ({@code deadlands_bosses}); the boss is saved with its chunk and marked with
 * its day, so a restart carries on. A boss from an encounter that is over is removed when it loads.
 * {@code /deadlands boss <kind>} brings one at once, outside the schedule.
 */
public final class BossDirector {
	private static final int SPAWN_MIN = 40;
	private static final int SPAWN_MAX = 56;

	/** The current or last boss encounter. */
	public static final class State extends SavedData {
		public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.LONG.optionalFieldOf("day", 0L).forGetter(s -> s.day),
			Codec.STRING.optionalFieldOf("kind", "").forGetter(s -> s.kind),
			UUIDUtil.CODEC.optionalFieldOf("boss").forGetter(s -> Optional.ofNullable(s.boss)),
			Codec.BOOL.optionalFieldOf("active", false).forGetter(s -> s.active),
			Codec.STRING.optionalFieldOf("outcome", "").forGetter(s -> s.outcome)
		).apply(instance, State::new));
		public static final SavedDataType<State> TYPE = new SavedDataType<>("deadlands_bosses", State::new, CODEC, null);

		long day;
		String kind = "";
		@Nullable UUID boss;
		boolean active;
		String outcome = "";

		public State() {
		}

		private State(long day, String kind, Optional<UUID> boss, boolean active, String outcome) {
			this.day = day;
			this.kind = kind;
			this.boss = boss.orElse(null);
			this.active = active;
			this.outcome = outcome;
		}

		public boolean active() {
			return active;
		}

		public long day() {
			return day;
		}
	}

	private BossDirector() {
	}

	public static State state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(State.TYPE);
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 9) {
				tick(server);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof BossEntity boss && boss.encounterDay() > 0) {
				State state = state(level.getServer());
				if (!state.active || state.day != boss.encounterDay()) {
					boss.discard(); // its night is over
				}
			}
		});
		EntitySleepEvents.ALLOW_SLEEPING.register((player, pos) -> sleepProblem(player));
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		State state = state(server);
		long dayTime = level.getDayTime();
		long day = Math.floorDiv(dayTime, WorldClock.TICKS_PER_DAY) + 1;
		long tod = Math.floorMod(dayTime, WorldClock.TICKS_PER_DAY);
		if (state.active) {
			if (day != state.day || tod >= HordeDirector.DAWN) {
				retreat(server, level, state);
			}
			return;
		}
		if (!ConfigManager.get().bosses.enabled || tod < HordeDirector.DUSK || tod >= HordeDirector.DAWN || state.day == day
			|| level.getDifficulty() == Difficulty.PEACEFUL || !level.getGameRules().get(GameRules.SPAWN_MONSTERS)) {
			return;
		}
		EncounterSchedule.Encounter tonight = EncounterSchedule.forDay(day, ScheduleSettings.fromConfig(ConfigManager.get()));
		if (tonight.kind() == EncounterSchedule.Kind.BOSS && tonight.boss() != null) {
			List<ServerPlayer> players = level.players().stream().filter(p -> !p.isSpectator() && !p.isCreative()).toList();
			if (!players.isEmpty()) {
				ServerPlayer chosen = players.get(level.getRandom().nextInt(players.size()));
				start(level, chosen, tonight.boss(), tonight.tier(), day);
			}
		}
	}

	/** Bring a boss near a survivor. Returns it, or null if no place could be found this time (tried again next second). */
	public static @Nullable BossEntity start(ServerLevel level, ServerPlayer near, BossKind kind, int tier, long day) {
		RandomSource random = level.getRandom();
		BossEntity boss = null;
		for (int attempt = 0; attempt < 16 && boss == null; attempt++) {
			BlockPos spot = SpawnFinder.around(level, near.blockPosition(), SPAWN_MIN, SPAWN_MAX, random, false);
			if (spot == null || !level.getFluidState(spot).isEmpty() || !level.getFluidState(spot.below()).isEmpty()) {
				continue;
			}
			if (!level.noCollision(DLBosses.type(kind).getSpawnAABB(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5))) {
				continue;
			}
			boss = DLBosses.type(kind).create(level, EntitySpawnReason.EVENT);
			if (boss != null) {
				boss.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
				boss.prepare(tier, day);
				if (!level.addFreshEntity(boss)) {
					boss = null;
				}
			}
		}
		if (boss == null) {
			return null;
		}
		boss.setTarget(near);
		for (int i = 0; i < 4 + tier * 2; i++) {
			BlockPos spot = SpawnFinder.around(level, boss.blockPosition(), 2.0, 8.0, random, false);
			if (spot != null) {
				InfectedEntity escort = SpawnFinder.spawn(level, random.nextBoolean() ? InfectedKind.WALKER : InfectedKind.RUNNER, spot, EntitySpawnReason.EVENT, null);
				if (escort != null) {
					escort.setTarget(near);
				}
			}
		}
		State state = state(level.getServer());
		if (day > 0) {
			state.day = day;
			state.kind = kind.id();
			state.boss = boss.getUUID();
			state.active = true;
			state.outcome = "";
			state.setDirty();
		}
		if (ConfigManager.get().general.debugLogging) {
			com.deadlands.Deadlands.LOGGER.info("Boss {} (tier {}) at {} for day {}", kind.id(), tier, boss.blockPosition().toShortString(), day);
		}
		broadcast(level.getServer(), new BossEventPayload(BossEventPayload.START, kind.id(), tier));
		com.deadlands.cutscene.Cutscenes.playNear(level, com.deadlands.cutscene.Cutscenes.BOSS_REVEAL, boss.position(), boss.getYRot(), 128.0);
		chronicle(level.getServer(), "chronicle.deadlands.boss.arrived", kind.displayName().getString());
		return boss;
	}

	private static void retreat(MinecraftServer server, ServerLevel level, State state) {
		Entity boss = state.boss == null ? null : level.getEntity(state.boss);
		if (boss != null) {
			boss.discard();
		}
		state.active = false;
		state.outcome = "retreated";
		state.setDirty();
		broadcast(server, new BossEventPayload(BossEventPayload.RETREATED, state.kind, 0));
		BossKind kind = byId(state.kind);
		chronicle(server, "chronicle.deadlands.boss.retreated", kind == null ? state.kind : kind.displayName().getString());
	}

	/** A boss died. Counts for the story whether or not it came on its night. */
	public static void onDefeated(ServerLevel level, BossEntity boss, @Nullable ServerPlayer killer) {
		MinecraftServer server = level.getServer();
		StoryState.get(server).bossDefeated(boss.kind());
		State state = state(server);
		if (state.active && state.boss != null && state.boss.equals(boss.getUUID())) {
			state.active = false;
			state.outcome = "defeated";
			state.setDirty();
		}
		broadcast(server, new BossEventPayload(BossEventPayload.DEFEATED, boss.kind().id(), boss.tier()));
		chronicle(server, killer == null ? "chronicle.deadlands.boss.defeated" : "chronicle.deadlands.boss.defeated_by",
			boss.kind().displayName().getString(), killer == null ? "" : killer.getName().getString());
	}

	private static Player.@Nullable BedSleepingProblem sleepProblem(Player player) {
		if (player.level() instanceof ServerLevel level && level == level.getServer().overworld() && state(level.getServer()).active) {
			return new Player.BedSleepingProblem(Component.translatable("message.deadlands.boss.no_sleep"));
		}
		return null;
	}

	private static void broadcast(MinecraftServer server, BossEventPayload payload) {
		for (ServerPlayer player : server.overworld().players()) {
			if (ServerPlayNetworking.canSend(player, BossEventPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	private static void chronicle(MinecraftServer server, String key, String... args) {
		DeadlandsWorldData data = DeadlandsWorldData.get(server);
		data.addChronicle(new ChronicleEntry(server.overworld().getDayTime() / WorldClock.TICKS_PER_DAY + 1, key, List.of(args)), 64);
		data.setDirty();
	}

	public static @Nullable BossKind byId(String id) {
		for (BossKind kind : BossKind.values()) {
			if (kind.id().equals(id)) {
				return kind;
			}
		}
		return null;
	}
}
