package com.deadlands.event;

import com.deadlands.Deadlands;
import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import com.deadlands.container.LootContainerBlockEntity;
import com.deadlands.entity.InfectedKind;
import com.deadlands.network.WorldEventPayload;
import com.deadlands.npc.Faction;
import com.deadlands.npc.NpcEntity;
import com.deadlands.npc.NpcRole;
import com.deadlands.npc.NpcSpawns;
import com.deadlands.persistence.ChronicleEntry;
import com.deadlands.persistence.DeadlandsWorldData;
import com.deadlands.registry.DLBlocks;
import com.deadlands.registry.DLContainers;
import com.deadlands.registry.DLEvents;
import com.deadlands.registry.DLParticles;
import com.deadlands.spawning.SpawnFinder;
import com.deadlands.vehicle.VehicleEntity;
import com.deadlands.vehicle.VehicleKind;
import com.deadlands.vehicle.VehicleSpawns;
import com.deadlands.world.town.TownIdentity;
import com.deadlands.world.town.TownLookup;
import com.deadlands.world.town.TownSite;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * World events ({@code deadlands_events}). Each morning (from 3000 ticks into the day) the day's events
 * are rolled, near a survivor who is out in the overworld:
 *
 * <ul>
 *   <li><b>Supply drop</b>, every {@code worldEvents.supplyDropIntervalDays} days: a crate on a
 *       parachute ({@link SupplyDropEntity}) 60 to 120 blocks away, announced with its position;
 *       landing brings the infected.</li>
 *   <li>One of these, about every other day: a <b>helicopter crash</b> (burning wreck, a burned-out
 *       jeep, two crates, a smoke column), a <b>military convoy</b> left behind (burned vehicles and
 *       crates), a <b>bandit patrol</b> (three bandits on the move, gone by night), a <b>distress
 *       signal</b> (two survivors with infected closing in, and their stores), or a <b>power
 *       outage</b> in a town survivors had lit (its generator needs fuel again).</li>
 * </ul>
 *
 * Every event is announced (a card and a chat line with where) and the last few are kept for the
 * radio. Migrating hordes are {@code horde.MigrationSystem}; survivors met on the road are
 * {@code npc.NpcSpawns}.
 */
public final class WorldEvents {
	public enum Kind {
		SUPPLY_DROP("supply_drop"), CRASH("crash"), CONVOY("convoy"), BANDITS("bandits"), DISTRESS("distress"), OUTAGE("outage");

		private final String id;

		Kind(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}
	}

	/** One announced event, for the radio. */
	public record Recent(String kind, int x, int z, long day) {
		public static final Codec<Recent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("kind").forGetter(Recent::kind),
			Codec.INT.fieldOf("x").forGetter(Recent::x),
			Codec.INT.fieldOf("z").forGetter(Recent::z),
			Codec.LONG.fieldOf("day").forGetter(Recent::day)
		).apply(instance, Recent::new));
	}

	public static final class State extends SavedData {
		public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.LONG.optionalFieldOf("rolled_day", 0L).forGetter(s -> s.rolledDay),
			Codec.LONG.optionalFieldOf("next_drop_day", 2L).forGetter(s -> s.nextDropDay),
			Recent.CODEC.listOf().optionalFieldOf("recent", List.of()).forGetter(s -> List.copyOf(s.recent))
		).apply(instance, State::new));
		public static final SavedDataType<State> TYPE = new SavedDataType<>("deadlands_events", State::new, CODEC, null);

		long rolledDay;
		long nextDropDay = 2L;
		final List<Recent> recent = new ArrayList<>();

		public State() {
		}

		private State(long rolledDay, long nextDropDay, List<Recent> recent) {
			this.rolledDay = rolledDay;
			this.nextDropDay = nextDropDay;
			this.recent.addAll(recent);
		}

		public List<Recent> recent() {
			return List.copyOf(recent);
		}
	}

	private static final long ROLL_TIME = 3000L;

	private WorldEvents() {
	}

	public static State state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(State.TYPE);
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 40 == 17) {
				tick(server);
			}
		});
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		State state = state(server);
		long day = level.getDayTime() / 24000L + 1L;
		if (state.rolledDay >= day || Math.floorMod(level.getDayTime(), 24000L) < ROLL_TIME) {
			return;
		}
		List<ServerPlayer> players = level.players().stream().filter(p -> !p.isSpectator()).toList();
		if (players.isEmpty()) {
			return;
		}
		state.rolledDay = day;
		state.setDirty();
		DeadlandsConfig.WorldEvents config = ConfigManager.get().events;
		RandomSource random = level.getRandom();
		ServerPlayer near = players.get(random.nextInt(players.size()));
		if (config.supplyDrops && day >= state.nextDropDay) {
			if (start(level, near, Kind.SUPPLY_DROP)) {
				state.nextDropDay = day + Math.max(1, config.supplyDropIntervalDays - 1 + random.nextInt(3));
			}
		}
		if (random.nextFloat() < 0.5F) {
			List<Kind> options = new ArrayList<>(List.of(Kind.CONVOY, Kind.BANDITS, Kind.OUTAGE));
			if (config.crashSites) {
				options.add(Kind.CRASH);
			}
			if (config.distressSignals) {
				options.add(Kind.DISTRESS);
			}
			start(level, near, options.get(random.nextInt(options.size())));
		}
	}

	/** Start an event near a survivor now (also used by {@code /deadlands event}). Returns false if it could not happen here. */
	public static boolean start(ServerLevel level, ServerPlayer near, Kind kind) {
		RandomSource random = level.getRandom();
		BlockPos at = switch (kind) {
			case OUTAGE -> outage(level, near);
			default -> {
				BlockPos spot = spot(level, near.blockPosition(), kind == Kind.BANDITS ? 45 : 60, kind == Kind.CRASH ? 140 : 110, random);
				if (spot == null) {
					yield null;
				}
				switch (kind) {
					case SUPPLY_DROP -> supplyDrop(level, spot);
					case CRASH -> crash(level, spot, random);
					case CONVOY -> convoy(level, spot, random);
					case BANDITS -> bandits(level, spot, random);
					case DISTRESS -> distress(level, spot, random);
					default -> {
					}
				}
				yield spot;
			}
		};
		if (at == null) {
			return false;
		}
		announce(level, kind, at);
		return true;
	}

	// ======================================================================================== the events

	private static void supplyDrop(ServerLevel level, BlockPos spot) {
		SupplyDropEntity drop = DLEvents.SUPPLY_DROP.create(level, EntitySpawnReason.EVENT);
		if (drop != null) {
			drop.snapTo(spot.getX() + 0.5, Math.min(level.getMaxY() - 8, spot.getY() + 60), spot.getZ() + 0.5, 0.0F, 0.0F);
			level.addFreshEntity(drop);
		}
	}

	private static void crash(ServerLevel level, BlockPos spot, RandomSource random) {
		debris(level, spot, 5, random, 0.45F);
		put(level, spot, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, true));
		crate(level, spot.offset(3, 0, -2), "containers/military_crate", random);
		crate(level, spot.offset(-2, 0, 3), "containers/military_crate", random);
		wreck(level, spot.offset(-3, 0, -3), VehicleKind.MILITARY_JEEP, random);
		level.sendParticles(DLParticles.EMBER, spot.getX() + 0.5, spot.getY() + 1.0, spot.getZ() + 0.5, 30, 2.0, 1.0, 2.0, 0.05);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, spot.getX() + 0.5, spot.getY() + 1.0, spot.getZ() + 0.5, 40, 2.0, 1.0, 2.0, 0.02);
	}

	private static void convoy(ServerLevel level, BlockPos spot, RandomSource random) {
		debris(level, spot, 3, random, 0.2F);
		wreck(level, spot, VehicleKind.CARGO_TRUCK, random);
		wreck(level, spot.offset(7, 0, 2), VehicleKind.MILITARY_JEEP, random);
		crate(level, spot.offset(3, 0, 4), "containers/military_crate", random);
		crate(level, spot.offset(-3, 0, 3), "containers/military_crate", random);
	}

	private static void bandits(ServerLevel level, BlockPos spot, RandomSource random) {
		for (int i = 0; i < 3; i++) {
			NpcEntity bandit = NpcSpawns.create(level, NpcRole.BANDIT, Faction.BANDITS, null, ground(level, spot.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2)),
				random.nextFloat() * 360.0F);
			if (bandit != null) {
				bandit.setLeavesAt(level.getGameTime() + 12000L);
			}
		}
	}

	private static void distress(ServerLevel level, BlockPos spot, RandomSource random) {
		for (int i = 0; i < 2; i++) {
			NpcEntity survivor = NpcSpawns.create(level, i == 0 ? NpcRole.SETTLER : NpcRole.DOCTOR, Faction.DRIFTERS, null,
				ground(level, spot.offset(i * 2, 0, 0)), random.nextFloat() * 360.0F);
			if (survivor != null) {
				survivor.setLeavesAt(level.getGameTime() + 24000L);
			}
		}
		crate(level, spot.offset(-2, 0, 1), "containers/campsite", random);
		for (int i = 0; i < 6; i++) {
			BlockPos around = SpawnFinder.around(level, spot, 7.0, 12.0, random, false);
			if (around != null) {
				SpawnFinder.spawn(level, random.nextInt(3) == 0 ? InfectedKind.RUNNER : InfectedKind.WALKER, around, EntitySpawnReason.EVENT, null);
			}
		}
	}

	/** A town survivors had lit loses its power: the generator wants fuel again. Returns the town's centre, or null if none is lit. */
	private static @Nullable BlockPos outage(ServerLevel level, ServerPlayer near) {
		DeadlandsWorldData data = DeadlandsWorldData.get(level.getServer());
		for (TownSite town : TownLookup.around(level, near.blockPosition(), 3)) {
			if (data.isTownPowered(town.key()) && data.unpowerTown(town.key())) {
				return town.center();
			}
		}
		return null;
	}

	// ======================================================================================== helpers

	private static @Nullable BlockPos spot(ServerLevel level, BlockPos center, int min, int max, RandomSource random) {
		for (int attempt = 0; attempt < 16; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = min + random.nextDouble() * (max - min);
			int x = center.getX() + (int) (Math.cos(angle) * distance);
			int z = center.getZ() + (int) (Math.sin(angle) * distance);
			BlockPos column = new BlockPos(x, center.getY(), z);
			if (!areaLoaded(level, column)) {
				continue;
			}
			BlockPos pos = ground(level, column);
			if (level.getFluidState(pos.below()).isEmpty() && level.getFluidState(pos).isEmpty() && level.getBlockState(pos).canBeReplaced()) {
				return pos;
			}
		}
		return null;
	}

	/**
	 * Whether everything an event may touch around a spot (debris, wrecks, crates, and infected up to
	 * twelve blocks out) is in loaded chunks; otherwise placing it would make the server generate
	 * chunks on the spot, a long stall.
	 */
	private static boolean areaLoaded(ServerLevel level, BlockPos column) {
		for (int dx = -SCENE_RADIUS; dx <= SCENE_RADIUS; dx += SCENE_RADIUS) {
			for (int dz = -SCENE_RADIUS; dz <= SCENE_RADIUS; dz += SCENE_RADIUS) {
				if (!level.isLoaded(column.offset(dx, 0, dz))) {
					return false;
				}
			}
		}
		return true;
	}

	private static final int SCENE_RADIUS = 14;

	private static BlockPos ground(ServerLevel level, BlockPos column) {
		return new BlockPos(column.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()), column.getZ());
	}

	private static void put(ServerLevel level, BlockPos pos, BlockState state) {
		BlockPos at = ground(level, pos);
		if (level.getBlockState(at).canBeReplaced() && level.getFluidState(at).isEmpty()) {
			level.setBlock(at, state, Block.UPDATE_ALL);
		}
	}

	private static void debris(ServerLevel level, BlockPos center, int radius, RandomSource random, float density) {
		BlockState[] pieces = {DLBlocks.RUBBLE.defaultBlockState(), DLBlocks.ASH.defaultBlockState(), DLBlocks.CORRUGATED_METAL.defaultBlockState(),
			DLBlocks.RUSTED_SHEET_METAL.defaultBlockState()};
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz <= radius * radius && random.nextFloat() < density && (dx != 0 || dz != 0)) {
					put(level, center.offset(dx, 0, dz), pieces[random.nextInt(pieces.length)]);
				}
			}
		}
	}

	private static void crate(ServerLevel level, BlockPos pos, String table, RandomSource random) {
		BlockPos at = ground(level, pos);
		if (!level.getBlockState(at).canBeReplaced()) {
			return;
		}
		level.setBlock(at, DLContainers.MILITARY_CRATE.defaultBlockState(), Block.UPDATE_ALL);
		if (level.getBlockEntity(at) instanceof LootContainerBlockEntity container) {
			container.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, Deadlands.id(table)), random.nextLong());
		}
	}

	private static void wreck(ServerLevel level, BlockPos pos, VehicleKind kind, RandomSource random) {
		BlockPos at = ground(level, pos);
		VehicleEntity vehicle = VehicleSpawns.abandon(level, kind, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, random);
		if (vehicle != null) {
			vehicle.setFlag(VehicleEntity.FLAG_BURNED, true);
			vehicle.setFlag(VehicleEntity.FLAG_BATTERY, false);
			vehicle.setEngine(0.0F);
			vehicle.setTires(0.0F);
			vehicle.setBody(0.0F);
			vehicle.setFuel(0.0F);
		}
	}

	private static void announce(ServerLevel level, WorldEvents.Kind kind, BlockPos at) {
		MinecraftServer server = level.getServer();
		State state = state(server);
		long day = level.getDayTime() / 24000L + 1L;
		state.recent.add(new Recent(kind.id(), at.getX(), at.getZ(), day));
		if (ConfigManager.get().general.debugLogging) {
			Deadlands.LOGGER.info("World event {} at {} on day {}", kind.id(), at.toShortString(), day);
		}
		while (state.recent.size() > 6) {
			state.recent.removeFirst();
		}
		state.setDirty();
		Component where = kind == Kind.OUTAGE ? townName(level, at) : Component.translatable("event.deadlands.where", at.getX(), at.getZ());
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.displayClientMessage(Component.translatable("event.deadlands." + kind.id(), where), false);
			if (ServerPlayNetworking.canSend(player, WorldEventPayload.TYPE)) {
				ServerPlayNetworking.send(player, new WorldEventPayload(kind.id(), at.getX(), at.getZ()));
			}
		}
		DeadlandsWorldData data = DeadlandsWorldData.get(server);
		data.addChronicle(new ChronicleEntry(day, "chronicle.deadlands.event." + kind.id(), List.of(where.getString())), 64);
		data.setDirty();
	}

	private static Component townName(ServerLevel level, BlockPos at) {
		TownSite town = TownLookup.nearest(level, at, 64.0).orElse(null);
		return town == null ? Component.translatable("radio.deadlands.open_country") : Component.literal(TownIdentity.of(level.getSeed(), town).name());
	}

	public static @Nullable Kind byId(String id) {
		for (Kind kind : Kind.values()) {
			if (kind.id().equals(id)) {
				return kind;
			}
		}
		return null;
	}
}
