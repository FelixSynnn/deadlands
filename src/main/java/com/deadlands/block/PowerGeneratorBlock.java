package com.deadlands.block;

import com.deadlands.registry.DLItems;
import com.deadlands.world.power.PowerGrid;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The generator in a substation or power station. Found dead with a blown fuse; using a Fuse on it
 * starts it and restores power to every town within reach ({@link PowerGrid}): their street lamps
 * come on at night, which keeps the infected from appearing on lit streets, and the town card says
 * so. The restoration is saved with the world.
 */
public class PowerGeneratorBlock extends FacingDecorBlock {
	public static final MapCodec<PowerGeneratorBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		DecorKind.CODEC.fieldOf("kind").forGetter(PowerGeneratorBlock::kind),
		propertiesCodec()
	).apply(instance, PowerGeneratorBlock::new));
	public static final BooleanProperty RUNNING = BooleanProperty.create("running");

	public PowerGeneratorBlock(DecorKind kind, Properties properties) {
		super(kind, properties);
		registerDefaultState(defaultBlockState().setValue(RUNNING, false));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(RUNNING);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (state.getValue(RUNNING) || !stack.is(DLItems.FUSE)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level instanceof ServerLevel server) {
			// Engineers sometimes get it going without burning the fuse.
			if (player.getRandom().nextFloat() >= com.deadlands.skill.Skills.keepFuseChance(player)) {
				stack.consume(1, player);
			}
			level.setBlock(pos, state.setValue(RUNNING, true), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.6F);
			int towns = PowerGrid.restore(server, pos, player);
			if (towns > 0 && player instanceof net.minecraft.server.level.ServerPlayer survivor) {
				com.deadlands.skill.Skills.award(survivor, com.deadlands.skill.Skill.ENGINEER, com.deadlands.skill.Skills.XP_POWER_RESTORED);
			}
			player.displayClientMessage(towns > 0
				? Component.translatable("message.deadlands.power.restored", towns)
				: Component.translatable("message.deadlands.power.no_town"), true);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			player.displayClientMessage(Component.translatable(state.getValue(RUNNING)
				? "message.deadlands.power.running" : "message.deadlands.power.needs_fuse"), true);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(RUNNING) && random.nextInt(4) == 0) {
			level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 1.0, pos.getZ() + 0.3 + random.nextDouble() * 0.4,
				0.0, 0.03, 0.0);
		}
		if (state.getValue(RUNNING) && random.nextInt(40) == 0) {
			level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.4F, 0.5F, false);
		}
	}
}
