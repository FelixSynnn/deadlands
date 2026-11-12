package vectorwing.farmersdelight.common.block;

import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ConsumableListener;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import vectorwing.farmersdelight.common.tag.ModTags;
import vectorwing.farmersdelight.common.utility.ItemUtils;
import vectorwing.farmersdelight.common.utility.ShapeUtils;

public class PieBlock extends Block {
    public static final Property<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty BITES = IntegerProperty.create("bites", 0, 3);
    protected static final VoxelShape[] SHAPES = new VoxelShape[]{
        Block.box(2.0, 0.0, 2.0, 14.0, 4.0, 14.0),
        Shapes.join(Block.box(2.0, 0.0, 8.0, 8.0, 4.0, 14.0), Block.box(2.0, 0.0, 2.0, 14.0, 4.0, 8.0), BooleanOp.OR),
        Block.box(2.0, 0.0, 2.0, 14.0, 4.0, 8.0),
        Block.box(8.0, 0.0, 2.0, 14.0, 4.0, 8.0)
    };
    private static final VoxelShape[][] ROTATED_SHAPES = buildShapes();
    public final Supplier<Item> pieSlice;

    public PieBlock(Properties properties, Supplier<Item> pieSlice) {
        super(properties);
        this.pieSlice = pieSlice;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(BITES, 0));
    }

    public ItemStack getPieSliceItem() {
        return new ItemStack(this.pieSlice.get());
    }

    public int getMaxBites() {
        return 4;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ROTATED_SHAPES[state.getValue(BITES)][state.getValue(FACING).get2DDataValue()];
    }

    private static VoxelShape[][] buildShapes() {
        VoxelShape[][] result = new VoxelShape[SHAPES.length][4];

        for (int i = 0; i < SHAPES.length; i++) {
            Map<Direction, VoxelShape> rotated = ShapeUtils.getShapesRotatedFromNorth(SHAPES[i]);

            for (Entry<Direction, VoxelShape> entry : rotated.entrySet()) {
                result[i][entry.getKey().get2DDataValue()] = entry.getValue();
            }
        }

        return result;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public InteractionResult useItemOn(
        ItemStack heldStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit
    ) {
        return heldStack.is(ModTags.Items.KNIVES) ? this.cutSlice(heldStack, level, pos, state, player) : InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            if (this.consumeBite(level, pos, state, player).consumesAction()) {
                return InteractionResult.SUCCESS;
            }

            if (player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
                return InteractionResult.CONSUME;
            }
        }

        return this.consumeBite(level, pos, state, player);
    }

    protected InteractionResult consumeBite(Level level, BlockPos pos, BlockState state, Player playerIn) {
        if (!playerIn.canEat(false)) {
            return InteractionResult.PASS;
        }

        ItemStack sliceStack = this.getPieSliceItem();
        if (sliceStack.has(DataComponents.CONSUMABLE)) {
            Consumable sliceConsumable = sliceStack.get(DataComponents.CONSUMABLE);
            sliceStack.getAllOfType(ConsumableListener.class)
                .forEach(consumableListener -> consumableListener.onConsume(level, playerIn, sliceStack, sliceConsumable));
            if (!level.isClientSide()) {
                sliceConsumable.onConsumeEffects().forEach(consumeEffect -> consumeEffect.apply(level, sliceStack, playerIn));
            }
        }

        int bites = state.getValue(BITES);
        if (bites < this.getMaxBites() - 1) {
            level.setBlock(pos, state.setValue(BITES, bites + 1), 3);
        } else {
            level.removeBlock(pos, false);
        }

        level.playSound(null, pos, SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.8F, 0.8F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 3, 0.1, 0.1, 0.1, 0.001
            );
        }

        return InteractionResult.SUCCESS;
    }

    protected InteractionResult cutSlice(ItemStack knife, Level level, BlockPos pos, BlockState state, Player player) {
        int bites = state.getValue(BITES);
        if (bites < this.getMaxBites() - 1) {
            level.setBlock(pos, state.setValue(BITES, bites + 1), 3);
        } else {
            level.removeBlock(pos, false);
        }

        Direction direction = player.getDirection().getOpposite();
        ItemUtils.spawnItemEntity(
            level, this.getPieSliceItem(), pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, direction.getStepX() * 0.15, 0.05, direction.getStepZ() * 0.15
        );
        level.playSound(null, pos, SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 0.8F, 0.8F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 3, 0.1, 0.1, 0.1, 0.001
            );
        }

        player.awardStat(Stats.ITEM_USED.get(knife.getItem()));
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState updateShape(
        BlockState stateIn,
        LevelReader level,
        ScheduledTickAccess scheduledTickAccess,
        BlockPos currentPos,
        Direction facing,
        BlockPos facingPos,
        BlockState facingState,
        RandomSource random
    ) {
        return facing == Direction.DOWN && !stateIn.canSurvive(level, currentPos)
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(stateIn, level, scheduledTickAccess, currentPos, facing, facingPos, facingState, random);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canSupportRigidBlock(level, pos.below());
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(FACING, BITES);
    }

    @Override
    public int getAnalogOutputSignal(BlockState blockState, Level level, BlockPos pos, Direction direction) {
        return this.getMaxBites() - blockState.getValue(BITES);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
