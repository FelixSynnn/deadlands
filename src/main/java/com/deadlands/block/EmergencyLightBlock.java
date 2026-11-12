package com.deadlands.block;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A battery emergency light on a wall (Phase 3 lighting): a red lamp that pulses slowly, bright and
 * dim, while the power is out. It is what still works in a dead building. When the town's power is
 * restored it goes dark (mains lighting is back); outside towns it keeps pulsing. Placed on the face
 * of a wall, its lamp facing out.
 */
public class EmergencyLightBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<EmergencyLightBlock> CODEC = simpleCodec(EmergencyLightBlock::new);
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	public static final BooleanProperty PULSE = BooleanProperty.create("pulse");
	/** Half a pulse: a second and a half bright, a second and a half dim. */
	private static final int HALF_PULSE = 30;
	private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

	static {
		VoxelShape north = Block.box(4, 9, 13, 12, 14, 16); // on the wall to the south, facing north
		SHAPES.put(Direction.NORTH, north);
		SHAPES.put(Direction.EAST, Shapes2.rotate(north, Direction.EAST));
		SHAPES.put(Direction.SOUTH, Shapes2.rotate(north, Direction.SOUTH));
		SHAPES.put(Direction.WEST, Shapes2.rotate(north, Direction.WEST));
	}

	public EmergencyLightBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, true).setValue(PULSE, true));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LIT, PULSE);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction face = context.getClickedFace();
		Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite();
		return defaultBlockState().setValue(FACING, facing);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (level instanceof ServerLevel server && !oldState.is(this)) {
			server.scheduleTick(pos, this, HALF_PULSE);
		}
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		boolean lit = !mainsBack(level, pos);
		if (state.getValue(LIT) != lit) {
			level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_CLIENTS);
		}
		if (lit && !level.getBlockTicks().hasScheduledTick(pos, this)) {
			level.scheduleTick(pos, this, HALF_PULSE);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT)) {
			return;
		}
		level.setBlock(pos, state.setValue(PULSE, !state.getValue(PULSE)), Block.UPDATE_CLIENTS);
		level.scheduleTick(pos, this, HALF_PULSE);
	}

	/** True when this light stands in a town whose power has been restored. */
	private static boolean mainsBack(ServerLevel level, BlockPos pos) {
		return com.deadlands.world.town.TownLookup.nearest(level, pos, 150.0).map(town -> com.deadlands.world.power.PowerGrid.isPowered(level, town)
			&& !com.deadlands.horror.DarkBuildings.holdsDark(level, pos, town, false)).orElse(false); // the dark building keeps pulsing
	}
}
