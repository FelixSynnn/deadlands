package com.huaxiaosheng.spapacks.mixin;

import com.huaxiaosheng.spapacks.client.PunchyMoves;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "punchymoves/client/render/HeldItemAnimation", remap = false)
abstract class PunchyHeldItemMixin {
    @Inject(method = "apply", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void spaPacks$heldItem(CallbackInfo ci) {
        if (PunchyMoves.stepsAside()) {
            ci.cancel();
        }
    }
}
