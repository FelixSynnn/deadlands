package vectorwing.farmersdelight.common.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import vectorwing.farmersdelight.common.registry.ModBlocks;
import vectorwing.farmersdelight.common.registry.ModItems;

public class RiceBlock extends VegetationBlock implements BonemealableBlock, LiquidBlockContainer {
    public static final MapCodec<RiceBlock> CODEC = simpleCodec(RiceBlock::new);
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    public static final BooleanProperty SUPPORTING = BooleanProperty.create("supporting");
    private static final VoxelShape[] SHAPE_BY_AGE = new VoxelShape[]{
        Block.box(3.0, 0.0, 3.0, 13.0, 8.0, 13.0),
        Block.box(3.0, 0.0, 3.0, 13.0, 10.0, 13.0),
        Block.box(2.0, 0.0, 2.0, 14.0, 12.0, 14.0),
        Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0)
    };

    public RiceBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(AGE, 0).setValue(SUPPORTING, false));
    }

    @Override
    protected MapCodec<? extends VegetationBlock> codec() {
        return CODEC;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        if (level.hasChunksAt(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            if (level.getRawBrightness(pos.above(), 0) >= 6) {
                int age = this.getAge(state);
                if (age <= this.getMaxAge()) {
                    float chance = 10.0F;
                    if (random.nextInt((int)(25.0F / chance) + 1) == 0) {
                        if (age == this.getMaxAge()) {
                            RicePaniclesBlock riceUpper = (RicePaniclesBlock)ModBlocks.RICE_CROP_PANICLES.get();
                            if (riceUpper.defaultBlockState().canSurvive(level, pos.above()) && level.isEmptyBlock(pos.above())) {
                                level.setBlockAndUpdate(pos.above(), riceUpper.defaultBlockState());
                            }
                        } else {
                            level.setBlock(pos, this.withAge(age + 1), 2);
                        }
                    }
                }
            }
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE_BY_AGE[state.getValue(this.getAgeProperty())];
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        return super.canSurvive(state, level, pos) && fluid.is(FluidTags.WATER) && fluid.getAmount() == 8;
    }

    @Override
    public boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return super.mayPlaceOn(state, level, pos) || state.is(BlockTags.DIRT);
    }

    public IntegerProperty getAgeProperty() {
        return AGE;
    }

    protected int getAge(BlockState state) {
        return state.getValue(this.getAgeProperty());
    }

    public int getMaxAge() {
        return 3;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(ModItems.RICE.get());
    }

    public BlockState withAge(int age) {
        return this.defaultBlockState().setValue(this.getAgeProperty(), age);
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(AGE, SUPPORTING);
    }

    @Override
    public BlockState updateShape(
        BlockState state,
        LevelReader level,
        ScheduledTickAccess scheduledTickAccess,
        BlockPos currentPos,
        Direction facing,
        BlockPos facingPos,
        BlockState facingState,
        RandomSource random
    ) {
        BlockState updatedState = super.updateShape(state, level, scheduledTickAccess, currentPos, facing, facingPos, facingState, random);
        if (!updatedState.isAir()) {
            scheduledTickAccess.scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
            if (facing == Direction.UP) {
                return updatedState.setValue(SUPPORTING, this.isSupportingRiceUpper(facingState));
            }
        }

        return updatedState;
    }

    public boolean isSupportingRiceUpper(BlockState topState) {
        return topState.getBlock() == ModBlocks.RICE_CROP_PANICLES.get();
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        return fluid.is(FluidTags.WATER) && fluid.getAmount() == 8 ? super.getStateForPlacement(context) : null;
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        BlockState upperState = level.getBlockState(pos.above());
        return upperState.getBlock() instanceof RicePaniclesBlock ? !((RicePaniclesBlock)upperState.getBlock()).isMaxAge(upperState) : true;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    protected int getBonemealAgeIncrease(Level level) {
        return Mth.nextInt(level.getRandom(), 1, 4);
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        int ageGrowth = Math.min(this.getAge(state) + this.getBonemealAgeIncrease(level), 7);
        if (ageGrowth <= this.getMaxAge()) {
            level.setBlockAndUpdate(pos, state.setValue(AGE, ageGrowth));
        } else {
            BlockState top = level.getBlockState(pos.above());
            if (top.getBlock() == ModBlocks.RICE_CROP_PANICLES.get()) {
                BonemealableBlock growable = (BonemealableBlock)level.getBlockState(pos.above()).getBlock();
                if (growable.isValidBonemealTarget(level, pos.above(), top)) {
                    growable.performBonemeal(level, level.getRandom(), pos.above(), top);
                }
            } else {
                RicePaniclesBlock riceUpper = (RicePaniclesBlock)ModBlocks.RICE_CROP_PANICLES.get();
                int remainingGrowth = ageGrowth - this.getMaxAge() - 1;
                if (riceUpper.defaultBlockState().canSurvive(level, pos.above()) && level.isEmptyBlock(pos.above())) {
                    level.setBlockAndUpdate(pos, state.setValue(AGE, this.getMaxAge()));
                    level.setBlock(pos.above(), riceUpper.defaultBlockState().setValue(RicePaniclesBlock.RICE_AGE, remainingGrowth), 2);
                }
            }
        }
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return Fluids.WATER.getSource(false);
    }

    @Override
    public boolean canPlaceLiquid(@Nullable LivingEntity player, BlockGetter level, BlockPos pos, BlockState state, Fluid fluidIn) {
        return false;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        return false;
    }
}
