package com.huaxiaosheng.spapacks.mixin;

import com.huaxiaosheng.spapacks.client.PunchyMoves;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "punchymoves/client/PlayerAnimationEngine", remap = false)
abstract class PunchyEngineMixin {
    @Inject(method = "crouchRenderAmount", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void spaPacks$crouch(CallbackInfoReturnable<Float> cir) {
        if (PunchyMoves.stepsAside()) {
            cir.setReturnValue(Float.NaN);
        }
    }

    @Inject(
        method = {"isCustomSwimmingActive", "isCustomElytraActive", "hasCustomSwimming", "hasCustomElytra"},
        at = @At("HEAD"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private static void spaPacks$wholeBodyTurn(CallbackInfoReturnable<Boolean> cir) {
        if (PunchyMoves.stepsAside()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "prepareStationaryTurning", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void spaPacks$turnInPlace(CallbackInfo ci) {
        if (PunchyMoves.stepsAside()) {
            ci.cancel();
        }
    }

    @Inject(method = "delayedHandSwapVisual", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void spaPacks$handSwap(AbstractClientPlayer player, HumanoidArm arm, CallbackInfoReturnable<ItemStack> cir) {
        if (PunchyMoves.stepsAside()) {
            cir.setReturnValue(arm == player.getMainArm() ? player.getMainHandItem() : player.getOffhandItem());
        }
    }
}
