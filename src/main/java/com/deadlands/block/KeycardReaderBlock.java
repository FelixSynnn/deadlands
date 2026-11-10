package com.deadlands.block;

import com.deadlands.item.KeycardItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A wall-mounted keycard reader (level 1 green, 2 blue, 3 red). Swiping a keycard of its level or
 * higher powers it for three seconds, like a button, which opens the security door beside it. A
 * bare hand or the wrong card gets a refusal. It cannot be broken or pushed.
 */
public class KeycardReaderBlock extends ButtonBlock {
	public static final MapCodec<ButtonBlock> CODEC = simpleCodec(KeycardReaderBlock::new);
	public static final IntegerProperty LEVEL = IntegerProperty.create("level", 1, 3);
	private static final int OPEN_TICKS = 60;

	public KeycardReaderBlock(Properties properties) {
		super(BlockSetType.IRON, OPEN_TICKS, properties);
		registerDefaultState(defaultBlockState().setValue(LEVEL, 1));
	}

	@Override
	public MapCodec<ButtonBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(LEVEL);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			player.displayClientMessage(Component.translatable("message.deadlands.reader.needs", KeycardItem.levelName(state.getValue(LEVEL))), true);
			level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.6F, 0.6F);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (!(stack.getItem() instanceof KeycardItem card)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (card.accessLevel() < state.getValue(LEVEL)) {
			player.displayClientMessage(Component.translatable("message.deadlands.reader.denied", KeycardItem.levelName(state.getValue(LEVEL))), true);
			level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.7F, 0.5F);
			return InteractionResult.SUCCESS;
		}
		if (!state.getValue(POWERED)) {
			press(state, level, pos, player);
			player.displayClientMessage(Component.translatable("message.deadlands.reader.granted"), true);
			level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.6F, 1.6F);
		}
		return InteractionResult.SUCCESS;
	}
}
