package com.deadlands.block;

import com.deadlands.registry.DLStory;
import com.deadlands.registry.DLVehicles;
import com.deadlands.skill.Skill;
import com.deadlands.skill.Skills;
import com.deadlands.story.Radio;
import com.deadlands.story.StoryState;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The transmitter cabinet in a radio tower's hut. Found dead; using radio parts on it while carrying
 * a wrench brings it back. Each repaired tower is counted for the world: more of the radio comes
 * through, the military and unknown frequencies open up, and Imlilylol can reach the others.
 */
public class RadioTransmitterBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<RadioTransmitterBlock> CODEC = simpleCodec(RadioTransmitterBlock::new);
	public static final BooleanProperty REPAIRED = BooleanProperty.create("repaired");
	private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 2.0, 15.0, 15.0, 14.0);

	public RadioTransmitterBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(REPAIRED, false));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, REPAIRED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			player.displayClientMessage(Component.translatable(state.getValue(REPAIRED) ? "message.deadlands.radio.transmitter_on"
				: "message.deadlands.radio.transmitter_dead"), true);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (!stack.is(DLStory.RADIO_PARTS)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (state.getValue(REPAIRED)) {
			player.displayClientMessage(Component.translatable("message.deadlands.radio.transmitter_on"), true);
			return InteractionResult.SUCCESS;
		}
		if (!player.getInventory().contains(new ItemStack(DLVehicles.WRENCH))) {
			player.displayClientMessage(Component.translatable("vehicle.deadlands.need_wrench"), true);
			return InteractionResult.SUCCESS;
		}
		stack.consume(1, player);
		level.setBlock(pos, state.setValue(REPAIRED, true), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, 1.4F);
		if (level instanceof ServerLevel server && StoryState.get(server.getServer()).addTower(pos)) {
			ServerPlayer by = player instanceof ServerPlayer survivor ? survivor : null;
			if (by != null) {
				Skills.award(by, Skill.ENGINEER, 60);
			}
			Radio.towerRepaired(server, by);
		}
		return InteractionResult.SUCCESS;
	}
}
