package vectorwing.farmersdelight.common.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity;
import vectorwing.farmersdelight.common.registry.ModBlockEntityTypes;
import vectorwing.farmersdelight.common.registry.ModSounds;
import vectorwing.farmersdelight.common.tag.ModTags;

public class SkilletBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final MapCodec<SkilletBlock> CODEC = simpleCodec(SkilletBlock::new);
    public static final int MINIMUM_COOKING_TIME = 60;
    public static final Property<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty SUPPORT = BooleanProperty.create("support");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    protected static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 4.0, 15.0);
    protected static final VoxelShape SHAPE_WITH_TRAY = Shapes.or(SHAPE, Block.box(0.0, -1.0, 0.0, 16.0, 0.0, 16.0));

    public SkilletBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SUPPORT, false).setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof SkilletBlockEntity skilletEntity) {
            ItemStack heldStack = player.getItemInHand(hand);
            EquipmentSlot heldSlot = hand.equals(InteractionHand.MAIN_HAND) ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            if (heldStack.isEmpty()) {
                ItemStack extractedStack = skilletEntity.removeItem();
                if (!player.isCreative()) {
                    player.setItemSlot(heldSlot, extractedStack);
                }

                return InteractionResult.SUCCESS;
            } else {
                if (!skilletEntity.hasStoredStack() && level.recipeAccess().propertySet(RecipePropertySet.CAMPFIRE_INPUT).test(heldStack)) {
                    if (!(level instanceof ServerLevel serverLevel)) {
                        return InteractionResult.SUCCESS;
                    }

                    ItemStack remainderStack = skilletEntity.addItemToCook(heldStack, player, serverLevel);
                    if (remainderStack.getCount() != heldStack.getCount()) {
                        if (!player.isCreative()) {
                            player.setItemSlot(heldSlot, remainderStack);
                        }

                        level.playSound(null, pos, SoundEvents.LANTERN_PLACE, SoundSource.BLOCKS, 0.7F, 1.0F);
                        return InteractionResult.SUCCESS;
                    }
                }

                return InteractionResult.CONSUME;
            }
        } else {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(SUPPORT).equals(true) ? SHAPE_WITH_TRAY : SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        FluidState fluid = level.getFluidState(context.getClickedPos());
        return this.defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection())
            .setValue(WATERLOGGED, fluid.getType() == Fluids.WATER)
            .setValue(SUPPORT, this.getTrayState(context.getLevel(), context.getClickedPos()));
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
        if (state.getValue(WATERLOGGED)) {
            scheduledTickAccess.scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return facing.getAxis().equals(Axis.Y) ? state.setValue(SUPPORT, this.getTrayState(level, currentPos)) : state;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return level.getBlockEntity(pos) instanceof SkilletBlockEntity skillet
            ? skillet.getSkilletAsItem()
            : super.getCloneItemStack(level, pos, state, includeData);
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(FACING, SUPPORT, WATERLOGGED);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof SkilletBlockEntity skillet && skillet.isCooking()) {
            double x = pos.getX() + 0.5;
            double y = pos.getY();
            double z = pos.getZ() + 0.5;
            if (random.nextInt(10) == 0) {
                level.playLocalSound(x, y, z, ModSounds.BLOCK_SKILLET_SIZZLE.get(), SoundSource.BLOCKS, 0.4F, random.nextFloat() * 0.2F + 0.9F, false);
            }
        }
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntityTypes.SKILLET.get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntity) {
        return level instanceof ServerLevel serverLevel
            ? createTickerHelper(
                blockEntity,
                ModBlockEntityTypes.SKILLET.get(),
                (_level, blockPos, blockState, skilletEntity) -> SkilletBlockEntity.cookingTick(serverLevel, blockPos, blockState, skilletEntity)
            )
            : createTickerHelper(blockEntity, ModBlockEntityTypes.SKILLET.get(), SkilletBlockEntity::animationTick);
    }

    private boolean getTrayState(LevelReader world, BlockPos pos) {
        return world.getBlockState(pos.below()).is(ModTags.Blocks.TRAY_HEAT_SOURCES);
    }

    public static int getSkilletCookingTime(int originalCookingTime, int fireAspectLevel) {
        int cookingTime = originalCookingTime > 0 ? originalCookingTime : 600;
        int cookingSeconds = cookingTime / 20;
        float cookingTimeReduction = 0.2F;
        if (fireAspectLevel > 0) {
            cookingTimeReduction = (float)(cookingTimeReduction - fireAspectLevel * 0.05);
        }

        int result = (int)(cookingSeconds * cookingTimeReduction) * 20;
        return Mth.clamp(result, 60, originalCookingTime);
    }
}
