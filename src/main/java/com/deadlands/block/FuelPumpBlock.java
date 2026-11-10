package com.deadlands.block;

import com.deadlands.item.FuelCanItem;
import com.deadlands.vehicle.FuelPumpBlockEntity;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * A fuel pump. Each pump has what was left in its tank when the world ended: most are dry, some
 * still hold tens of litres, and what is taken is gone for good (saved in
 * {@link FuelPumpBlockEntity}). Use a fuel can on it to fill the can; use it empty-handed to read
 * the gauge.
 */
public class FuelPumpBlock extends FacingDecorBlock implements EntityBlock {
	public static final MapCodec<FuelPumpBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		DecorKind.CODEC.fieldOf("kind").forGetter(FuelPumpBlock::kind),
		propertiesCodec()
	).apply(instance, FuelPumpBlock::new));

	public FuelPumpBlock(DecorKind kind, Properties properties) {
		super(kind, properties);
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FuelPumpBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (!(stack.getItem() instanceof FuelCanItem)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FuelPumpBlockEntity pump) {
			float room = FuelCanItem.CAPACITY - FuelCanItem.litres(stack);
			float taken = pump.take(room);
			if (taken > 0.0F) {
				FuelCanItem.setLitres(stack, FuelCanItem.litres(stack) + taken);
				level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.8F, 0.8F);
				player.displayClientMessage(Component.translatable("message.deadlands.pump.filled", Math.round(taken), Math.round(pump.litres())), true);
			} else {
				player.displayClientMessage(Component.translatable(room <= 0.0F ? "message.deadlands.pump.can_full" : "message.deadlands.pump.dry"), true);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FuelPumpBlockEntity pump) {
			player.displayClientMessage(pump.litres() > 0.0F
				? Component.translatable("message.deadlands.pump.gauge", Math.round(pump.litres()))
				: Component.translatable("message.deadlands.pump.dry"), true);
		}
		return InteractionResult.SUCCESS;
	}
}
