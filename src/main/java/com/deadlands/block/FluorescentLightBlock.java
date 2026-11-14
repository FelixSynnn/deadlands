package com.deadlands.block;

import com.deadlands.Deadlands;
import com.deadlands.world.power.PowerGrid;
import com.deadlands.world.town.TownLookup;
import com.deadlands.world.town.TownSite;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A ceiling fluorescent fixture (Phase 3 lighting). Inside a town it follows the town's power
 * ({@link PowerGrid}): dark until the power is restored. Outside towns (a survivor's base, a story
 * location) it has its own supply and is lit. A <b>faulty</b> tube flickers: it stutters off and on,
 * now and then dies for a few seconds, and buzzes as it comes back, all on scheduled ticks on the
 * server so every player sees the same flicker.
 */
public class FluorescentLightBlock extends Block {
	public static final MapCodec<FluorescentLightBlock> CODEC = simpleCodec(FluorescentLightBlock::new);
	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	public static final BooleanProperty FAULTY = BooleanProperty.create("faulty");
	private static final VoxelShape ALONG_X = Block.box(1, 14, 4.5, 15, 16, 11.5);
	private static final VoxelShape ALONG_Z = Block.box(4.5, 14, 1, 11.5, 16, 15);
	private static final double TOWN_EXTENT = 150.0;

	/** How many times faulty tubes have switched since the server started (for {@code /deadlands lights}). */
	private static int toggles;

	public FluorescentLightBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(LIT, true).setValue(FAULTY, false));
	}

	public static int toggles() {
		return toggles;
	}

	@Override
	public MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS, LIT, FAULTY);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(AXIS) == Direction.Axis.X ? ALONG_X : ALONG_Z;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
			return state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
		}
		return state;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (level instanceof ServerLevel server && !oldState.is(this)) {
			server.scheduleTick(pos, this, 20 + server.getRandom().nextInt(60));
		}
	}

	/** Re-checks the power now and then, and keeps a faulty tube's flicker going. */
	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		boolean power = powered(level, pos);
		if (!power && state.getValue(LIT)) {
			level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_CLIENTS);
		} else if (power && !state.getValue(LIT) && !state.getValue(FAULTY)) {
			level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_CLIENTS);
		}
		if (power && state.getValue(FAULTY) && !level.getBlockTicks().hasScheduledTick(pos, this)) {
			level.scheduleTick(pos, this, 20 + random.nextInt(100));
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!powered(level, pos)) {
			if (state.getValue(LIT)) {
				level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_CLIENTS);
			}
			return;
		}
		if (!state.getValue(FAULTY)) {
			if (!state.getValue(LIT)) {
				level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_CLIENTS);
			}
			return;
		}
		toggles++;
		if (state.getValue(LIT)) {
			level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_CLIENTS);
			// Mostly a stutter of a tick or four; one time in twelve the tube dies for a few seconds.
			level.scheduleTick(pos, this, random.nextInt(12) == 0 ? 20 + random.nextInt(60) : 1 + random.nextInt(4));
		} else {
			level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_CLIENTS);
			SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(Deadlands.id(random.nextInt(3) == 0 ? "ambient.buzz" : "ambient.flicker"));
			if (sound != null && random.nextInt(3) == 0) {
				level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.18F, 0.9F + random.nextFloat() * 0.2F);
			}
			// Three times in five it is still stuttering; otherwise it holds for a few seconds to twenty.
			level.scheduleTick(pos, this, random.nextInt(5) < 3 ? 2 + random.nextInt(6) : 60 + random.nextInt(340));
		}
	}

	/** In a town: the town's power. Outside towns: its own supply. */
	static boolean powered(ServerLevel level, BlockPos pos) {
		Optional<TownSite> town = TownLookup.nearest(level, pos, TOWN_EXTENT);
		if (town.isEmpty()) {
			return true;
		}
		// In a powered town the dark building (Phase 4 jumpscare) keeps its tubes off until a survivor comes near.
		return PowerGrid.isPowered(level, town.get()) && !com.deadlands.horror.DarkBuildings.holdsDark(level, pos, town.get(), true);
	}
}
