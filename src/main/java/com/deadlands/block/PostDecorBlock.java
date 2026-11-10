package com.deadlands.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** An upright post that looks the same from every side (utility poles, lamp posts). */
public class PostDecorBlock extends Block {
	public static final MapCodec<PostDecorBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		DecorKind.CODEC.fieldOf("kind").forGetter(PostDecorBlock::kind),
		propertiesCodec()
	).apply(instance, PostDecorBlock::new));

	private final DecorKind kind;

	public PostDecorBlock(DecorKind kind, Properties properties) {
		super(properties);
		this.kind = kind;
	}

	public DecorKind kind() {
		return kind;
	}

	@Override
	public MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return kind.northShape();
	}
}
