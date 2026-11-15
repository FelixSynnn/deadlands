package com.deadlands.horror;

import com.deadlands.Deadlands;
import com.deadlands.achievement.Achievements;
import com.deadlands.audio.ChaseTracker;
import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import com.deadlands.content.DLContent;
import com.deadlands.flags.StoryFlags;
import com.deadlands.item.FlashlightItem;
import com.deadlands.network.HorrorBusyPayload;
import com.deadlands.network.HorrorScarePayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Horror Director (Phase 2): server-authoritative, one schedule per survivor, presentation on
 * their client ({@code ScarePlayer}).
 *
 * <p><b>Active play</b> is what counts: a survivor who has not moved or looked around for a minute,
 * or who has a screen open, is not playing, and their clock stops. <b>Frequency</b> follows
 * {@code horror.scarePreset}; on Normal, at night a major scare every 4 to 8 minutes of active play
 * and a minor, unsettling event every 1 to 3; by day a major scare every 10 to 15 minutes and a
 * minor one every 3 to 6. Low stretches these by three quarters, Nightmare shortens them to just
 * over half.
 *
 * <p><b>Choice:</b> a scare fits when its time, place, darkness, water and flashlight conditions
 * hold where the survivor is; among those, one is drawn by weight, never one this survivor had in
 * the last 30 minutes. No major scare while infected are chasing them (a fight is not the time).
 *
 * <p><b>Never during</b> conversations or cutscenes, and not for two minutes after them or after a
 * major story beat ({@link #grace}). Server steps ({@code slam_door}) run here at their tick; every
 * other step is the client's.
 */
public final class HorrorDirector {
	private static final int CHECK_TICKS = 20;
	private static final int IDLE_LIMIT = 1200;
	private static final long NO_REPEAT_TICKS = 36000L;
	public static final int GRACE_TICKS = 2400;
	private static final int MINUTE = 1200;
	private static final RandomSource RANDOM = RandomSource.create();

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static final List<Pending> PENDING = new ArrayList<>();

	private static final class State {
		int activeTicks;
		int nextMajor = -1;
		int nextMinor = -1;
		Vec3 lastPos = Vec3.ZERO;
		float lastYaw;
		float lastPitch;
		int idle;
		long graceUntil;
		int busy;
		/** Game time the survivor opened a screen (the closed-inventory scare). */
		long screenSince = -1;
		final Map<Identifier, Long> recent = new HashMap<>();
	}

	/** Server-side actions of a scare, due at a game time (fixtures dying one by one, a strobe's toggles). */
	private record Timed(UUID player, long due, Consumer<ServerPlayer> action) {
	}

	private static final List<Timed> TIMED = new ArrayList<>();
	/** The hidden horror achievements, one per Phase 4 jumpscare survived. */
	private static final Map<String, String> SCARE_ACHIEVEMENTS = Map.of(
		"ceiling_crawl", "horror/looked_up", "caught_in_the_beam", "horror/in_the_beam", "emergency_strobe", "horror/red_light",
		"still_water", "horror/still_water", "breath_on_glass", "horror/glass", "closed_inventory", "horror/doorway",
		"dark_building", "horror/dark_building", "feeding_turn", "horror/interrupted");
	private static final java.util.Set<String> SERVER_STEPS = java.util.Set.of("slam_door", "fixtures_out", "emergency_strobe");
	/** A menu kept open this long, then closed, can bring the closed-inventory scare. */
	private static final long SCREEN_SCARE_TICKS = 80;

	private record Pending(UUID player, long due, Scare.Step step) {
	}

	private HorrorDirector() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			runPending(server);
			runTimed(server);
			if (server.getTickCount() % CHECK_TICKS == 11) {
				tick(server);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.getPlayer().getUUID()));
		ServerPlayNetworking.registerGlobalReceiver(HorrorBusyPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			State state = state(player);
			if (state.busy == HorrorBusyPayload.STORY && payload.state() != HorrorBusyPayload.STORY) {
				grace(player, GRACE_TICKS);
			}
			long now = player.level().getGameTime();
			if (payload.state() == HorrorBusyPayload.SCREEN && state.busy != HorrorBusyPayload.SCREEN) {
				state.screenSince = now;
			} else if (payload.state() == HorrorBusyPayload.FREE && state.busy == HorrorBusyPayload.SCREEN && state.screenSince >= 0) {
				if (now - state.screenSince >= SCREEN_SCARE_TICKS) {
					closedScreen(player, state);
				}
				state.screenSince = -1;
			}
			state.busy = payload.state();
		});
	}

	private static State state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), id -> new State());
	}

	/** No scares for this survivor for {@code ticks} (after cutscenes, conversations, major story beats). */
	public static void grace(ServerPlayer player, int ticks) {
		State state = state(player);
		state.graceUntil = Math.max(state.graceUntil, player.level().getGameTime() + ticks);
	}

	/** Whether this survivor is actively playing (moved or looked around in the last minute, no screen open). */
	public static boolean active(ServerPlayer player) {
		State state = STATES.get(player.getUUID());
		return state != null && state.idle < IDLE_LIMIT && state.busy == HorrorBusyPayload.FREE;
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			State state = state(player);
			Vec3 pos = player.position();
			boolean moved = pos.distanceToSqr(state.lastPos) > 0.04 || Math.abs(player.getYRot() - state.lastYaw) > 3.0F
				|| Math.abs(player.getXRot() - state.lastPitch) > 3.0F;
			state.lastPos = pos;
			state.lastYaw = player.getYRot();
			state.lastPitch = player.getXRot();
			state.idle = moved ? 0 : state.idle + CHECK_TICKS;
			if (player.isSpectator() || player.isCreative() || !active(player)) {
				continue;
			}
			state.activeTicks += CHECK_TICKS;
			ServerLevel level = player.level();
			if (level.getGameTime() < state.graceUntil) {
				continue;
			}
			boolean night = level.isDarkOutside();
			if (state.nextMajor < 0) {
				state.nextMajor = state.activeTicks + interval(Scare.Kind.MAJOR, night);
				state.nextMinor = state.activeTicks + interval(Scare.Kind.MINOR, night);
			}
			if (state.activeTicks >= state.nextMajor) {
				boolean done = ChaseTracker.chasers(player) == 0 && tryScare(player, Scare.Kind.MAJOR);
				state.nextMajor = state.activeTicks + (done ? interval(Scare.Kind.MAJOR, night) : MINUTE);
			} else if (state.activeTicks >= state.nextMinor) {
				boolean done = tryScare(player, Scare.Kind.MINOR);
				state.nextMinor = state.activeTicks + (done ? interval(Scare.Kind.MINOR, night) : MINUTE / 2);
			}
		}
	}

	/** Ticks of active play until the next scare of a kind, from the preset. */
	private static int interval(Scare.Kind kind, boolean night) {
		float min;
		float max;
		if (kind == Scare.Kind.MAJOR) {
			min = night ? 4 : 10;
			max = night ? 8 : 15;
		} else {
			min = night ? 1 : 3;
			max = night ? 3 : 6;
		}
		DeadlandsConfig.ScarePreset preset = ConfigManager.get().horror.scarePreset;
		float scale = preset == DeadlandsConfig.ScarePreset.LOW ? 1.75F : preset == DeadlandsConfig.ScarePreset.NIGHTMARE ? 0.55F : 1.0F;
		return Math.round((min + RANDOM.nextFloat() * (max - min)) * scale * MINUTE);
	}

	/** Draw a fitting scare of this kind and perform it; false if none fits right now. */
	private static boolean tryScare(ServerPlayer player, Scare.Kind kind) {
		ServerLevel level = player.level();
		BlockPos pos = player.blockPosition();
		boolean night = level.isDarkOutside();
		boolean outdoors = level.canSeeSky(pos);
		boolean dark = level.getBrightness(LightLayer.BLOCK, pos) <= 4 && (night || !outdoors);
		boolean flashlight = FlashlightItem.shining(player.getMainHandItem()) || FlashlightItem.shining(player.getOffhandItem());
		Boolean water = null;
		State state = state(player);
		long now = level.getGameTime();
		List<Map.Entry<Identifier, Scare>> fits = new ArrayList<>();
		int total = 0;
		for (Map.Entry<Identifier, Scare> entry : DLContent.JUMPSCARES.all().entrySet()) {
			Scare scare = entry.getValue();
			Long last = state.recent.get(entry.getKey());
			if (scare.kind() != kind || (last != null && now - last < NO_REPEAT_TICKS)
				|| (scare.time() == Scare.Time.NIGHT && !night) || (scare.time() == Scare.Time.DAY && night)
				|| (scare.place() == Scare.Place.OUTDOORS && !outdoors) || (scare.place() == Scare.Place.INDOORS && outdoors)
				|| (scare.dark() && !dark) || (scare.flashlight() && !flashlight) || !scare.trigger().equals("director")) {
				continue;
			}
			if (scare.requires().isPresent() && !ScareConditions.met(player, scare.requires().get(), outdoors)) {
				continue;
			}
			if (scare.nearWater()) {
				if (water == null) {
					water = nearWater(level, pos);
				}
				if (!water) {
					continue;
				}
			}
			fits.add(entry);
			total += Math.max(1, scare.weight());
		}
		if (fits.isEmpty()) {
			return false;
		}
		int pick = RANDOM.nextInt(total);
		for (Map.Entry<Identifier, Scare> entry : fits) {
			pick -= Math.max(1, entry.getValue().weight());
			if (pick < 0) {
				perform(player, entry.getKey(), entry.getValue());
				return true;
			}
		}
		return false;
	}

	/** Perform a scare now, whatever the conditions (also {@code /deadlands horror <id>}). */
	public static void perform(ServerPlayer player, Identifier id, Scare scare) {
		State state = state(player);
		long now = player.level().getGameTime();
		state.recent.put(id, now);
		List<HorrorScarePayload.Step> steps = new ArrayList<>();
		for (Scare.Step step : scare.steps()) {
			if (SERVER_STEPS.contains(step.type())) {
				PENDING.add(new Pending(player.getUUID(), now + step.at(), step));
			} else {
				steps.add(new HorrorScarePayload.Step(step.at(), step.type(), step.sound().map(Identifier::toString).orElse(""), step.volume(),
					step.pitch(), step.where(), step.distance(), step.amount()));
			}
		}
		ServerPlayNetworking.send(player, new HorrorScarePayload(id.toString(), steps));
		String earned = SCARE_ACHIEVEMENTS.get(id.getPath());
		if (earned != null) {
			Achievements.award(player, earned);
		}
		if (scare.kind() == Scare.Kind.MAJOR) {
			int survived = StoryFlags.add(player, "scares_survived", 1);
			Achievements.award(player, "horror/first_scare");
			if (survived >= 50) {
				Achievements.award(player, "horror/fifty_scares");
			}
		}
		if (ConfigManager.get().general.debugLogging) {
			Deadlands.LOGGER.info("Horror Director: {} for {}", id, player.getScoreboardName());
		}
	}

	private static void runPending(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		Iterator<Pending> it = PENDING.iterator();
		while (it.hasNext()) {
			Pending pending = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(pending.player());
			if (player == null) {
				it.remove();
				continue;
			}
			if (player.level().getGameTime() < pending.due()) {
				continue;
			}
			it.remove();
			switch (pending.step().type()) {
				case "slam_door" -> slamDoor(player.level(), player.blockPosition());
				case "fixtures_out" -> schedule(player, ScareConditions.fixturesOut(player, Math.max(1, Math.round(pending.step().amount()))));
				case "emergency_strobe" -> schedule(player, ScareConditions.strobe(player, Math.max(1, Math.round(pending.step().amount()))));
				default -> {
				}
			}
		}
	}

	private static void schedule(ServerPlayer player, List<Map.Entry<Integer, Consumer<ServerPlayer>>> actions) {
		long now = player.level().getGameTime();
		for (Map.Entry<Integer, Consumer<ServerPlayer>> action : actions) {
			TIMED.add(new Timed(player.getUUID(), now + action.getKey(), action.getValue()));
		}
	}

	private static void runTimed(MinecraftServer server) {
		if (TIMED.isEmpty()) {
			return;
		}
		Iterator<Timed> it = TIMED.iterator();
		List<Timed> due = new ArrayList<>();
		while (it.hasNext()) {
			Timed timed = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(timed.player());
			if (player == null) {
				it.remove();
			} else if (player.level().getGameTime() >= timed.due()) {
				due.add(timed);
				it.remove();
			}
		}
		for (Timed timed : due) {
			ServerPlayer player = server.getPlayerList().getPlayer(timed.player());
			if (player != null) {
				timed.action().accept(player);
			}
		}
	}

	/**
	 * Fires an event scare by id if this survivor has not had it within {@code cooldown} ticks and no grace
	 * period, cutscene or chase is under way. Returns whether it played.
	 */
	public static boolean fire(ServerPlayer player, String id, long cooldown) {
		Identifier key = Identifier.tryParse(id);
		if (key == null || player.isSpectator() || player.isCreative()) {
			return false;
		}
		java.util.Optional<Scare> scare = DLContent.JUMPSCARES.get(key);
		State state = state(player);
		long now = player.level().getGameTime();
		Long last = state.recent.get(key);
		if (scare.isEmpty() || (last != null && now - last < cooldown) || now < state.graceUntil || state.busy == HorrorBusyPayload.STORY) {
			return false;
		}
		perform(player, key, scare.get());
		return true;
	}

	/** Closed Inventory: after a menu was kept open a while, indoors at night, never in a world's first hour. */
	private static void closedScreen(ServerPlayer player, State state) {
		ServerLevel level = player.level();
		BlockPos pos = player.blockPosition();
		if (!level.isDarkOutside() || level.canSeeSky(pos) || level.getGameTime() < 72000L || ChaseTracker.chasers(player) > 0) {
			return;
		}
		fire(player, "deadlands:closed_inventory", 48000L);
	}

	/** The nearest open door within 8 blocks swings shut (it makes its own sound). */
	private static void slamDoor(ServerLevel level, BlockPos center) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-8, -3, -8), center.offset(8, 3, 8))) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.OPEN) && pos.distSqr(center) < bestDistance) {
				best = pos.immutable();
				bestDistance = pos.distSqr(center);
			}
		}
		if (best != null) {
			BlockState state = level.getBlockState(best);
			((DoorBlock) state.getBlock()).setOpen(null, level, state, best, false);
		}
	}

	private static boolean nearWater(ServerLevel level, BlockPos center) {
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-6, -3, -6), center.offset(6, 2, 6))) {
			if (!level.getFluidState(pos).isEmpty() && level.getFluidState(pos).isSource()) {
				return true;
			}
		}
		return false;
	}

	/** For {@code /deadlands horror status}: active ticks and the next scheduled scares. */
	public static String status(ServerPlayer player) {
		State state = state(player);
		return "active " + state.activeTicks / 20 + " s, next major at " + state.nextMajor / 20 + " s, next minor at " + state.nextMinor / 20
			+ " s, idle " + state.idle / 20 + " s, busy " + state.busy + ", grace " + Math.max(0, state.graceUntil - player.level().getGameTime()) / 20 + " s";
	}
}
