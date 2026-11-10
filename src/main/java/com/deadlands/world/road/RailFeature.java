package com.deadlands.world.road;

import com.deadlands.block.DecorKind;
import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import com.deadlands.registry.DLBlocks;
import com.deadlands.registry.DLDecor;
import com.deadlands.world.Hashing;
import com.deadlands.world.WorldgenView;
import com.deadlands.world.town.TownSite;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Lays railway lines ({@link RailNetwork}) one chunk at a time: a gravel ballast bed with a
 * rusted track down the middle (sections missing, more with {@code world.roadDecay}), cuttings and
 * tunnels through hills, embankments, and steel trestles on pillars over water and valleys. Each
 * end has a station platform and a buffer stop. Runs before the road feature, so where a road
 * crosses a line the road wins (a level crossing), and before structures, so buildings stand on
 * top of anything that would cross them.
 */
public class RailFeature extends Feature<NoneFeatureConfiguration> {
	private static final int REACH = 5;
	private static final float BALLAST = 2.0F;
	private static final float PLATFORM = 4.6F;
	private static final int PLATFORM_LENGTH = 16;
	private static final int CLEARANCE = 4;
	private static final int MAX_CUT = 9;
	private static final int MAX_FILL = 4;
	private static final int PILLAR_SPACING = 10;

	public RailFeature() {
		super(NoneFeatureConfiguration.CODEC);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		DeadlandsConfig.World config = ConfigManager.get().world;
		if (!config.roadNetwork || !config.railways) {
			return false;
		}
		WorldGenLevel level = context.level();
		WorldgenView view = WorldgenView.of(level, context.chunkGenerator());
		RailNetwork network = RailNetwork.of(view).orElse(null);
		if (network == null) {
			return false;
		}
		int chunkX = SectionPos.blockToSectionCoord(context.origin().getX());
		int chunkZ = SectionPos.blockToSectionCoord(context.origin().getZ());
		List<RailEdge> lines = network.railsNear(chunkX, chunkZ);
		if (lines.isEmpty()) {
			return false;
		}
		int minX = chunkX << 4;
		int minZ = chunkZ << 4;
		float[] distance = new float[256];
		Arrays.fill(distance, Float.MAX_VALUE);
		RailEdge[] line = new RailEdge[256];
		int[] point = new int[256];
		float[] lateral = new float[256];
		for (RailEdge edge : lines) {
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
							line[column] = edge;
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
			RailEdge edge = line[column];
			if (edge == null) {
				continue;
			}
			int i = point[column];
			boolean station = i < PLATFORM_LENGTH || i >= edge.size() - PLATFORM_LENGTH;
			float d = distance[column];
			if (d > BALLAST && !(station && lateral[column] > 0 && d <= PLATFORM)) {
				continue;
			}
			placed |= buildColumn(level, view, pos, edge, i, minX + column / 16, minZ + column % 16, d, lateral[column], station, config);
		}
		return placed;
	}

	private static boolean insideTown(RailEdge edge, int x, int z) {
		return within(edge.from(), x, z) || within(edge.to(), x, z);
	}

	private static boolean within(TownSite town, int x, int z) {
		return Math.hypot(x - town.centerX(), z - town.centerZ()) < town.size().gateRadius() + 4;
	}

	private boolean buildColumn(WorldGenLevel level, WorldgenView view, BlockPos.MutableBlockPos pos, RailEdge edge, int i, int x, int z,
		float distance, float lateral, boolean station, DeadlandsConfig.World config) {
		int y = Math.round(edge.y(i));
		if (y <= level.getMinY() + 2 || y >= level.getMaxY() - CLEARANCE - 2) {
			return false;
		}
		int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
		int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
		boolean bridge = y - ground > MAX_FILL || surface > ground && surface >= y - 1;
		boolean tunnel = surface - y > MAX_CUT;
		long hash = Hashing.of(view.seed(), x, z, 0x7A1FL);
		boolean platform = station && distance > BALLAST;

		pos.set(x, y, z);
		if (platform) {
			level.setBlock(pos, DLBlocks.SIDEWALK.defaultBlockState(), Block.UPDATE_CLIENTS);
			pos.set(x, y + 1, z);
			level.setBlock(pos, DLBlocks.SIDEWALK_SLAB.defaultBlockState(), Block.UPDATE_CLIENTS);
		} else if (bridge) {
			level.setBlock(pos, distance <= 1.6F ? DLBlocks.DIAMOND_PLATE.defaultBlockState() : DLBlocks.RUSTED_SHEET_METAL.defaultBlockState(),
				Block.UPDATE_CLIENTS);
		} else {
			level.setBlock(pos, Hashing.unit(hash) < 0.15 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState(),
				Block.UPDATE_CLIENTS);
		}

		// Clear above (cutting or tunnel bore).
		int clearFrom = platform ? y + 2 : y + 1;
		int clearTo = tunnel ? y + CLEARANCE : Math.max(y + CLEARANCE, Math.min(surface, y + MAX_CUT));
		for (int k = clearFrom; k <= clearTo; k++) {
			pos.set(x, k, z);
			BlockState above = level.getBlockState(pos);
			if (!above.isAir() && (above.getFluidState().isEmpty() || k <= y + CLEARANCE)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		if (tunnel && distance > BALLAST - 0.6F) {
			for (int k = y + 1; k <= y + CLEARANCE; k++) {
				pos.set(x, k, z);
				level.setBlock(pos, DLBlocks.STAINED_CONCRETE.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}

		// The track: one rail down the centre, gaps where it has been torn up or rusted through.
		if (!platform && distance <= 0.71F && Hashing.unit(Hashing.mix(hash)) >= config.roadDecay * 0.3) {
			pos.set(x, y + 1, z);
			boolean end = i == 0 || i == edge.size() - 1;
			if (end) {
				Direction along = axis(edge, i) == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
				level.setBlock(pos, DLDecor.get(DecorKind.CONCRETE_BARRIER).defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, along),
					Block.UPDATE_CLIENTS);
			} else {
				RailShape shape = axis(edge, i) == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
				level.setBlock(pos, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, shape), Block.UPDATE_CLIENTS);
			}
		}

		// Support: trestle pillars over gaps, embankment fill on land.
		if (bridge) {
			if (i % PILLAR_SPACING == 0 && distance <= 1.0F) {
				for (int k = y - 1; k > ground && k > level.getMinY(); k--) {
					pos.set(x, k, z);
					level.setBlock(pos, DLBlocks.RUSTED_SHEET_METAL.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		} else {
			for (int k = y - 1; k > y - 1 - MAX_FILL && k > level.getMinY(); k--) {
				pos.set(x, k, z);
				BlockState below = level.getBlockState(pos);
				if (below.isSolid() && below.getFluidState().isEmpty()) {
					break;
				}
				level.setBlock(pos, Blocks.GRAVEL.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		return true;
	}

	private static Direction.Axis axis(RailEdge edge, int i) {
		int prev = Math.max(0, i - 2);
		int next = Math.min(edge.size() - 1, i + 2);
		return Math.abs(edge.x(next) - edge.x(prev)) >= Math.abs(edge.z(next) - edge.z(prev)) ? Direction.Axis.X : Direction.Axis.Z;
	}
}
