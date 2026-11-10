package com.deadlands.block;

import com.deadlands.world.power.PowerGrid;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A street lamp head. Dead until the town it stands in has its power restored (see
 * {@link PowerGrid}); then it lights at night. Lamps check on random ticks, so a restored town's
 * streets come on gradually over a minute or two, block by block.
 */
public class LampHeadBlock extends FacingDecorBlock {
	public static final MapCodec<LampHeadBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		DecorKind.CODEC.fieldOf("kind").forGetter(LampHeadBlock::kind),
		propertiesCodec()
	).apply(instance, LampHeadBlock::new));
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	public LampHeadBlock(DecorKind kind, Properties properties) {
		super(kind, properties);
		registerDefaultState(defaultBlockState().setValue(LIT, false));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(LIT);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		boolean lit = level.isDarkOutside() && PowerGrid.isPowered(level, pos);
		if (state.getValue(LIT) != lit) {
			level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_CLIENTS);
		}
	}
}
