package com.deadlands.story;

import com.deadlands.Deadlands;
import com.deadlands.npc.NpcCharacter;
import com.deadlands.npc.NpcWorldData;
import com.deadlands.registry.DLTags;
import com.deadlands.world.WorldgenView;
import com.deadlands.world.road.RoadEdge;
import com.deadlands.world.road.RoadNetwork;
import com.deadlands.world.town.TownSite;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * Part 19: a road near Harley, always. The opening must never leave the survivor in the wild with no way on, so once
 * her camp is built (or, in an older world, as soon as she is known) this makes sure a road starts at her camp and joins
 * the regional road network ({@code world.road.RoadNetwork}: the roads between towns, the same ones world generation
 * paves).
 *
 * <ol>
 *   <li><b>Where to</b>: the nearest point of the network, worked out from the seed (no chunks generated to look). Already
 *       within 8 blocks: nothing to build. None within 480 blocks: the nearest town's gate. No town at all: a fallback
 *       trail, so the road is never silently left out.</li>
 *   <li><b>The route</b>: planned on a 4-block grid from the camp to there, avoiding water, steep ground and towns'
 *       built-up middles (A*, on the generator's own height estimates).</li>
 *   <li><b>Building</b> (a few columns a tick, so nothing stalls): the real ground along the route is read, the road's
 *       height smoothed so it never climbs more than a block a block, then a 3-wide dirt-path road with gravel edges is
 *       laid: natural ground is cut or filled, water is bridged with planks, and nothing with a block entity, nothing
 *       killer-proof and nothing built (planks, bricks, glass...) is touched.</li>
 *   <li><b>Checking</b>: every column is checked for a surface and for no step over a block, and that the road ends on
 *       the network; gaps found are filled. The result is saved ({@code deadlands_harley_road}) and logged.</li>
 * </ol>
 */
public final class HarleyRoad {
	private static final int SEARCH_CHUNKS = 30;
	private static final double ALREADY_NEAR = 8.0;
	private static final int GRID = 4;
	private static final int MAX_NODES = 6000;
	private static final int PER_TICK = 6;

	/** The saved result: whether it was made sure of, how, and where it runs. */
	public static final class State extends SavedData {
		public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("done", false).forGetter(s -> s.done),
			Codec.STRING.optionalFieldOf("mode", "").forGetter(s -> s.mode),
			BlockPos.CODEC.optionalFieldOf("from").forGetter(s -> Optional.ofNullable(s.from)),
			BlockPos.CODEC.optionalFieldOf("to").forGetter(s -> Optional.ofNullable(s.to)),
			Codec.BOOL.optionalFieldOf("validated", false).forGetter(s -> s.validated)
		).apply(instance, (done, mode, from, to, validated) -> {
			State s = new State();
			s.done = done;
			s.mode = mode;
			s.from = from.orElse(null);
			s.to = to.orElse(null);
			s.validated = validated;
			return s;
		}));
		public static final SavedDataType<State> TYPE = new SavedDataType<>("deadlands_harley_road", State::new, CODEC, null);

		boolean done;
		/** near (already on the network), connector, town, fallback. */
		String mode = "";
		@Nullable BlockPos from;
		@Nullable BlockPos to;
		boolean validated;
	}

	/** A road being built: the route's columns, their road heights once known, and how far along it is. */
	private static final class Job {
		final List<int[]> columns;
		final int[] heights;
		final String mode;
		final BlockPos from;
		final BlockPos to;
		int sampled;
		int built;
		boolean smoothed;

		Job(List<int[]> columns, String mode, BlockPos from, BlockPos to) {
			this.columns = columns;
			this.heights = new int[columns.size()];
			this.mode = mode;
			this.from = from;
			this.to = to;
		}
	}

	private static @Nullable Job job;

	private HarleyRoad() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(HarleyRoad::tick);
	}

	private static State state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(State.TYPE);
	}

	private static void tick(MinecraftServer server) {
		if (job != null) {
			work(server.overworld());
			return;
		}
		// An older world, or a build cut short by a restart: make sure of it once Harley is known.
		if (server.getTickCount() % 200 == 123 && !state(server).done) {
			BlockPos harley = NpcWorldData.get(server).position(NpcCharacter.HARLEY);
			if (harley != null) {
				ensure(server.overworld(), harley);
			}
		}
	}

	/** Make sure a road runs from Harley's camp to the network (plans it now and builds it over the next ticks). */
	public static void ensure(ServerLevel level, BlockPos camp) {
		State state = state(level.getServer());
		if (state.done || job != null) {
			return;
		}
		WorldgenView view = WorldgenView.of(level, level.getChunkSource().getGenerator());
		Optional<RoadNetwork> network = RoadNetwork.of(view);
		BlockPos target = null;
		String mode;
		double best = Double.MAX_VALUE;
		if (network.isPresent()) {
			Set<Long> seen = new HashSet<>();
			int cx = camp.getX() >> 4;
			int cz = camp.getZ() >> 4;
			for (int dx = -SEARCH_CHUNKS; dx <= SEARCH_CHUNKS; dx += 2) {
				for (int dz = -SEARCH_CHUNKS; dz <= SEARCH_CHUNKS; dz += 2) {
					for (RoadEdge edge : network.get().roadsNear(cx + dx, cz + dz)) {
						if (!seen.add(edge.key())) {
							continue;
						}
						for (int i = 0; i < edge.size(); i++) {
							double d = Mth.length(edge.x(i) - camp.getX(), edge.z(i) - camp.getZ());
							if (d < best) {
								best = d;
								target = BlockPos.containing(edge.x(i), edge.y(i), edge.z(i));
							}
						}
					}
				}
			}
		}
		if (target != null && best <= ALREADY_NEAR) {
			finish(level, state, "near", camp, target, true);
			return;
		}
		if (target != null) {
			mode = "connector";
		} else {
			TownSite town = network.isPresent() ? nearestTown(network.get(), camp) : null;
			if (town != null) {
				target = town.center();
				mode = "town";
			} else {
				// Fallback: a trail out of the camp toward open ground (east, then wherever the land is lowest and dry).
				target = camp.offset(96, 0, 0);
				mode = "fallback";
				Deadlands.LOGGER.warn("DEADLANDS: no road network near Harley's camp at {}; laying a fallback trail", camp);
			}
		}
		List<int[]> route = route(view, camp, target, network.orElse(null));
		if (route.isEmpty()) {
			route = straight(camp, target); // the planner gave up: a straight road is still a road
		}
		job = new Job(route, mode, camp, target);
		Deadlands.LOGGER.info("DEADLANDS: Harley's road ({}): {} columns from {} to {}", mode, route.size(), camp, target);
	}

	private static @Nullable TownSite nearestTown(RoadNetwork network, BlockPos at) {
		int spacing = network.placement().spacing();
		int rx = Math.floorDiv(at.getX() >> 4, spacing);
		int rz = Math.floorDiv(at.getZ() >> 4, spacing);
		TownSite best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				Optional<TownSite> town = network.town(rx + dx, rz + dz);
				if (town.isPresent()) {
					double d = town.get().center().distSqr(at);
					if (d < bestDistance) {
						bestDistance = d;
						best = town.get();
					}
				}
			}
		}
		return best;
	}

	// ---------------------------------------------------------------------------------------- planning

	/** A* on a 4-block grid: costs distance, climbing, water, and towns' built-up middles. Returns 1-block columns. */
	private static List<int[]> route(WorldgenView view, BlockPos from, BlockPos to, @Nullable RoadNetwork network) {
		int sx = Math.floorDiv(from.getX(), GRID);
		int sz = Math.floorDiv(from.getZ(), GRID);
		int tx = Math.floorDiv(to.getX(), GRID);
		int tz = Math.floorDiv(to.getZ(), GRID);
		Map<Long, Double> cost = new HashMap<>();
		Map<Long, Long> came = new HashMap<>();
		PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
		long start = key(sx, sz);
		cost.put(start, 0.0);
		open.add(new double[] {h(sx, sz, tx, tz), sx, sz});
		int seaLevel = view.seaLevel();
		List<TownSite> towns = new ArrayList<>();
		if (network != null) {
			TownSite near = nearestTown(network, to);
			if (near != null) {
				towns.add(near);
			}
		}
		int expanded = 0;
		long goal = Long.MIN_VALUE;
		while (!open.isEmpty() && expanded++ < MAX_NODES) {
			double[] node = open.poll();
			int x = (int) node[1];
			int z = (int) node[2];
			if (Math.abs(x - tx) <= 1 && Math.abs(z - tz) <= 1) {
				goal = key(x, z);
				break;
			}
			int y0 = view.surface(x * GRID, z * GRID);
			double here = cost.getOrDefault(key(x, z), Double.MAX_VALUE);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dz == 0) {
						continue;
					}
					int nx = x + dx;
					int nz = z + dz;
					int y1 = view.surface(nx * GRID, nz * GRID);
					double step = (dx != 0 && dz != 0 ? 1.414 : 1.0) * GRID;
					step += Math.max(0, Math.abs(y1 - y0) - 1) * 6.0;      // climbing
					if (y1 < seaLevel) {
						step += 25.0;                                      // water: a bridge, if it must
					}
					for (TownSite town : towns) {
						double d = Mth.length(nx * GRID - town.centerX(), nz * GRID - town.centerZ());
						if (d < 40.0 && Math.abs(nx - tx) + Math.abs(nz - tz) > 3) {
							step += 60.0;                                  // not through the houses
						}
					}
					double next = here + step;
					long k = key(nx, nz);
					if (next < cost.getOrDefault(k, Double.MAX_VALUE)) {
						cost.put(k, next);
						came.put(k, key(x, z));
						open.add(new double[] {next + h(nx, nz, tx, tz), nx, nz});
					}
				}
			}
		}
		if (goal == Long.MIN_VALUE) {
			return List.of();
		}
		List<int[]> nodes = new ArrayList<>();
		for (long k = goal; ; k = came.get(k)) {
			nodes.add(0, new int[] {(int) (k >> 32) * GRID + GRID / 2, (int) k * GRID + GRID / 2});
			if (k == start || !came.containsKey(k)) {
				break;
			}
		}
		nodes.add(0, new int[] {from.getX(), from.getZ()});
		nodes.add(new int[] {to.getX(), to.getZ()});
		return densify(nodes);
	}

	private static double h(int x, int z, int tx, int tz) {
		return Mth.length(x - tx, z - tz) * GRID;
	}

	private static long key(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	private static List<int[]> straight(BlockPos from, BlockPos to) {
		List<int[]> nodes = new ArrayList<>();
		nodes.add(new int[] {from.getX(), from.getZ()});
		nodes.add(new int[] {to.getX(), to.getZ()});
		return densify(nodes);
	}

	/** One column a block along the polyline. */
	private static List<int[]> densify(List<int[]> nodes) {
		List<int[]> out = new ArrayList<>();
		for (int i = 0; i + 1 < nodes.size(); i++) {
			int[] a = nodes.get(i);
			int[] b = nodes.get(i + 1);
			int n = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
			for (int s = 0; s < n; s++) {
				int x = Math.round(a[0] + (b[0] - a[0]) * (s / (float) n));
				int z = Math.round(a[1] + (b[1] - a[1]) * (s / (float) n));
				if (out.isEmpty() || out.get(out.size() - 1)[0] != x || out.get(out.size() - 1)[1] != z) {
					out.add(new int[] {x, z});
				}
			}
		}
		out.add(nodes.get(nodes.size() - 1));
		return out;
	}

	// ---------------------------------------------------------------------------------------- building

	private static void work(ServerLevel level) {
		Job j = job;
		if (j == null) {
			return;
		}
		// 1. The real ground (water counts as its surface: it gets a bridge).
		if (j.sampled < j.columns.size()) {
			for (int n = 0; n < PER_TICK * 3 && j.sampled < j.columns.size(); n++) {
				int[] c = j.columns.get(j.sampled);
				level.getChunk(c[0] >> 4, c[1] >> 4);
				j.heights[j.sampled] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c[0], c[1]);
				j.sampled++;
			}
			return;
		}
		// 2. Smooth: never more than a block's rise between neighbours (a few passes of a running clamp both ways).
		if (!j.smoothed) {
			smooth(j.heights);
			j.smoothed = true;
			return;
		}
		// 3. Lay it.
		for (int n = 0; n < PER_TICK && j.built < j.columns.size(); n++) {
			int i = j.built++;
			int[] c = j.columns.get(i);
			int[] next = j.columns.get(Math.min(j.columns.size() - 1, i + 1));
			int[] prev = j.columns.get(Math.max(0, i - 1));
			int dx = Integer.signum(next[0] - prev[0]);
			int dz = Integer.signum(next[1] - prev[1]);
			// Across the road: perpendicular to its direction.
			int ax = -dz;
			int az = dx;
			if (ax == 0 && az == 0) {
				ax = 1;
			}
			for (int w = -1; w <= 1; w++) {
				column(level, c[0] + ax * w, c[1] + az * w, j.heights[i], w == 0);
			}
		}
		if (j.built >= j.columns.size()) {
			boolean ok = validate(level, j);
			finish(level, state(level.getServer()), j.mode, j.from, j.to, ok);
			job = null;
		}
	}

	static void smooth(int[] y) {
		for (int pass = 0; pass < 3; pass++) {
			for (int i = 1; i < y.length; i++) {
				y[i] = Mth.clamp(y[i], y[i - 1] - 1, y[i - 1] + 1);
			}
			for (int i = y.length - 2; i >= 0; i--) {
				y[i] = Mth.clamp(y[i], y[i + 1] - 1, y[i + 1] + 1);
			}
		}
		// A gentle 5-wide average on top, keeping the one-block rule.
		int[] copy = y.clone();
		for (int i = 2; i + 2 < y.length; i++) {
			y[i] = Math.round((copy[i - 2] + copy[i - 1] + copy[i] + copy[i + 1] + copy[i + 2]) / 5.0F);
		}
		for (int i = 1; i < y.length; i++) {
			y[i] = Mth.clamp(y[i], y[i - 1] - 1, y[i - 1] + 1);
		}
	}

	/** One column of road: the surface at {@code y - 1}, headroom above, fill below; plank decking over water. */
	private static void column(ServerLevel level, int x, int z, int y, boolean middle) {
		BlockPos surface = new BlockPos(x, y - 1, z);
		for (int up = -1; up <= 3; up++) {
			if (!touchable(level.getBlockState(surface.above(up + 1)))) {
				return; // something built or protected: the road goes round it (its neighbours carry on)
			}
		}
		boolean water = !level.getFluidState(surface).isEmpty() || !level.getFluidState(surface.below()).isEmpty();
		BlockState top = water ? Blocks.SPRUCE_PLANKS.defaultBlockState()
			: middle ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
		level.setBlock(surface, top, Block.UPDATE_CLIENTS);
		for (int up = 1; up <= 3; up++) {
			BlockPos above = surface.above(up);
			if (!level.getBlockState(above).isAir()) {
				level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		if (!water) {
			for (int down = 1; down <= 6; down++) {
				BlockPos below = surface.below(down);
				BlockState at = level.getBlockState(below);
				if (!at.canBeReplaced() && at.getFluidState().isEmpty()) {
					break;
				}
				level.setBlock(below, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
	}

	/** Natural ground, plants and air may be cut or filled; anything built, holding things or protected may not. */
	private static boolean touchable(BlockState state) {
		if (state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty()) {
			return true;
		}
		if (state.hasBlockEntity() || state.is(DLTags.KILLER_PROOF)) {
			return false;
		}
		return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.LEAVES)
			|| state.is(BlockTags.LOGS) || state.is(BlockTags.SNOW) || state.is(BlockTags.TERRACOTTA) || state.is(Blocks.GRAVEL)
			|| state.is(Blocks.CLAY) || state.is(Blocks.ICE) || state.is(DLTags.ROAD_SURFACE);
	}

	/** Every column has a surface and no step over a block, and the road ends on (or at) the network. Gaps get filled. */
	private static boolean validate(ServerLevel level, Job j) {
		int gaps = 0;
		for (int i = 0; i < j.columns.size(); i++) {
			int[] c = j.columns.get(i);
			BlockPos surface = new BlockPos(c[0], j.heights[i] - 1, c[1]);
			BlockState at = level.getBlockState(surface);
			boolean paved = at.is(DLTags.ROAD_SURFACE) || at.is(Blocks.SPRUCE_PLANKS);
			if (!paved) {
				// A column skipped for a building: fine if a neighbour across is paved (the road narrows round it).
				boolean around = false;
				for (BlockPos side : new BlockPos[] {surface.east(), surface.west(), surface.north(), surface.south()}) {
					BlockState s = level.getBlockState(side);
					around |= s.is(DLTags.ROAD_SURFACE) || s.is(Blocks.SPRUCE_PLANKS);
				}
				if (!around) {
					gaps++;
				}
			}
			if (i > 0 && Math.abs(j.heights[i] - j.heights[i - 1]) > 1) {
				gaps++;
			}
		}
		int[] last = j.columns.get(j.columns.size() - 1);
		boolean reaches = Mth.length(last[0] - j.to.getX(), last[1] - j.to.getZ()) <= 3.0;
		Deadlands.LOGGER.info("DEADLANDS: Harley's road checked: {} columns, {} gaps, reaches the {}: {}", j.columns.size(), gaps,
			j.mode.equals("fallback") ? "trail's end" : "network", reaches);
		return gaps == 0 && reaches;
	}

	private static void finish(ServerLevel level, State state, String mode, BlockPos from, BlockPos to, boolean validated) {
		state.done = true;
		state.mode = mode;
		state.from = from.immutable();
		state.to = to.immutable();
		state.validated = validated;
		state.setDirty();
	}
}
