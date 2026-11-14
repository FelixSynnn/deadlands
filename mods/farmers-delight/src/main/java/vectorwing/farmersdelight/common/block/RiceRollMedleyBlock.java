package vectorwing.farmersdelight.common.block;

import com.google.common.base.Suppliers;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import vectorwing.farmersdelight.common.registry.ModItems;

public class RiceRollMedleyBlock extends FeastBlock {
    public static final IntegerProperty ROLL_SERVINGS = IntegerProperty.create("servings", 0, 8);
    protected static final VoxelShape SHAPE_NORTH_SOUTH = Shapes.or(
        Block.box(2.0, 1.0, 1.0, 14.0, 3.0, 15.0), Block.box(2.0, 0.0, 3.0, 14.0, 2.0, 5.0), Block.box(2.0, 0.0, 11.0, 14.0, 2.0, 13.0)
    );
    protected static final VoxelShape SHAPE_EAST_WEST = Shapes.or(
        Block.box(1.0, 1.0, 2.0, 15.0, 3.0, 14.0), Block.box(3.0, 0.0, 2.0, 5.0, 2.0, 14.0), Block.box(11.0, 0.0, 2.0, 13.0, 2.0, 14.0)
    );
    public final Supplier<List<Item>> riceRollServings = Suppliers.memoize(
        () -> List.of(
            ModItems.COD_ROLL.get(),
            ModItems.COD_ROLL.get(),
            ModItems.SALMON_ROLL.get(),
            ModItems.SALMON_ROLL.get(),
            ModItems.SALMON_ROLL.get(),
            ModItems.KELP_ROLL_SLICE.get(),
            ModItems.KELP_ROLL_SLICE.get(),
            ModItems.KELP_ROLL_SLICE.get()
        )
    );

    public RiceRollMedleyBlock(Properties properties) {
        super(properties, () -> ModItems.SALMON_ROLL.get(), true);
    }

    @Override
    public IntegerProperty getServingsProperty() {
        return ROLL_SERVINGS;
    }

    @Override
    public int getMaxServings() {
        return 8;
    }

    @Override
    public ItemStack getServingItem(BlockState state) {
        return new ItemStack(this.riceRollServings.get().get(state.getValue(this.getServingsProperty()) - 1));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FeastBlock.FACING).getAxis().equals(Axis.X) ? SHAPE_NORTH_SOUTH : SHAPE_EAST_WEST;
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(FACING, ROLL_SERVINGS);
    }
}
