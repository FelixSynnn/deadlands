package com.deadlands.block;

import com.deadlands.npc.NpcRole;
import com.deadlands.npc.NpcSpawns;
import com.deadlands.registry.DLMarkers;
import com.deadlands.vehicle.VehicleKind;
import com.deadlands.vehicle.VehicleSpawns;
import com.mojang.serialization.MapCodec;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A technical block for structure templates and town buildings: invisible and without collision,
 * it stands where something that is not a block belongs. The first time its chunk ticks it removes
 * itself and puts that thing there: an abandoned vehicle from one of four pools, or a survivor of
 * a given role. (Entities cannot be written into the generated templates, and doing the work on
 * the first tick keeps it out of world generation.)
 */
public class SpawnMarkerBlock extends BaseEntityBlock {
	public static final MapCodec<SpawnMarkerBlock> CODEC = simpleCodec(SpawnMarkerBlock::new);
	public static final EnumProperty<Spawn> SPAWN = EnumProperty.create("spawn", Spawn.class);
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	/** What a marker turns into. */
	public enum Spawn implements StringRepresentable {
		VEHICLE_CIVILIAN, VEHICLE_MILITARY, VEHICLE_EMERGENCY, VEHICLE_FARM, VEHICLE_RUGGED, HELICOPTER,
		NPC_SETTLER, NPC_GUARD, NPC_TRADER, NPC_DOCTOR, NPC_MECHANIC, NPC_FARMER, NPC_LEADER, NPC_BANDIT, NPC_WANDERER, NPC_RESEARCHER;

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public SpawnMarkerBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(SPAWN, Spawn.VEHICLE_CIVILIAN).setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SPAWN, FACING);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MarkerEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, DLMarkers.MARKER, (l, pos, s, marker) -> resolve((ServerLevel) l, pos, s));
	}

	/** Replace the marker with what it stands for. */
	public static void resolve(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		RandomSource random = RandomSource.create(pos.asLong() ^ level.getSeed());
		Spawn spawn = state.getValue(SPAWN);
		float yaw = state.getValue(FACING).toYRot() + random.nextFloat() * 20.0F - 10.0F;
		double x = pos.getX() + 0.5;
		double z = pos.getZ() + 0.5;
		switch (spawn) {
			case VEHICLE_CIVILIAN -> VehicleSpawns.abandon(level, VehicleSpawns.pick(random), x, pos.getY(), z, yaw, random);
			case VEHICLE_MILITARY -> VehicleSpawns.abandon(level, random.nextFloat() < 0.7F ? VehicleKind.MILITARY_JEEP : VehicleKind.CARGO_TRUCK, x, pos.getY(), z, yaw, random);
			case VEHICLE_EMERGENCY -> VehicleSpawns.abandon(level,
				new VehicleKind[] {VehicleKind.AMBULANCE, VehicleKind.POLICE_CAR, VehicleKind.FIRE_TRUCK}[random.nextInt(3)], x, pos.getY(), z, yaw, random);
			case HELICOPTER -> {
				com.deadlands.story.HelicopterEntity helicopter = com.deadlands.registry.DLStory.HELICOPTER.create(level, net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
				if (helicopter != null) {
					helicopter.snapTo(x, pos.getY(), z, yaw, 0.0F);
					level.addFreshEntity(helicopter);
				}
			}
			case VEHICLE_RUGGED -> VehicleSpawns.abandon(level,
				new VehicleKind[] {VehicleKind.PICKUP, VehicleKind.SUV, VehicleKind.MILITARY_JEEP, VehicleKind.MOTORCYCLE}[random.nextInt(4)], x, pos.getY(), z, yaw, random);
			case VEHICLE_FARM -> VehicleSpawns.abandon(level, random.nextFloat() < 0.6F ? VehicleKind.TRACTOR : VehicleKind.PICKUP, x, pos.getY(), z, yaw, random);
			default -> NpcSpawns.spawn(level, NpcRole.valueOf(spawn.name().substring(4)), pos, yaw, random);
		}
	}

	/** The marker's block entity: it exists only so the marker can tick once. */
	public static class MarkerEntity extends BlockEntity {
		public MarkerEntity(BlockPos pos, BlockState state) {
			super(DLMarkers.MARKER, pos, state);
		}
	}
}
