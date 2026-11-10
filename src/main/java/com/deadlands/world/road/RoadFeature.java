package com.deadlands.world.road;

import com.deadlands.block.AxisDecorBlock;
import com.deadlands.block.DecorKind;
import com.deadlands.block.RoadLineBlock;
import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import com.deadlands.registry.DLBlocks;
import com.deadlands.registry.DLDecor;
import com.deadlands.world.Hashing;
import com.deadlands.world.WorldgenView;
import com.deadlands.world.biome.DLBiomes;
import com.deadlands.world.town.TownCondition;
import com.deadlands.world.town.TownSite;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Lays the road network into one chunk at a time. For every column near a road it finds the
 * closest point of the road's polyline and builds by distance from the centre line.
 *
 * <ul>
 *   <li><b>Kinds</b> ({@link RoadKind}): four-lane highways (double yellow centre line, dashed
 *       lane lines, wide shoulders, electrical pylons), two-lane roads (dashed centre line, edge
 *       lines, telephone poles) and dirt roads (packed earth and gravel, wooden bridges).</li>
 *   <li><b>Terrain</b>: embankments, cuts through rises, tunnels through hills, and decks on
 *       pillars over water and valleys.</li>
 *   <li><b>Decay</b> ({@code world.roadDecay}): cracks, potholes, missing pavement, weeds and
 *       rubble; snow drifts in the frozen outskirts; mud and flooded dips in the marsh; fallen
 *       trees across forest roads; abandoned roadblocks of concrete barriers.</li>
 *   <li><b>Signs</b>: route signs leaving each town, speed signs, and infection warnings on the
 *       way into overrun towns.</li>
 * </ul>
 * Everything comes from deterministic hashes of positions and road keys, so a road looks the same
 * however its chunks are generated; only blocks inside the chunk are written. Inside a town
 * (closer to its centre than its gates) nothing is placed: the town's own streets take over.
 */
public class RoadFeature extends Feature<NoneFeatureConfiguration> {
	private static final int REACH = 7;
	private static final int POLE_SPACING = 36;
	private static final int PYLON_SPACING = 64;
	private static final int PILLAR_SPACING = 12;
	private static final int CLEARANCE = 4;
	private static final int MAX_CUT = 10;
	private static final int MAX_FILL = 5;
	private static final int EVENT_SPACING = 150;

	public RoadFeature() {
		super(NoneFeatureConfiguration.CODEC);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		DeadlandsConfig.World config = ConfigManager.get().world;
		if (!config.roadNetwork) {
			return false;
		}
		WorldGenLevel level = context.level();
		WorldgenView view = WorldgenView.of(level, context.chunkGenerator());
		RoadNetwork network = RoadNetwork.of(view).orElse(null);
		if (network == null) {
			return false;
		}
		int chunkX = SectionPos.blockToSectionCoord(context.origin().getX());
		int chunkZ = SectionPos.blockToSectionCoord(context.origin().getZ());
		List<RoadEdge> roads = network.roadsNear(chunkX, chunkZ);
		if (roads.isEmpty()) {
			return false;
		}
		int minX = chunkX << 4;
		int minZ = chunkZ << 4;

		// Closest road point per column: distance, which road, which point, which side.
		float[] distance = new float[256];
		Arrays.fill(distance, Float.MAX_VALUE);
		RoadEdge[] road = new RoadEdge[256];
		int[] point = new int[256];
		float[] lateral = new float[256];
		for (RoadEdge edge : roads) {
			for (int i = 0; i < edge.size(); i++) {
				float px = edge.x(i);
				float pz = edge.z(i);
				if (px < minX - REACH || px > minX + 16 + REACH || pz < minZ - REACH || pz > minZ + 16 + REACH) {
					continue;
				}
				int prev = Math.max(0, i - 1);
				int next = Math.min(edge.size() - 1, i + 1);
				float tx = edge.x(next) - edge.x(prev);
				float tz = edge.z(next) - edge.z(prev);
				float tn = (float) Math.hypot(tx, tz);
				if (tn < 1e-4F) {
					continue;
				}
				tx /= tn;
				tz /= tn;
				for (int x = (int) Math.floor(px - REACH); x <= (int) Math.ceil(px + REACH); x++) {
					for (int z = (int) Math.floor(pz - REACH); z <= (int) Math.ceil(pz + REACH); z++) {
						if (x < minX || x >= minX + 16 || z < minZ || z >= minZ + 16) {
							continue;
						}
						float dx = x + 0.5F - px;
						float dz = z + 0.5F - pz;
						float d = (float) Math.hypot(dx, dz);
						int column = (x - minX) * 16 + (z - minZ);
						if (d < distance[column] && !insideTown(edge, x, z)) {
							distance[column] = d;
							road[column] = edge;
							point[column] = i;
							lateral[column] = tx * dz - tz * dx;
						}
					}
				}
			}
		}

		boolean placed = false;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int column = 0; column < 256; column++) {
			RoadEdge edge = road[column];
			if (edge == null || distance[column] > edge.shoulderAt(point[column])) {
				continue;
			}
			int x = minX + column / 16;
			int z = minZ + column % 16;
			int i = point[column];
			int y = Math.round(edge.y(i));
			placed |= buildColumn(level, view, pos, edge, i, x, y, z, distance[column], lateral[column], config);
		}
		placeRoadside(level, view, roads, minX, minZ, config);
		return placed;
	}

	private static boolean insideTown(RoadEdge edge, int x, int z) {
		return within(edge.from(), x, z) || within(edge.to(), x, z);
	}

	private static boolean within(TownSite town, int x, int z) {
		double r = town.size().gateRadius() - 1.5;
		return Math.hypot(x - town.centerX(), z - town.centerZ()) < r;
	}

	private enum Climate {
		NORMAL, FROZEN, MARSH, FOREST, BURNED
	}

	private static Climate climate(WorldGenLevel level, BlockPos pos) {
		Holder<Biome> biome = level.getBiome(pos);
		if (biome.is(DLBiomes.FROZEN_OUTSKIRTS)) {
			return Climate.FROZEN;
		}
		if (biome.is(DLBiomes.FLOODED_MARSH)) {
			return Climate.MARSH;
		}
		if (biome.is(DLBiomes.BURNED_FOREST)) {
			return Climate.BURNED;
		}
		if (biome.is(DLBiomes.DEAD_FOREST) || biome.is(DLBiomes.DEAD_PINE_VALLEY) || biome.is(DLBiomes.RURAL_COUNTRYSIDE)) {
			return Climate.FOREST;
		}
		return Climate.NORMAL;
	}

	private boolean buildColumn(WorldGenLevel level, WorldgenView view, BlockPos.MutableBlockPos pos, RoadEdge edge, int i,
		int x, int y, int z, float distance, float lateral, DeadlandsConfig.World config) {
		if (y <= level.getMinY() + 2 || y >= level.getMaxY() - CLEARANCE - 2) {
			return false;
		}
		RoadKind kind = edge.kind();
		float paved = edge.pavedAt(i);
		int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
		int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
		boolean bridge = y - ground > MAX_FILL || surface > ground && surface >= y - 1;
		boolean tunnel = surface - y > MAX_CUT;
		boolean onPaving = distance <= paved;
		long hash = Hashing.of(view.seed(), x, z, 0x0AD5L);
		double decay = config.roadDecay;
		pos.set(x, y, z);
		Climate climate = climate(level, pos);

		// The road surface.
		BlockState top;
		if (bridge) {
			if (kind == RoadKind.DIRT) {
				top = DLBlocks.BOARDED_PLANKS.defaultBlockState();
			} else {
				top = onPaving ? surfaceBlock(edge, i, distance, lateral, paved, hash, decay * 0.6, false) : DLBlocks.CONCRETE.defaultBlockState();
			}
		} else if (kind == RoadKind.DIRT) {
			double roll = Hashing.unit(hash);
			top = !onPaving ? (roll < 0.5 ? Blocks.COARSE_DIRT.defaultBlockState() : DLBlocks.DRY_DIRT.defaultBlockState())
				: roll < decay * 0.25 ? DLBlocks.DEAD_GRASS_BLOCK.defaultBlockState()
				: roll < 0.65 ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
		} else if (onPaving) {
			top = surfaceBlock(edge, i, distance, lateral, paved, hash, decay, true);
		} else {
			top = Hashing.unit(hash) < 0.5 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
		}
		if (!bridge && climate == Climate.MARSH && Hashing.unit(Hashing.mix(hash)) < decay * 0.35) {
			top = Blocks.MUD.defaultBlockState();
		}
		boolean flooded = !bridge && climate == Climate.MARSH && y <= view.seaLevel() && Hashing.unit(Hashing.mix(hash ^ 0xF100DL)) < 0.45;
		level.setBlock(pos, flooded ? Blocks.WATER.defaultBlockState() : top, Block.UPDATE_CLIENTS);

		// Clear the way above (a cut through small rises, a tunnel bore through hills).
		int clearTo = tunnel ? y + CLEARANCE : Math.max(y + CLEARANCE, Math.min(surface, y + MAX_CUT));
		for (int k = y + 1; k <= clearTo; k++) {
			pos.set(x, k, z);
			BlockState above = level.getBlockState(pos);
			if (!above.isAir() && (above.getFluidState().isEmpty() || k <= y + CLEARANCE)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		if (tunnel && !onPaving) {
			// Tunnel walls: concrete lining at the edges of the bore.
			for (int k = y + 1; k <= y + CLEARANCE; k++) {
				pos.set(x, k, z);
				level.setBlock(pos, DLBlocks.STAINED_CONCRETE.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		if (!bridge && !tunnel && !flooded && climate == Climate.FROZEN && Hashing.unit(Hashing.mix(hash ^ 0x5A0L)) < 0.3 + decay * 0.3) {
			pos.set(x, y + 1, z);
			level.setBlock(pos, Blocks.SNOW.defaultBlockState(), Block.UPDATE_CLIENTS);
		}

		// What holds the road up.
		if (bridge) {
			boolean wooden = kind == RoadKind.DIRT;
			pos.set(x, y - 1, z);
			level.setBlock(pos, wooden ? DLBlocks.DEAD_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axisAt(edge, i))
				: DLBlocks.CONCRETE.defaultBlockState(), Block.UPDATE_CLIENTS);
			if (!onPaving && !wooden) {
				placeGuardrail(level, pos.set(x, y + 1, z), edge, i, lateral);
			}
			if (i % PILLAR_SPACING == 0 && distance <= 1.5F) {
				for (int k = y - 2; k > ground && k > level.getMinY(); k--) {
					pos.set(x, k, z);
					level.setBlock(pos, wooden ? DLBlocks.DEAD_LOG.defaultBlockState() : DLBlocks.CRACKED_CONCRETE.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		} else {
			for (int k = y - 1; k > y - 1 - MAX_FILL && k > level.getMinY(); k--) {
				pos.set(x, k, z);
				BlockState below = level.getBlockState(pos);
				if (below.isSolid() && below.getFluidState().isEmpty()) {
					break;
				}
				level.setBlock(pos, k == y - 1 ? Blocks.GRAVEL.defaultBlockState() : Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			if (!flooded && !tunnel) {
				placeObstacle(level, view, pos, edge, i, x, y, z, distance, lateral, paved, climate, config);
			}
		}
		return true;
	}

	/** Asphalt, its markings and its decay. */
	private static BlockState surfaceBlock(RoadEdge edge, int i, float distance, float lateral, float paved, long hash, double decay, boolean onGround) {
		double roll = Hashing.unit(hash);
		double patch = Hashing.unit(Hashing.of(0L, Math.floorDiv(i, 7), edge.key(), 0x5EC7L));
		boolean worn = patch < decay * 0.5;
		if (worn && onGround && roll < 0.18) {
			return roll < 0.06 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
		}
		if (worn && onGround && roll < 0.24) {
			return DLBlocks.DEAD_GRASS_BLOCK.defaultBlockState();
		}
		if (roll < decay * 0.04 && onGround) {
			return DLBlocks.RUBBLE.defaultBlockState();
		}
		boolean cracked = roll < decay * 0.55 || worn && roll < 0.6;
		Direction.Axis axis = axisAt(edge, i);
		boolean highway = paved > RoadKind.ROAD.paved() + 0.75F;
		float side = Math.abs(lateral);
		if (!cracked) {
			if (highway) {
				if (side < 0.6F) {
					return DLBlocks.ASPHALT_YELLOW_LINE.defaultBlockState().setValue(RoadLineBlock.AXIS, axis); // solid double centre line
				}
				if (Math.abs(side - paved / 2.0F) < 0.5F && (i / 4) % 2 == 0) {
					return DLBlocks.ASPHALT_WHITE_LINE.defaultBlockState().setValue(RoadLineBlock.AXIS, axis); // dashed lane line
				}
			} else if (side < 0.5F && (i / 4) % 2 == 0) {
				return DLBlocks.ASPHALT_YELLOW_LINE.defaultBlockState().setValue(RoadLineBlock.AXIS, axis);
			}
			if (distance > paved - 1.0F && distance <= paved && roll > 0.15) {
				return DLBlocks.ASPHALT_WHITE_LINE.defaultBlockState().setValue(RoadLineBlock.AXIS, axis);
			}
		}
		return cracked ? DLBlocks.CRACKED_ASPHALT.defaultBlockState() : DLBlocks.ASPHALT.defaultBlockState();
	}

	/**
	 * Fallen trees and abandoned roadblocks. Each road gets one chance per stretch of
	 * {@link #EVENT_SPACING} blocks, decided by the road key, so the obstacle is whole across chunks.
	 */
	private static void placeObstacle(WorldGenLevel level, WorldgenView view, BlockPos.MutableBlockPos pos, RoadEdge edge, int i, int x, int y, int z,
		float distance, float lateral, float paved, Climate climate, DeadlandsConfig.World config) {
		int stretch = i / EVENT_SPACING;
		int at = stretch * EVENT_SPACING + EVENT_SPACING / 2;
		if (i != at || at < 40 || at > edge.size() - 40) {
			return;
		}
		long roll = Hashing.of(view.seed(), stretch, edge.key(), 0x0B57L);
		double chance = Hashing.unit(roll);
		boolean forest = climate == Climate.FOREST || climate == Climate.BURNED;
		Direction.Axis along = axisAt(edge, i);
		pos.set(x, y + 1, z);
		if (forest && chance < 0.2 + config.roadDecay * 0.3) {
			if (distance <= paved + 1.0F) {
				Block log = climate == Climate.BURNED ? DLBlocks.CHARRED_LOG : DLBlocks.DEAD_LOG;
				Direction.Axis across = along == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
				level.setBlock(pos, log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, across), Block.UPDATE_CLIENTS);
			}
			return;
		}
		if (chance > 1.0 - config.roadDecay * 0.25 && distance <= paved) {
			// A roadblock: barriers across the paving with a gap for one car on one side.
			float gapCentre = (Hashing.unit(Hashing.mix(roll)) < 0.5 ? -1 : 1) * paved * 0.55F;
			if (Math.abs(lateral - gapCentre) > 1.2F) {
				Direction facing = along == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
				level.setBlock(pos, DLDecor.get(DecorKind.CONCRETE_BARRIER).defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing),
					Block.UPDATE_CLIENTS);
			}
		}
	}

	private static Direction.Axis axisAt(RoadEdge edge, int i) {
		int prev = Math.max(0, i - 2);
		int next = Math.min(edge.size() - 1, i + 2);
		return Math.abs(edge.x(next) - edge.x(prev)) >= Math.abs(edge.z(next) - edge.z(prev)) ? Direction.Axis.X : Direction.Axis.Z;
	}

	/**
	 * A guardrail on a bridge edge. The rail model sits on the side opposite its facing, so facing
	 * back towards the centre line puts the rail on the outer edge of the deck.
	 */
	private static void placeGuardrail(WorldGenLevel level, BlockPos pos, RoadEdge edge, int i, float lateral) {
		level.setBlock(pos, DLDecor.get(DecorKind.GUARDRAIL).defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, inward(edge, i, lateral)),
			Block.UPDATE_CLIENTS);
	}

	/** The cardinal direction from a column at {@code lateral} back toward the centre line. */
	private static Direction inward(RoadEdge edge, int i, float lateral) {
		int prev = Math.max(0, i - 2);
		int next = Math.min(edge.size() - 1, i + 2);
		float tx = edge.x(next) - edge.x(prev);
		float tz = edge.z(next) - edge.z(prev);
		// A column lies at +lateral along n = (-tz, tx); the centre line is back along -n.
		float s = Math.signum(lateral);
		float ix = tz * s;
		float iz = -tx * s;
		return Math.abs(ix) >= Math.abs(iz) ? (ix >= 0 ? Direction.EAST : Direction.WEST) : (iz >= 0 ? Direction.SOUTH : Direction.NORTH);
	}

	/** Poles, pylons and signs along the roads, out in the country. */
	private static void placeRoadside(WorldGenLevel level, WorldgenView view, List<RoadEdge> roads, int minX, int minZ, DeadlandsConfig.World config) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (RoadEdge edge : roads) {
			RoadKind kind = edge.kind();
			if (config.roadsideUtilities && kind != RoadKind.DIRT) {
				int spacing = kind == RoadKind.HIGHWAY ? PYLON_SPACING : POLE_SPACING;
				for (int i = spacing / 2; i < edge.size() - spacing / 2; i += spacing) {
					float offset = edge.shoulderAt(i) + (kind == RoadKind.HIGHWAY ? 6.0F : 1.4F);
					Spot spot = spot(edge, i, offset);
					if (spot != null && inChunk(spot.x, spot.z, minX, minZ) && !insideTown(edge, spot.x, spot.z)) {
						if (kind == RoadKind.HIGHWAY) {
							placePylon(level, pos, edge, i, spot);
						} else {
							placePole(level, pos, edge, i, spot);
						}
					}
				}
			}
			placeSigns(level, view, pos, edge, minX, minZ);
			if (config.abandonedVehicles) {
				placeVehicles(level, view, edge, minX, minZ);
			}
		}
	}

	private record Spot(int x, int z, float tx, float tz) {
	}

	/** The column {@code offset} blocks to the left of the road at point {@code i} (negative: right). */
	private static Spot spot(RoadEdge edge, int i, float offset) {
		int prev = Math.max(0, i - 2);
		int next = Math.min(edge.size() - 1, i + 2);
		float tx = edge.x(next) - edge.x(prev);
		float tz = edge.z(next) - edge.z(prev);
		float tn = (float) Math.hypot(tx, tz);
		if (tn < 1e-4F) {
			return null;
		}
		tx /= tn;
		tz /= tn;
		return new Spot(Math.round(edge.x(i) - tz * offset), Math.round(edge.z(i) + tx * offset), tx, tz);
	}

	private static boolean inChunk(int x, int z, int minX, int minZ) {
		return x >= minX && x < minX + 16 && z >= minZ && z < minZ + 16;
	}

	/** Dry ground close to the road's height, or -1. */
	private static int groundNear(WorldGenLevel level, RoadEdge edge, int i, int x, int z) {
		int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
		int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
		if (surface != ground || Math.abs(ground - Math.round(edge.y(i)) - 1) > 4) {
			return -1;
		}
		return ground;
	}

	/** A wooden telephone pole whose crossarm runs across the road. */
	private static void placePole(WorldGenLevel level, BlockPos.MutableBlockPos pos, RoadEdge edge, int i, Spot spot) {
		int ground = groundNear(level, edge, i, spot.x, spot.z);
		if (ground < 0) {
			return;
		}
		BlockState pole = DLDecor.get(DecorKind.UTILITY_POLE).defaultBlockState();
		BlockState arm = DLDecor.get(DecorKind.UTILITY_POLE_CROSSARM).defaultBlockState();
		Direction.Axis across = Math.abs(spot.tx) >= Math.abs(spot.tz) ? Direction.Axis.Z : Direction.Axis.X;
		for (int k = 0; k < 7; k++) {
			pos.set(spot.x, ground + k, spot.z);
			level.setBlock(pos, k == 6 ? arm.setValue(AxisDecorBlock.AXIS, across) : pole, Block.UPDATE_CLIENTS);
		}
	}

	/** A rusted steel lattice pylon of iron bars, with arms across the line it carries. */
	private static void placePylon(WorldGenLevel level, BlockPos.MutableBlockPos pos, RoadEdge edge, int i, Spot spot) {
		int ground = groundNear(level, edge, i, spot.x, spot.z);
		if (ground < 0) {
			return;
		}
		BlockState bars = Blocks.IRON_BARS.defaultBlockState();
		for (int k = 0; k < 14; k++) {
			pos.set(spot.x, ground + k, spot.z);
			level.setBlock(pos, bars, Block.UPDATE_CLIENTS);
		}
		// Arms across the road direction, on one block column only (chunk-safe within the column's
		// chunk: arm blocks outside the chunk are skipped).
		int ax = Math.abs(spot.tx) >= Math.abs(spot.tz) ? 0 : 1;
		int az = 1 - ax;
		for (int w = -3; w <= 3; w++) {
			int x = spot.x + ax * w;
			int z = spot.z + az * w;
			if (w == 0 || (x >> 4) != (spot.x >> 4) || (z >> 4) != (spot.z >> 4)) {
				continue;
			}
			pos.set(x, ground + 12, z);
			level.setBlock(pos, bars, Block.UPDATE_CLIENTS);
			if (Math.abs(w) == 3) {
				pos.set(x, ground + 11, z);
				level.setBlock(pos, Blocks.IRON_CHAIN.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
	}

	/**
	 * Abandoned vehicles: every 90 blocks along a road there is about a one in three chance of one,
	 * left in a lane or run off into the ditch, pointing more or less along the road. Decided by
	 * hashes of the spot, so a vehicle is placed once, by the chunk it stands in.
	 */
	private static void placeVehicles(WorldGenLevel level, WorldgenView view, RoadEdge edge, int minX, int minZ) {
		int n = edge.size();
		for (int i = 70; i < n - 70; i += 90) {
			long hash = Hashing.of(view.seed(), Math.round(edge.x(i)), Math.round(edge.z(i)), 0x76656869L);
			if (Hashing.unit(hash) >= 0.34) {
				continue;
			}
			boolean ditched = Hashing.unit(Hashing.mix(hash)) < 0.45;
			float side = Hashing.unit(Hashing.mix(hash ^ 0x51L)) < 0.5 ? 1.0F : -1.0F;
			float offset = side * (ditched ? edge.shoulderAt(i) + 1.5F : Math.min(1.3F, edge.pavedAt(i) * 0.5F));
			Spot spot = spot(edge, i, offset);
			if (spot == null || !inChunk(spot.x, spot.z, minX, minZ) || insideTown(edge, spot.x, spot.z)) {
				continue;
			}
			int ground = groundNear(level, edge, i, spot.x, spot.z);
			if (ground < 0) {
				continue;
			}
			net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(hash);
			float along = (float) (Math.atan2(-spot.tx, spot.tz) * 180.0 / Math.PI) + (side > 0.0F ? 0.0F : 180.0F);
			float yaw = along + (ditched ? random.nextFloat() * 70.0F - 35.0F : random.nextFloat() * 16.0F - 8.0F);
			com.deadlands.vehicle.VehicleSpawns.abandon(level, com.deadlands.vehicle.VehicleSpawns.pick(random), spot.x + 0.5, ground, spot.z + 0.5, yaw, random);
		}
	}

	/** Route signs leaving each town, speed signs along the way, warnings before overrun towns. */
	private static void placeSigns(WorldGenLevel level, WorldgenView view, BlockPos.MutableBlockPos pos, RoadEdge edge, int minX, int minZ) {
		int n = edge.size();
		if (n < 120) {
			return;
		}
		int[] points = {36, n - 37, n / 2};
		for (int k = 0; k < points.length; k++) {
			int i = points[k];
			// Right-hand side for traffic heading away from the nearer town.
			boolean outbound = k != 1;
			float offset = -(edge.shoulderAt(i) + 1.2F) * (outbound ? 1 : -1);
			Spot spot = spot(edge, i, offset);
			if (spot == null || !inChunk(spot.x, spot.z, minX, minZ) || insideTown(edge, spot.x, spot.z)) {
				continue;
			}
			int ground = groundNear(level, edge, i, spot.x, spot.z);
			if (ground < 0) {
				continue;
			}
			DecorKind kind;
			if (k == 2) {
				kind = DecorKind.ROAD_SIGN_SPEED;
			} else {
				TownSite ahead = k == 0 ? edge.to() : edge.from();
				kind = ahead.condition() == TownCondition.OVERRUN ? DecorKind.ROAD_SIGN_WARNING : DecorKind.ROAD_SIGN_ROUTE;
			}
			// Face the traffic that should read it.
			float fx = outbound ? -spot.tx : spot.tx;
			float fz = outbound ? -spot.tz : spot.tz;
			Direction facing = Math.abs(fx) >= Math.abs(fz) ? (fx >= 0 ? Direction.EAST : Direction.WEST) : (fz >= 0 ? Direction.SOUTH : Direction.NORTH);
			pos.set(spot.x, ground, spot.z);
			if (Hashing.unit(Hashing.of(view.seed(), spot.x, spot.z, 0x5160L)) < 0.15) {
				continue; // this one was knocked down long ago
			}
			level.setBlock(pos, DLDecor.get(kind).defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing), Block.UPDATE_CLIENTS);
		}
	}
}
