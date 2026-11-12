package vectorwing.farmersdelight.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class RopeFenceGateBlock extends FenceGateBlock {
    protected static final VoxelShape Z_SHAPE = Block.box(0.0, 0.0, 7.0, 16.0, 14.0, 9.0);
    protected static final VoxelShape X_SHAPE = Block.box(7.0, 0.0, 0.0, 9.0, 14.0, 16.0);
    protected static final VoxelShape Z_COLLISION_SHAPE = Block.box(0.0, 0.0, 7.0, 16.0, 24.0, 9.0);
    protected static final VoxelShape X_COLLISION_SHAPE = Block.box(7.0, 0.0, 0.0, 9.0, 24.0, 16.0);

    public RopeFenceGateBlock(Properties props) {
        super(WoodType.OAK, props);
    }

    @Override
    public BlockState updateShape(
        BlockState state,
        LevelReader level,
        ScheduledTickAccess ticks,
        BlockPos pos,
        Direction directionToNeighbour,
        BlockPos neighbourPos,
        BlockState neighbourState,
        RandomSource random
    ) {
        Axis axis = directionToNeighbour.getAxis();
        if (state.getValue(FACING).getClockWise().getAxis() != axis) {
            return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
        }

        boolean isBorderedByWalls = this.isWall(state) && this.isWall(level.getBlockState(pos.relative(directionToNeighbour.getOpposite())));
        return state.setValue(IN_WALL, isBorderedByWalls);
    }

    protected boolean isWall(BlockState state) {
        return state.is(BlockTags.WALLS);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Axis.X ? X_SHAPE : Z_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(OPEN)) {
            return Shapes.empty();
        } else {
            return state.getValue(FACING).getAxis() == Axis.Z ? Z_COLLISION_SHAPE : X_COLLISION_SHAPE;
        }
    }
}
