package com.deadlands.container;

import com.deadlands.registry.DLBlockEntities;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A physical loot container (crate, cabinet, locker...). One class serves every {@link ContainerKind}.
 *
 * <p>Block state: {@code facing} (front), {@code condition} (textures, loot amount) and {@code ajar}
 * (left slightly open, for environmental storytelling).
 */
public class LootContainerBlock extends BaseEntityBlock {
	public static final MapCodec<LootContainerBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		ContainerKind.CODEC.fieldOf("kind").forGetter(LootContainerBlock::kind),
		propertiesCodec()
	).apply(instance, LootContainerBlock::new));
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<ContainerCondition> CONDITION = EnumProperty.create("condition", ContainerCondition.class);
	public static final BooleanProperty AJAR = BooleanProperty.create("ajar");

	private final ContainerKind kind;
	private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

	public LootContainerBlock(ContainerKind kind, Properties properties) {
		super(properties);
		this.kind = kind;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			shapes.put(direction, rotate(kind.northShape(), direction));
		}
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CONDITION, ContainerCondition.CLEAN).setValue(AJAR, false));
	}

	private static VoxelShape rotate(VoxelShape north, Direction to) {
		VoxelShape[] result = {Shapes.empty()};
		int turns = (to.get2DDataValue() - Direction.NORTH.get2DDataValue() + 4) % 4;
		north.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
			double ax1 = x1, az1 = z1, ax2 = x2, az2 = z2;
			for (int i = 0; i < turns; i++) {
				// 90 degrees clockwise seen from above: (x, z) -> (1 - z, x)
				double nx1 = 1 - az2, nz1 = ax1, nx2 = 1 - az1, nz2 = ax2;
				ax1 = nx1;
				az1 = nz1;
				ax2 = nx2;
				az2 = nz2;
			}
			result[0] = Shapes.or(result[0], Shapes.box(ax1, y1, az1, ax2, y2, az2));
		});
		return result[0];
	}

	public ContainerKind kind() {
		return kind;
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, CONDITION, AJAR);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapes.get(state.getValue(FACING));
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer
			&& level.getBlockEntity(pos) instanceof LootContainerBlockEntity container) {
			if (container.tryUnlock(serverPlayer)) {
				player.openMenu(container);
			}
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * A locked container cannot be broken open by hand in survival: find the key (or blast it).
	 * Runs on both sides; the client knows the lock from the block entity's update tag.
	 */
	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		if (isLockedFor(player, level, pos)) {
			return 0.0F;
		}
		return super.getDestroyProgress(state, player, level, pos);
	}

	@Override
	protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
		if (player instanceof ServerPlayer serverPlayer && isLockedFor(player, level, pos)
			&& level.getBlockEntity(pos) instanceof LootContainerBlockEntity container) {
			serverPlayer.displayClientMessage(Component.translatable("lock.deadlands.locked", container.getDisplayName(), container.lock().requirement()), true);
		}
	}

	private static boolean isLockedFor(Player player, BlockGetter level, BlockPos pos) {
		return !player.isCreative() && level.getBlockEntity(pos) instanceof LootContainerBlockEntity container
			&& container.lock().isLocked();
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (level.getBlockEntity(pos) instanceof LootContainerBlockEntity container) {
			container.recheckOpen();
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		Containers.updateNeighboursAfterDestroy(state, level, pos);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new LootContainerBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? createTickerHelper(type, DLBlockEntities.LOOT_CONTAINER, LootContainerBlockEntity::clientTick) : null;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}
}
