package vectorwing.farmersdelight.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import vectorwing.farmersdelight.common.Configuration;
import vectorwing.farmersdelight.common.registry.ModBlocks;
import vectorwing.farmersdelight.refabricated.block.ReplaceOnRemoval;

public class HangingTomatoBlock extends TomatoBlock implements ReplaceOnRemoval {
    public HangingTomatoBlock(Properties properties) {
        super(properties, false);
        this.registerDefaultState(this.stateDefinition.any().setValue(this.getAgeProperty(), 0));
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    public boolean onRemove(BlockState state, BlockState newState, LevelAccessor level, BlockPos pos, int updateFlags) {
        return Configuration.ENABLE_TOMATO_ROPE_PERMANENCE.get() && (updateFlags ^ 64) != 0 && !state.is(newState.getBlock()) && newState.isAir()
            ? placeRope(level, pos)
            : true;
    }

    public static boolean placeRope(LevelAccessor level, BlockPos pos) {
        Block configuredRopeBlock = BuiltInRegistries.BLOCK.getValue(Identifier.parse(Configuration.DEFAULT_TOMATO_VINE_ROPE.get()));
        if (configuredRopeBlock == null) {
            configuredRopeBlock = ModBlocks.ROPE.get();
        }

        BlockState finalRopeState = configuredRopeBlock.equals(ModBlocks.ROPE.get())
            ? RopeBlock.getStateWithConnections(ModBlocks.ROPE.get().defaultBlockState(), level, pos, Direction.UP)
            : configuredRopeBlock.defaultBlockState();
        return level.setBlock(pos, finalRopeState, level.isClientSide() ? 11 : 3);
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(VINE_AGE);
    }
}
