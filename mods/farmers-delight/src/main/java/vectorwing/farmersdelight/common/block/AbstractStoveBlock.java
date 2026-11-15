package vectorwing.farmersdelight.common.block;

import java.util.Optional;
import net.fabricmc.fabric.api.registry.LandPathNodeTypesRegistry;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import vectorwing.farmersdelight.common.block.entity.AbstractStoveBlockEntity;
import vectorwing.farmersdelight.common.registry.ModDamageTypes;
import vectorwing.farmersdelight.common.utility.MathUtils;
import vectorwing.farmersdelight.refabricated.ItemAbility;

public abstract class AbstractStoveBlock extends BaseEntityBlock {
    public static final Property<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape GRILLING_AREA = Block.box(3.0, 0.0, 3.0, 13.0, 1.0, 13.0);

    public AbstractStoveBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
        LandPathNodeTypesRegistry.registerDynamic(this, (state, world, pos, neighbor) -> {
            AbstractStoveBlock block = (AbstractStoveBlock)state.getBlock();
            return block.getBlockPathType(state, world, pos, null);
        });
    }

    @Override
    public InteractionResult useItemOn(
        ItemStack heldStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit
    ) {
        if (state.getValue(LIT)) {
            InteractionResult extinguishResult = this.tryToExtinguish(heldStack, state, level, pos, player, hand, hit);
            if (extinguishResult != InteractionResult.TRY_WITH_EMPTY_HAND) {
                return extinguishResult;
            }
        } else {
            InteractionResult igniteResult = this.tryToIgnite(heldStack, state, level, pos, player, hand, hit);
            if (igniteResult != InteractionResult.TRY_WITH_EMPTY_HAND) {
                return igniteResult;
            }
        }

        return this.tryToPlaceFoodItem(heldStack, state, level, pos, player, hand, hit);
    }

    protected InteractionResult tryToIgnite(
        ItemStack heldStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit
    ) {
        Item heldItem = heldStack.getItem();
        if (heldItem instanceof FlintAndSteelItem) {
            if (!level.isClientSide()) {
                level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, MathUtils.RAND.nextFloat() * 0.4F + 0.8F);
            }

            this.ignite(player, level, pos, state);
            heldStack.hurtAndBreak(1, player, hand.asEquipmentSlot());
            return InteractionResult.SUCCESS;
        } else if (heldItem instanceof FireChargeItem) {
            if (!level.isClientSide()) {
                level.playSound(
                    null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, (MathUtils.RAND.nextFloat() - MathUtils.RAND.nextFloat()) * 0.2F + 1.0F
                );
            }

            this.ignite(player, level, pos, state);
            if (!player.getAbilities().instabuild) {
                heldStack.shrink(1);
            }

            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
    }

    protected InteractionResult tryToExtinguish(
        ItemStack heldStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit
    ) {
        if (ItemAbility.SHOVEL_DIG.canPerformAction(heldStack)) {
            if (!level.isClientSide()) {
                level.levelEvent(null, 1009, pos, 0);
            }

            this.extinguish(player, level, pos, state);
            heldStack.hurtAndBreak(1, player, hand.asEquipmentSlot());
            return InteractionResult.SUCCESS;
        } else if (heldStack.is(ConventionalItemTags.WATER_BUCKETS)) {
            if (!level.isClientSide()) {
                level.playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1.0F, 1.0F);
            }

            this.extinguish(player, level, pos, state);
            ItemStack template = heldStack.getRecipeRemainder();
            if (!player.getAbilities().instabuild && !template.isEmpty()) {
                player.setItemInHand(hand, template.copy());
            }

            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
    }

    protected InteractionResult tryToPlaceFoodItem(
        ItemStack heldStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit
    ) {
        if (isStoveTopCovered(level, pos, state)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        if (level.getBlockEntity(pos) instanceof AbstractStoveBlockEntity stoveEntity) {
            if (level.isClientSide()) {
                return InteractionResult.CONSUME;
            }

            Optional<? extends RecipeHolder<? extends AbstractCookingRecipe>> maybeRecipe = stoveEntity.getCookingRecipe(heldStack);
            if (maybeRecipe.isEmpty()) {
                return InteractionResult.TRY_WITH_EMPTY_HAND;
            }

            boolean placeFoodSuccess = stoveEntity.placeFood(
                player, player.getAbilities().instabuild ? heldStack.copy() : heldStack, (RecipeHolder<? extends AbstractCookingRecipe>)maybeRecipe.get()
            );
            if (!placeFoodSuccess) {
                return InteractionResult.CONSUME;
            }

            level.playSound(null, pos, SoundEvents.LANTERN_PLACE, SoundSource.BLOCKS, 0.5F, 1.0F);
            return InteractionResult.SUCCESS_SERVER;
        } else {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
    }

    public void ignite(@Nullable Entity entity, LevelAccessor level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof AbstractStoveBlockEntity stoveEntity) {
            stoveEntity.ignite();
        }

        if (!level.isClientSide()) {
            BlockState newState = state.setValue(LIT, true);
            level.setBlock(pos, newState, 11);
            level.gameEvent(entity, GameEvent.BLOCK_CHANGE, pos);
        }
    }

    public void extinguish(@Nullable Entity entity, LevelAccessor level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof AbstractStoveBlockEntity stoveEntity) {
            stoveEntity.extinguish();
        }

        if (!level.isClientSide()) {
            BlockState newState = state.setValue(LIT, false);
            level.setBlock(pos, newState, 11);
            level.gameEvent(entity, GameEvent.BLOCK_CHANGE, pos);
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(LIT, true);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        this.burnEntitySteppingOnStove(level, pos, state, entity);
        super.stepOn(level, pos, state, entity);
    }

    protected void burnEntitySteppingOnStove(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (state.getValue(LIT)) {
            if (entity.getBoundingBox().intersects(GRILLING_AREA.bounds().move(pos.above()))) {
                if (!entity.isSteppingCarefully()) {
                    if (entity instanceof LivingEntity) {
                        entity.hurt(ModDamageTypes.getSimpleDamageSource(level, ModDamageTypes.STOVE_BURN), 1.0F);
                    }
                }
            }
        }
    }

    public static boolean isStoveTopCovered(Level level, BlockPos pos, BlockState stoveState) {
        if (!(stoveState.getBlock() instanceof StoveBlock)) {
            return false;
        }

        BlockPos abovePos = pos.above();
        BlockState aboveState = level.getBlockState(abovePos);
        return Shapes.joinIsNotEmpty(GRILLING_AREA, aboveState.getShape(level, abovePos), BooleanOp.AND);
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, LIT);
    }

    protected static <T extends BlockEntity> @Nullable BlockEntityTicker<T> createStoveTicker(
        BlockEntityType<T> actual, BlockEntityType<? extends AbstractStoveBlockEntity> expected
    ) {
        return createTickerHelper(actual, expected, (level, pos, state, entity) -> {
            if (level instanceof ServerLevel serverLevel) {
                AbstractStoveBlockEntity.serverTick(serverLevel, pos, state, entity);
            }
        });
    }

    public @Nullable PathType getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, @Nullable Mob entity) {
        return state.getValue(LIT) ? PathType.DAMAGE_FIRE : null;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
