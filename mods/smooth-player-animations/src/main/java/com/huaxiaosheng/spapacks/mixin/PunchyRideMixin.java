package com.huaxiaosheng.spapacks.mixin;

import com.huaxiaosheng.spapacks.client.PunchyMoves;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = {"punchymoves/client/animation/PassengerRideDynamics", "punchymoves/client/render/BoatRowingPose"}, remap = false)
abstract class PunchyRideMixin {
    @Inject(method = {"isTracking", "isRower"}, at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void spaPacks$ride(CallbackInfoReturnable<Boolean> cir) {
        if (PunchyMoves.stepsAside()) {
            cir.setReturnValue(false);
        }
    }
}
