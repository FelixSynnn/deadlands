package com.deadlands.horror;

import com.deadlands.block.EmergencyLightBlock;
import com.deadlands.block.FluorescentLightBlock;
import com.deadlands.entity.InfectedEntity;
import com.deadlands.registry.DLEntities;
import com.deadlands.world.town.TownSite;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Phase 4, the "Dark Building" jumpscare. When a town's power comes back, the first fluorescent light
 * that would switch on claims its building: every fixture within nine blocks of it stays dark and its
 * emergency lights keep pulsing, while the rest of the town lights up. When a survivor comes within
 * seven blocks, every tube in it snaps on at once, showing three to five infected standing still and
 * facing them; a second and a half later they charge. Once per town, saved with the world.
 */
public final class DarkBuildings {
	private static final int REACH_XZ = 9;
	private static final int REACH_Y = 4;
	private static final double TRIGGER = 7.0;
	private static final int STILL_TICKS = 30;
	private static final RandomSource RANDOM = RandomSource.create();
	private static final List<Release> RELEASES = new ArrayList<>();

	private record Release(UUID infected, UUID player, long due) {
	}

	/** A town waiting for its dark building (anchor once claimed), or done. */
	private record Entry(long town, Optional<Long> anchor, boolean done) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("town").forGetter(Entry::town),
			Codec.LONG.optionalFieldOf("anchor").forGetter(Entry::anchor),
			Codec.BOOL.optionalFieldOf("done", false).forGetter(Entry::done)
		).apply(i, Entry::new));
	}

	public static final class State extends SavedData {
		static final Codec<State> CODEC = Entry.CODEC.listOf().xmap(State::new, s -> new ArrayList<>(s.entries.values()));
		static final SavedDataType<State> TYPE = new SavedDataType<>("deadlands_dark_buildings", State::new, CODEC, null);
		final Map<Long, Entry> entries = new HashMap<>();

		public State() {
		}

		State(List<Entry> list) {
			for (Entry entry : list) {
				entries.put(entry.town(), entry);
			}
		}

		void put(Entry entry) {
			entries.put(entry.town(), entry);
			setDirty();
		}
	}

	private DarkBuildings() {
	}

	private static State state(ServerLevel level) {
		return level.getServer().overworld().getDataStorage().computeIfAbsent(State.TYPE);
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			release(server);
			if (server.getTickCount() % 10 == 3) {
				watch(server);
			}
		});
	}

	/** The town's power was just restored: one of its buildings will stay dark. */
	public static void arm(ServerLevel level, TownSite town) {
		State state = state(level);
		if (!state.entries.containsKey(town.key())) {
			state.put(new Entry(town.key(), Optional.empty(), false));
		}
	}

	/**
	 * Whether a fixture at {@code pos} in this (powered) town must stay dark because it is in the dark
	 * building. With {@code claim}, the first fixture asking claims its building.
	 */
	public static boolean holdsDark(ServerLevel level, BlockPos pos, TownSite town, boolean claim) {
		Entry entry = state(level).entries.get(town.key());
		if (entry == null || entry.done()) {
			return false;
		}
		if (entry.anchor().isEmpty()) {
			if (!claim) {
				return false;
			}
			state(level).put(new Entry(town.key(), Optional.of(pos.asLong()), false));
			return true;
		}
		return inside(BlockPos.of(entry.anchor().get()), pos);
	}

	private static boolean inside(BlockPos anchor, BlockPos pos) {
		return Math.abs(pos.getX() - anchor.getX()) <= REACH_XZ && Math.abs(pos.getZ() - anchor.getZ()) <= REACH_XZ
			&& Math.abs(pos.getY() - anchor.getY()) <= REACH_Y;
	}

	private static void watch(MinecraftServer server) {
		ServerLevel level = server.overworld();
		State state = state(level);
		for (Entry entry : List.copyOf(state.entries.values())) {
			if (entry.done() || entry.anchor().isEmpty()) {
				continue;
			}
			BlockPos anchor = BlockPos.of(entry.anchor().get());
			if (!level.isLoaded(anchor)) {
				continue;
			}
			for (ServerPlayer player : level.players()) {
				if (player.isSpectator() || player.isCreative() || Math.abs(player.getY() - anchor.getY()) > 5) {
					continue;
				}
				double dx = player.getX() - anchor.getX();
				double dz = player.getZ() - anchor.getZ();
				if (dx * dx + dz * dz <= TRIGGER * TRIGGER) {
					light(level, anchor, player);
					state.put(new Entry(entry.town(), entry.anchor(), true));
					break;
				}
			}
		}
	}

	/** Every tube comes on, the emergency lights stop, and the infected inside are revealed. */
	private static void light(ServerLevel level, BlockPos anchor, ServerPlayer player) {
		List<BlockPos> floors = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(anchor.offset(-REACH_XZ, -REACH_Y - 4, -REACH_XZ), anchor.offset(REACH_XZ, REACH_Y, REACH_XZ))) {
			BlockState state = level.getBlockState(p);
			if (state.getBlock() instanceof FluorescentLightBlock && !state.getValue(FluorescentLightBlock.LIT)) {
				level.setBlock(p, state.setValue(FluorescentLightBlock.LIT, true), Block.UPDATE_CLIENTS);
			} else if (state.getBlock() instanceof EmergencyLightBlock && state.getValue(EmergencyLightBlock.LIT)) {
				level.setBlock(p, state.setValue(EmergencyLightBlock.LIT, false), Block.UPDATE_CLIENTS);
			} else if (state.isAir() && level.getBlockState(p.above()).isAir() && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)
				&& p.distSqr(player.blockPosition()) > 9 && level.canSeeSky(p) == false) {
				floors.add(p.immutable());
			}
		}
		HorrorDirector.fire(player, "deadlands:dark_building", 0L);
		int count = Math.min(floors.size(), 3 + RANDOM.nextInt(3));
		for (int i = 0; i < count; i++) {
			BlockPos at = floors.remove(RANDOM.nextInt(floors.size()));
			InfectedEntity infected = DLEntities.WALKER.create(level, EntitySpawnReason.EVENT);
			if (infected == null) {
				continue;
			}
			double dx = player.getX() - (at.getX() + 0.5);
			double dz = player.getZ() - (at.getZ() + 0.5);
			float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
			infected.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
			infected.setYHeadRot(yaw);
			infected.setYBodyRot(yaw);
			infected.setNoAi(true); // standing still, staring, for a moment
			level.addFreshEntity(infected);
			RELEASES.add(new Release(infected.getUUID(), player.getUUID(), level.getGameTime() + STILL_TICKS));
		}
	}

	private static void release(MinecraftServer server) {
		if (RELEASES.isEmpty()) {
			return;
		}
		ServerLevel level = server.overworld();
		Iterator<Release> it = RELEASES.iterator();
		while (it.hasNext()) {
			Release r = it.next();
			if (level.getGameTime() < r.due()) {
				continue;
			}
			it.remove();
			Entity entity = level.getEntity(r.infected());
			ServerPlayer player = server.getPlayerList().getPlayer(r.player());
			if (entity instanceof InfectedEntity infected) {
				infected.setNoAi(false);
				if (player != null) {
					infected.setTarget(player);
				}
			}
		}
	}

	/** For {@code /deadlands horror darkbuildings}: towns waiting, claimed and done. */
	public static String status(ServerLevel level) {
		State state = state(level);
		long waiting = state.entries.values().stream().filter(e -> !e.done() && e.anchor().isEmpty()).count();
		long claimed = state.entries.values().stream().filter(e -> !e.done() && e.anchor().isPresent()).count();
		long done = state.entries.values().stream().filter(Entry::done).count();
		return "dark buildings: " + waiting + " waiting, " + claimed + " claimed, " + done + " done";
	}
}
