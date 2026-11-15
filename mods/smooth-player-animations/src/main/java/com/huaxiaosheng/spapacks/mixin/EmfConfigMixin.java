package com.huaxiaosheng.spapacks.mixin;

import com.huaxiaosheng.spapacks.client.ModConfig;
import com.huaxiaosheng.spapacks.client.SafeMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "traben/entity_model_features/config/EMFConfig", remap = false)
abstract class EmfConfigMixin {
    @Inject(method = "isModelDisabled", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void spaPacks$keepPlayerOff(String id, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (ModConfig.get().enabled && ("player".equals(id) || "player_slim".equals(id) || "player_cape".equals(id) || "elytra".equals(id))) {
                cir.setReturnValue(true);
            }
        } catch (Throwable e) {
            SafeMode.fail("keeping EMF off the player", e);
        }
    }
}
