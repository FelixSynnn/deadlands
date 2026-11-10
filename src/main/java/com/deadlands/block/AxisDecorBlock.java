package com.deadlands.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A fixture that lies along a horizontal axis (a utility-pole crossarm). Authored along X. */
public class AxisDecorBlock extends Block {
	public static final MapCodec<AxisDecorBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		DecorKind.CODEC.fieldOf("kind").forGetter(AxisDecorBlock::kind),
		propertiesCodec()
	).apply(instance, AxisDecorBlock::new));
	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

	private final DecorKind kind;
	private final VoxelShape alongX;
	private final VoxelShape alongZ;

	public AxisDecorBlock(DecorKind kind, Properties properties) {
		super(properties);
		this.kind = kind;
		this.alongX = kind.northShape();
		this.alongZ = Shapes2.rotate(kind.northShape(), Direction.EAST);
		registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
	}

	public DecorKind kind() {
		return kind;
	}

	@Override
	public MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// The arm runs across the direction the player faces, like a crossarm over a road.
		return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getClockWise().getAxis());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(AXIS) == Direction.Axis.X ? alongX : alongZ;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
			return state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
		}
		return state;
	}
}
