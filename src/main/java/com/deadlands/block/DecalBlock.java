package com.deadlands.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A flat environmental mark (dried blood, scorch, survivor graffiti) on a floor, wall or ceiling.
 * Floor and ceiling marks get a random quarter turn when placed so repeated decals never line up.
 * Cosmetic only: no collision, breaks instantly, drops nothing.
 */
public class DecalBlock extends FaceAttachedHorizontalDirectionalBlock {
	public static final MapCodec<DecalBlock> CODEC = simpleCodec(DecalBlock::new);
	public static final IntegerProperty TURN = IntegerProperty.create("turn", 0, 3);

	private static final VoxelShape FLOOR = Block.box(0, 0, 0, 16, 0.25, 16);
	private static final VoxelShape CEILING = Block.box(0, 15.75, 0, 16, 16, 16);
	private static final VoxelShape WALL_NORTH = Block.box(0, 0, 15.75, 16, 16, 16);
	private static final VoxelShape WALL_SOUTH = Block.box(0, 0, 0, 16, 16, 0.25);
	private static final VoxelShape WALL_EAST = Block.box(0, 0, 0, 0.25, 16, 16);
	private static final VoxelShape WALL_WEST = Block.box(15.75, 0, 0, 16, 16, 16);

	public DecalBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FACE, AttachFace.FLOOR).setValue(TURN, 0));
	}

	@Override
	protected MapCodec<DecalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, FACE, TURN);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = super.getStateForPlacement(context);
		return state == null ? null : state.setValue(TURN, context.getLevel().getRandom().nextInt(4));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(FACE)) {
			case FLOOR -> FLOOR;
			case CEILING -> CEILING;
			case WALL -> switch (state.getValue(FACING)) {
				case SOUTH -> WALL_SOUTH;
				case EAST -> WALL_EAST;
				case WEST -> WALL_WEST;
				default -> WALL_NORTH;
			};
		};
	}
}
