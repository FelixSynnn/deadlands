package com.deadlands.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnderEyeItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The End is closed: an eye of ender neither flies toward a stronghold nor fits an End portal frame. */
@Mixin(EnderEyeItem.class)
public abstract class EnderEyeItemMixin {
	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void deadlands$inertEye(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		cir.setReturnValue(InteractionResult.PASS);
	}

	@Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
	private void deadlands$inertFrame(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
		cir.setReturnValue(InteractionResult.PASS);
	}
}
