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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * An intersection of two town streets: dead traffic lights on the four corners (some knocked
 * down, more of them in overrun and abandoned towns) and faded zebra crossings on every arm.
 * Placed by {@link TownPlanner} after the streets, at the crossing's street height.
 */
public class TownCrossingPiece extends StructurePiece {
	private static final int CORNER = TownStreetPiece.HALF_WIDTH + 1;
	private static final int STRIPE_FROM = TownStreetPiece.HALF_WIDTH + 1;

	private final int cx;
	private final int cy;
	private final int cz;
	private final TownCondition condition;
	private final long seed;

	public TownCrossingPiece(int cx, int cy, int cz, TownCondition condition, long seed) {
		super(DLWorldgen.TOWN_CROSSING, 0, BoundingBox.fromCorners(new BlockPos(cx - CORNER - 2, cy, cz - CORNER - 2),
			new BlockPos(cx + CORNER + 2, cy + 3, cz + CORNER + 2)));
		this.cx = cx;
		this.cy = cy;
		this.cz = cz;
		this.condition = condition;
		this.seed = seed;
	}

	public TownCrossingPiece(CompoundTag tag) {
		super(DLWorldgen.TOWN_CROSSING, tag);
		this.cx = tag.getIntOr("CX", 0);
		this.cy = tag.getIntOr("CY", 0);
		this.cz = tag.getIntOr("CZ", 0);
		this.condition = TownCondition.byId(tag.getStringOr("Cond", "abandoned"));
		this.seed = tag.getLongOr("Seed", 0L);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putInt("CX", cx);
		tag.putInt("CY", cy);
		tag.putInt("CZ", cz);
		tag.putString("Cond", condition.id());
		tag.putLong("Seed", seed);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
		BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
		double knocked = condition == TownCondition.OVERRUN || condition == TownCondition.ABANDONED ? 0.5 : 0.2;
		BlockState light = DLDecor.get(DecorKind.TRAFFIC_LIGHT).defaultBlockState();
		// Each corner's light faces the traffic arriving on the street to its right.
		int[][] corners = {{CORNER, CORNER}, {-CORNER, CORNER}, {-CORNER, -CORNER}, {CORNER, -CORNER}};
		Direction[] faces = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
		for (int i = 0; i < 4; i++) {
			BlockPos at = new BlockPos(cx + corners[i][0], cy + 1, cz + corners[i][1]);
			if (!chunkBox.isInside(at) || Hashing.unit(Hashing.of(seed, at.getX(), at.getZ(), 0x7AFFL)) < knocked) {
				continue;
			}
			if (level.getBlockState(at).isAir() || level.getBlockState(at).canBeReplaced()) {
				level.setBlock(at, light.setValue(HorizontalDirectionalBlock.FACING, faces[i]), Block.UPDATE_CLIENTS);
			}
		}
		// Zebra crossings: two rows of stripes across each arm, worn away in places.
		for (Direction arm : Direction.Plane.HORIZONTAL) {
			Direction across = arm.getClockWise();
			Direction.Axis stripeAxis = arm.getAxis();
			for (int depth = STRIPE_FROM; depth <= STRIPE_FROM + 1; depth++) {
				for (int w = -TownStreetPiece.HALF_WIDTH + 1; w <= TownStreetPiece.HALF_WIDTH - 1; w += 2) {
					BlockPos at = new BlockPos(cx + arm.getStepX() * depth + across.getStepX() * w, cy,
						cz + arm.getStepZ() * depth + across.getStepZ() * w);
					if (!chunkBox.isInside(at) || Hashing.unit(Hashing.of(seed, at.getX(), at.getZ(), 0x2EB4L)) < 0.25) {
						continue;
					}
					BlockState here = level.getBlockState(at);
					if (here.is(DLBlocks.ASPHALT) || here.is(DLBlocks.CRACKED_ASPHALT)) {
						level.setBlock(at, DLBlocks.ASPHALT_WHITE_LINE.defaultBlockState().setValue(RoadLineBlock.AXIS, stripeAxis),
							Block.UPDATE_CLIENTS);
					}
				}
			}
		}
	}
}
