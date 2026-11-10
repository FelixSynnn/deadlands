package com.deadlands.world.town;

import com.deadlands.block.DecorKind;
import com.deadlands.container.ContainerCondition;
import com.deadlands.container.ContainerKind;
import com.deadlands.container.LootContainerBlock;
import com.deadlands.container.LootContainerBlockEntity;
import com.deadlands.registry.DLBlocks;
import com.deadlands.registry.DLContainers;
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
 * What a town's condition put across the end of a main street: a military checkpoint (concrete
 * barriers, a sandbag line and a supply crate), an evacuation cordon, or a survivors' or bandits'
 * scrap wall. Every gate leaves a gap so players can get in.
 */
public class TownGatePiece extends StructurePiece {
	private final int gateX;
	private final int gateY;
	private final int gateZ;
	private final Direction outward;
	private final TownCondition condition;
	private final long seed;

	public TownGatePiece(int gateX, int gateY, int gateZ, Direction outward, TownCondition condition, long seed) {
		super(DLWorldgen.TOWN_GATE, 0, box(gateX, gateY, gateZ, outward));
		this.gateX = gateX;
		this.gateY = gateY;
		this.gateZ = gateZ;
		this.outward = outward;
		this.condition = condition;
		this.seed = seed;
	}

	public TownGatePiece(CompoundTag tag) {
		super(DLWorldgen.TOWN_GATE, tag);
		this.gateX = tag.getIntOr("GX", 0);
		this.gateY = tag.getIntOr("GY", 0);
		this.gateZ = tag.getIntOr("GZ", 0);
		this.outward = Direction.from2DDataValue(tag.getIntOr("Out", 0));
		this.condition = TownCondition.byId(tag.getStringOr("Cond", "abandoned"));
		this.seed = tag.getLongOr("Seed", 0L);
	}

	private static BoundingBox box(int x, int y, int z, Direction outward) {
		int w = TownStreetPiece.HALF_WIDTH;
		int inX = -outward.getStepX() * 4;
		int inZ = -outward.getStepZ() * 4;
		return BoundingBox.fromCorners(new BlockPos(x - w + Math.min(0, inX), y, z - w + Math.min(0, inZ)),
			new BlockPos(x + w + Math.max(0, inX), y + 3, z + w + Math.max(0, inZ)));
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putInt("GX", gateX);
		tag.putInt("GY", gateY);
		tag.putInt("GZ", gateZ);
		tag.putInt("Out", outward.get2DDataValue());
		tag.putString("Cond", condition.id());
		tag.putLong("Seed", seed);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
		BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
		Direction along = outward.getClockWise();
		Direction inward = outward.getOpposite();
		BlockState barrier = DLDecor.get(DecorKind.CONCRETE_BARRIER).defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, outward);
		switch (condition.gate()) {
			case CHECKPOINT -> {
				for (int w = -TownStreetPiece.HALF_WIDTH; w <= TownStreetPiece.HALF_WIDTH; w++) {
					if (Math.abs(w) >= 2) {
						set(level, chunkBox, offset(along, w, inward, 1), 1, barrier);
						set(level, chunkBox, offset(along, w, inward, 3), 1, DLBlocks.RUBBLE.defaultBlockState());
					}
				}
				BlockPos crate = offset(along, TownStreetPiece.HALF_WIDTH, inward, 4);
				if (chunkBox.isInside(crate.atY(gateY + 1))) {
					BlockState state = DLContainers.get(ContainerKind.MILITARY_CRATE).defaultBlockState()
						.setValue(LootContainerBlock.FACING, inward.getOpposite())
						.setValue(LootContainerBlock.CONDITION, ContainerCondition.DUSTY);
					BlockPos at = crate.atY(gateY + 1);
					level.setBlock(at, state, Block.UPDATE_ALL);
					if (level.getBlockEntity(at) instanceof LootContainerBlockEntity container) {
						container.setLootTable(ContainerKind.MILITARY_CRATE.lootTable(), Hashing.of(seed, at.getX(), at.getZ(), 1L));
					}
				}
			}
			case EVACUATION -> {
				for (int w = -TownStreetPiece.HALF_WIDTH; w <= TownStreetPiece.HALF_WIDTH; w++) {
					if (w != 0 && w != 1) {
						set(level, chunkBox, offset(along, w, inward, 1), 1, barrier);
					}
				}
			}
			case BARRICADE -> {
				for (int w = -TownStreetPiece.HALF_WIDTH; w <= TownStreetPiece.HALF_WIDTH; w++) {
					if (w == 0) {
						continue;
					}
					double roll = Hashing.unit(Hashing.of(seed, gateX + w, gateZ, 0xBA22L));
					BlockState wall = roll < 0.4 ? DLBlocks.CORRUGATED_METAL.defaultBlockState()
						: roll < 0.75 ? DLBlocks.BOARDED_PLANKS.defaultBlockState() : DLBlocks.RUSTED_SHEET_METAL.defaultBlockState();
					set(level, chunkBox, offset(along, w, inward, 1), 1, wall);
					if (roll > 0.2) {
						set(level, chunkBox, offset(along, w, inward, 1), 2, wall);
					}
				}
			}
			case NONE -> {
			}
		}
	}

	private BlockPos offset(Direction along, int w, Direction inward, int depth) {
		return new BlockPos(gateX + along.getStepX() * w + inward.getStepX() * depth, gateY, gateZ + along.getStepZ() * w + inward.getStepZ() * depth);
	}

	private void set(WorldGenLevel level, BoundingBox chunkBox, BlockPos base, int up, BlockState state) {
		BlockPos at = base.above(up);
		if (chunkBox.isInside(at)) {
			level.setBlock(at, state, Block.UPDATE_CLIENTS);
		}
	}
}
