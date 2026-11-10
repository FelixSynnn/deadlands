package com.deadlands.world.town;

import com.deadlands.block.DecorKind;
import com.deadlands.block.RoadLineBlock;
import com.deadlands.registry.DLBlocks;
import com.deadlands.registry.DLDecor;
import com.deadlands.world.DLWorldgen;
import com.deadlands.world.Hashing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * One straight town street: five blocks of asphalt with a dashed centre line between one-block
 * sidewalks, following a precomputed, smoothed height profile (saved with the piece, so every
 * chunk draws the same street). Embankments fill dips, the space above is cleared, and dead street
 * lamps stand along the sidewalks. Blood, rubble and cracks follow the town's condition.
 *
 * <p>The verges on both sides are levelled to street height (cut banks cleared, dips filled), so
 * buildings set back from the street are not walled off by a hillside or cut off by a ditch. Verges
 * skip the area of other streets ({@code keepOut}), so they never dig into a crossing street.
 */
public class TownStreetPiece extends StructurePiece {
	public static final int HALF_WIDTH = 3;
	/** Width of the levelled verge on each side of the street. */
	public static final int VERGE = 3;
	private static final int LAMP_SPACING = 16;

	private final int startX;
	private final int startZ;
	private final Direction.Axis axis;
	private final int[] heights;
	/** Rectangles (minX, minZ, maxX, maxZ, ...) of all the town's streets, which verges leave alone. */
	private final int[] keepOut;
	private final TownCondition condition;
	private final long seed;

	public TownStreetPiece(int startX, int startZ, Direction.Axis axis, int[] heights, int[] keepOut, TownCondition condition, long seed) {
		super(DLWorldgen.TOWN_STREET, 0, box(startX, startZ, axis, heights));
		this.startX = startX;
		this.startZ = startZ;
		this.axis = axis;
		this.heights = heights;
		this.keepOut = keepOut;
		this.condition = condition;
		this.seed = seed;
	}

	public TownStreetPiece(CompoundTag tag) {
		super(DLWorldgen.TOWN_STREET, tag);
		this.startX = tag.getIntOr("SX", 0);
		this.startZ = tag.getIntOr("SZ", 0);
		this.axis = tag.getIntOr("Axis", 0) == 0 ? Direction.Axis.X : Direction.Axis.Z;
		this.heights = tag.getIntArray("H").orElse(new int[] {0});
		this.keepOut = tag.getIntArray("Keep").orElse(new int[0]);
		this.condition = TownCondition.byId(tag.getStringOr("Cond", "abandoned"));
		this.seed = tag.getLongOr("Seed", 0L);
	}

	private static BoundingBox box(int startX, int startZ, Direction.Axis axis, int[] heights) {
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (int h : heights) {
			lo = Math.min(lo, h);
			hi = Math.max(hi, h);
		}
		int length = heights.length - 1;
		int side = HALF_WIDTH + VERGE;
		if (axis == Direction.Axis.X) {
			return new BoundingBox(startX, lo - 8, startZ - side, startX + length, hi + 6, startZ + side);
		}
		return new BoundingBox(startX - side, lo - 8, startZ, startX + side, hi + 6, startZ + length);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putInt("SX", startX);
		tag.putInt("SZ", startZ);
		tag.putInt("Axis", axis == Direction.Axis.X ? 0 : 1);
		tag.putIntArray("H", heights);
		tag.putIntArray("Keep", keepOut);
		tag.putString("Cond", condition.id());
		tag.putLong("Seed", seed);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
		BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int t = 0; t < heights.length; t++) {
			int y = heights[t];
			for (int w = -HALF_WIDTH - VERGE; w <= HALF_WIDTH + VERGE; w++) {
				int x = axis == Direction.Axis.X ? startX + t : startX + w;
				int z = axis == Direction.Axis.X ? startZ + w : startZ + t;
				if (x < chunkBox.minX() || x > chunkBox.maxX() || z < chunkBox.minZ() || z > chunkBox.maxZ()) {
					continue;
				}
				if (Math.abs(w) > HALF_WIDTH) {
					if (!keptOut(x, z)) {
						verge(level, pos, x, y, z);
					}
					continue;
				}
				long hash = Hashing.of(seed, x, z, 0x57EEL);
				pos.set(x, y, z);
				level.setBlock(pos, surface(t, w, hash), Block.UPDATE_CLIENTS);
				for (int k = y + 1; k <= y + 5; k++) {
					pos.set(x, k, z);
					if (!level.getBlockState(pos).isAir()) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
				for (int k = y - 1; k >= y - 8; k--) {
					pos.set(x, k, z);
					BlockState below = level.getBlockState(pos);
					if (below.isSolid() && below.getFluidState().isEmpty()) {
						break;
					}
					level.setBlock(pos, k >= y - 2 ? Blocks.DIRT.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				decorate(level, pos, x, y, z, t, w, hash);
			}
		}
	}

	private double parkedChance() {
		return switch (condition) {
			case OVERRUN -> 0.55;
			case EVACUATED -> 0.5;
			case ABANDONED -> 0.45;
			case MILITARY -> 0.35;
			case BANDIT -> 0.3;
			default -> 0.2;
		};
	}

	private boolean keptOut(int x, int z) {
		for (int i = 0; i + 3 < keepOut.length; i += 4) {
			if (x >= keepOut[i] && x <= keepOut[i + 2] && z >= keepOut[i + 1] && z <= keepOut[i + 3]) {
				return true;
			}
		}
		return false;
	}

	/** Levels one verge column to street height, topped with the ground the column already had. */
	private void verge(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z) {
		pos.set(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1, z);
		BlockState natural = level.getBlockState(pos);
		BlockState soil = natural.is(BlockTags.DIRT) || natural.is(BlockTags.SAND) ? natural : Blocks.COARSE_DIRT.defaultBlockState();
		for (int k = y + 1; k <= y + 6; k++) {
			pos.set(x, k, z);
			BlockState state = level.getBlockState(pos);
			if (!state.isAir() && !isStreet(state)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		pos.set(x, y, z);
		if (isStreet(level.getBlockState(pos))) {
			return;
		}
		level.setBlock(pos, soil, Block.UPDATE_CLIENTS);
		for (int k = y - 1; k >= y - 6; k--) {
			pos.set(x, k, z);
			BlockState below = level.getBlockState(pos);
			if (below.isSolid() && below.getFluidState().isEmpty()) {
				break;
			}
			level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/** Blocks a verge never removes: the surface and furniture of streets and roads. */
	private static boolean isStreet(BlockState state) {
		return state.is(DLBlocks.ASPHALT) || state.is(DLBlocks.CRACKED_ASPHALT) || state.is(DLBlocks.ASPHALT_YELLOW_LINE)
			|| state.is(DLBlocks.ASPHALT_WHITE_LINE) || state.is(DLBlocks.SIDEWALK) || state.is(DLBlocks.CONCRETE)
			|| state.is(DLDecor.get(DecorKind.STREET_LAMP)) || state.is(DLDecor.get(DecorKind.STREET_LAMP_HEAD))
			|| state.is(DLDecor.get(DecorKind.UTILITY_POLE)) || state.is(DLDecor.get(DecorKind.UTILITY_POLE_CROSSARM))
			|| state.is(DLDecor.get(DecorKind.GUARDRAIL));
	}

	private BlockState surface(int t, int w, long hash) {
		double roll = Hashing.unit(hash);
		if (Math.abs(w) == HALF_WIDTH) {
			return roll < condition.damage() * 0.25 ? DLBlocks.RUBBLE.defaultBlockState() : DLBlocks.SIDEWALK.defaultBlockState();
		}
		boolean cracked = roll < 0.25 + condition.damage() * 0.4;
		if (roll < condition.damage() * 0.06) {
			return Blocks.GRAVEL.defaultBlockState();
		}
		if (w == 0 && (t / 4) % 2 == 0 && !cracked) {
			return DLBlocks.ASPHALT_YELLOW_LINE.defaultBlockState().setValue(RoadLineBlock.AXIS, axis);
		}
		return cracked ? DLBlocks.CRACKED_ASPHALT.defaultBlockState() : DLBlocks.ASPHALT.defaultBlockState();
	}

	private void decorate(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, int t, int w, long hash) {
		double roll = Hashing.unit(Hashing.mix(hash));
		// Cars left at the kerb, more in the towns people fled than in those still lived in.
		if (Math.abs(w) == 2 && t % 19 == 9 && t > 8 && t < heights.length - 8 && com.deadlands.config.ConfigManager.get().world.abandonedVehicles
			&& Hashing.unit(Hashing.mix(hash ^ 0x70617263L)) < parkedChance()) {
			Direction along = axis == Direction.Axis.X ? (w > 0 ? Direction.EAST : Direction.WEST) : (w > 0 ? Direction.SOUTH : Direction.NORTH);
			pos.set(x, y + 1, z);
			com.deadlands.block.SpawnMarkerBlock.Spawn spawn = condition == TownCondition.MILITARY && roll < 0.6
				? com.deadlands.block.SpawnMarkerBlock.Spawn.VEHICLE_MILITARY
				: roll < 0.08 ? com.deadlands.block.SpawnMarkerBlock.Spawn.VEHICLE_EMERGENCY : com.deadlands.block.SpawnMarkerBlock.Spawn.VEHICLE_CIVILIAN;
			level.setBlock(pos, com.deadlands.registry.DLMarkers.SPAWN_MARKER.defaultBlockState()
				.setValue(com.deadlands.block.SpawnMarkerBlock.SPAWN, spawn).setValue(com.deadlands.block.SpawnMarkerBlock.FACING, along), Block.UPDATE_CLIENTS);
			return;
		}
		// Dead street lamps on alternating sidewalks; the power has been out for months.
		if (Math.abs(w) == HALF_WIDTH && t % LAMP_SPACING == LAMP_SPACING / 2 && (t / LAMP_SPACING) % 2 == (w > 0 ? 0 : 1)) {
			Direction inward = axis == Direction.Axis.X ? (w > 0 ? Direction.NORTH : Direction.SOUTH) : (w > 0 ? Direction.WEST : Direction.EAST);
			for (int k = 1; k <= 4; k++) {
				pos.set(x, y + k, z);
				level.setBlock(pos, DLDecor.get(DecorKind.STREET_LAMP).defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			pos.set(x, y + 5, z);
			level.setBlock(pos, DLDecor.get(DecorKind.STREET_LAMP_HEAD).defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, inward),
				Block.UPDATE_CLIENTS);
			return;
		}
		if (Math.abs(w) < HALF_WIDTH && roll < condition.blood() * 0.012) {
			Block decal = roll < condition.blood() * 0.004 ? DLBlocks.DRIED_BLOOD_POOL : roll < condition.blood() * 0.008 ? DLBlocks.DRAG_MARKS : DLBlocks.DRIED_BLOOD_SPOTS;
			pos.set(x, y + 1, z);
			level.setBlock(pos, decal.defaultBlockState().setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
				.setValue(com.deadlands.block.DecalBlock.TURN, (int) Math.floorMod(hash, 4L)), Block.UPDATE_CLIENTS);
		} else if (roll > 1.0 - condition.damage() * 0.01) {
			pos.set(x, y + 1, z);
			level.setBlock(pos, DLBlocks.RUBBLE.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}
}
