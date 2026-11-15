package vectorwing.farmersdelight.common.block;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;
import vectorwing.farmersdelight.common.registry.ModBlockEntityTypes;
import vectorwing.farmersdelight.common.registry.ModSounds;

public class CuttingBoardBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final MapCodec<CuttingBoardBlock> CODEC = simpleCodec(CuttingBoardBlock::new);
    public static final Property<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    protected static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 1.0, 15.0);

    public CuttingBoardBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.getStateDefinition().any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
    }

    public static void init() {
        UseBlockCallback.EVENT.register(CuttingBoardBlock.ToolCarvingEvent::onSneakPlaceTool);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return null;
    }

    @Override
    public InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof CuttingBoardBlockEntity cuttingBoard) {
            ItemStack mainHandStack = player.getMainHandItem();
            if (mainHandStack.isEmpty()) {
                if (!cuttingBoard.isEmpty() && !level.isClientSide()) {
                    ItemStack removedStack = cuttingBoard.removeItem();
                    if (!player.isCreative()) {
                        player.getInventory().add(removedStack);
                    }

                    Vec3 centerPos = pos.getCenter();
                    level.playSound(
                        null, centerPos.x(), centerPos.y(), centerPos.z(), ModSounds.BLOCK_CUTTING_BOARD_REMOVE.get(), SoundSource.BLOCKS, 0.25F, 0.5F
                    );
                    return InteractionResult.SUCCESS;
                } else {
                    return InteractionResult.CONSUME;
                }
            } else if (cuttingBoard.canAddItem(mainHandStack)) {
                if (level.isClientSide()) {
                    return InteractionResult.CONSUME;
                }

                ItemStack remainderStack = cuttingBoard.addItem(player.getAbilities().instabuild ? mainHandStack.copy() : mainHandStack);
                if (!player.isCreative()) {
                    player.setItemSlot(EquipmentSlot.MAINHAND, remainderStack);
                }

                Vec3 centerPos = pos.getCenter();
                level.playSound(null, centerPos.x(), centerPos.y(), centerPos.z(), ModSounds.BLOCK_CUTTING_BOARD_PLACE.get(), SoundSource.BLOCKS, 1.0F, 0.8F);
                return InteractionResult.SUCCESS_SERVER;
            } else {
                return cuttingBoard.processStoredItemUsingTool(mainHandStack, player) ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
            }
        } else {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    public boolean isPossibleToRespawnInThis(BlockState state) {
        return true;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
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

        return facing == Direction.DOWN && !state.canSurvive(level, currentPos)
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, level, scheduledTickAccess, currentPos, facing, facingPos, facingState, random);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos floorPos = pos.below();
        return Block.canSupportRigidBlock(level, floorPos) || Block.canSupportCenter(level, floorPos, Direction.UP);
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        if (level.getBlockEntity(pos) instanceof CuttingBoardBlockEntity cuttingBoard) {
            ItemStack storedStack = cuttingBoard.getStoredItem();
            if (!storedStack.isEmpty()) {
                float proportions = (float)storedStack.getCount() / Math.min(cuttingBoard.getMaxStackSize(), storedStack.getMaxStackSize());
                return Mth.floor(proportions * 14.0F) + 1;
            } else {
                return 0;
            }
        } else {
            return 0;
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
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntityTypes.CUTTING_BOARD.get().create(pos, state);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    public static void spawnCuttingParticles(Level level, BlockPos pos, ItemStack stack, int count) {
        for (int i = 0; i < count; i++) {
            Vec3 vec3d = new Vec3((level.getRandom().nextFloat() - 0.5) * 0.1, Math.random() * 0.1 + 0.1, (level.getRandom().nextFloat() - 0.5) * 0.1);
            if (level instanceof ServerLevel) {
                ((ServerLevel)level)
                    .sendParticles(
                        new ItemParticleOption(ParticleTypes.ITEM, stack),
                        pos.getX() + 0.5F,
                        pos.getY() + 0.1F,
                        pos.getZ() + 0.5F,
                        1,
                        vec3d.x,
                        vec3d.y + 0.05,
                        vec3d.z,
                        0.0
                    );
            } else {
                level.addParticle(
                    new ItemParticleOption(ParticleTypes.ITEM, stack),
                    pos.getX() + 0.5F,
                    pos.getY() + 0.1F,
                    pos.getZ() + 0.5F,
                    vec3d.x,
                    vec3d.y + 0.05,
                    vec3d.z
                );
            }
        }
    }

    public static class ToolCarvingEvent {
        public static InteractionResult onSneakPlaceTool(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
            if (player.isSpectator()) {
                return InteractionResult.PASS;
            }

            BlockPos pos = hit.getBlockPos();
            ItemStack heldStack = player.getMainHandItem();
            BlockEntity tileEntity = level.getBlockEntity(pos);
            if (player.isSecondaryUseActive()
                && !heldStack.isEmpty()
                && tileEntity instanceof CuttingBoardBlockEntity
                && (heldStack.has(DataComponents.TOOL) || heldStack.getItem() instanceof TridentItem || heldStack.getItem() instanceof ShearsItem)) {
                boolean success = ((CuttingBoardBlockEntity)tileEntity).carveToolOnBoard(player.getAbilities().instabuild ? heldStack.copy() : heldStack);
                if (success) {
                    if (!player.getAbilities().instabuild) {
                        heldStack.shrink(1);
                    }

                    Vec3 centerPos = pos.getCenter();
                    level.playSound(null, centerPos.x(), centerPos.y(), centerPos.z(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
                    return InteractionResult.SUCCESS;
                }
            }

            return InteractionResult.PASS;
        }
    }
}
