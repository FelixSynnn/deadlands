package vectorwing.farmersdelight.common.block;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import vectorwing.farmersdelight.common.utility.ShapeUtils;

public class RotatedFeastBlock extends FeastBlock {
    private final VoxelShape[][] combinedShapes;

    public RotatedFeastBlock(
        Properties properties, Supplier<Item> servingItem, boolean hasLeftovers, VoxelShape[] feastShapes, @Nullable VoxelShape containerShape
    ) {
        super(properties, servingItem, hasLeftovers, true);
        this.combinedShapes = containerShape == null
            ? ShapeUtils.buildRotatedFoodShapes(feastShapes)
            : ShapeUtils.buildPlatedFoodShapes(feastShapes, containerShape);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.combinedShapes[state.getValue(SERVINGS)][state.getValue(FACING).get2DDataValue()];
    }
}
