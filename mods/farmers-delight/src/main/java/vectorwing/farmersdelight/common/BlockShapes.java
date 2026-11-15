package vectorwing.farmersdelight.common;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BlockShapes {
    public static final VoxelShape TRAY_OUTER_SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 2.0, 15.0);
    public static final VoxelShape TRAY_INNER_SHAPE = Block.box(2.0, 1.0, 2.0, 14.0, 2.0, 14.0);
    public static final VoxelShape TRAY_SHAPE = Shapes.join(TRAY_OUTER_SHAPE, TRAY_INNER_SHAPE, BooleanOp.ONLY_FIRST);
    public static final VoxelShape[] ROAST_CHICKEN_SHAPES = new VoxelShape[]{
        Block.box(4.0, 1.0, 10.0, 12.0, 8.0, 12.0),
        Block.box(4.0, 1.0, 8.0, 12.0, 8.0, 12.0),
        Block.box(4.0, 1.0, 6.0, 12.0, 8.0, 12.0),
        Block.box(4.0, 1.0, 4.0, 12.0, 8.0, 12.0)
    };
    public static final VoxelShape[] HONEY_GLAZED_HAM_SHAPES = new VoxelShape[]{
        Block.box(4.0, 1.0, 3.0, 12.0, 3.0, 11.0),
        Block.box(4.0, 1.0, 6.0, 12.0, 9.0, 10.0),
        Block.box(4.0, 2.0, 4.0, 12.0, 10.0, 10.0),
        Block.box(4.0, 2.0, 2.0, 12.0, 10.0, 10.0)
    };
    public static final VoxelShape[] SHEPHERDS_PIE_SHAPES = new VoxelShape[]{
        Block.box(2.0, 1.0, 8.0, 8.0, 8.0, 14.0),
        Block.box(2.0, 1.0, 8.0, 14.0, 8.0, 14.0),
        Shapes.join(Block.box(8.0, 1.0, 2.0, 14.0, 8.0, 8.0), Block.box(2.0, 1.0, 8.0, 14.0, 8.0, 14.0), BooleanOp.OR),
        Block.box(2.0, 1.0, 2.0, 14.0, 8.0, 14.0)
    };
}
