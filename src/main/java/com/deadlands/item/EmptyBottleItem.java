package com.deadlands.item;

import com.deadlands.registry.DLSurvivalItems;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** An empty plastic bottle. Used on open water it fills with dirty water (purify it before drinking). */
public class EmptyBottleItem extends Item {
	public EmptyBottleItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		if (!level.mayInteract(player, pos) || !level.getFluidState(pos).is(FluidTags.WATER)) {
			return InteractionResult.PASS;
		}
		level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1.0F, 1.0F);
		ItemStack filled = ItemUtils.createFilledResult(stack, player, new ItemStack(DLSurvivalItems.DIRTY_WATER_BOTTLE));
		return InteractionResult.SUCCESS.heldItemTransformedTo(filled);
	}
}
