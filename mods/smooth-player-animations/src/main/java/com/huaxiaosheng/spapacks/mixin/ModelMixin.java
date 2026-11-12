package com.huaxiaosheng.spapacks.mixin;

import com.huaxiaosheng.spapacks.client.RigBend;
import com.huaxiaosheng.spapacks.client.SafeMode;
import net.minecraft.client.model.Model;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Model.class)
abstract class ModelMixin {
    @Inject(method = "setupAnim(Ljava/lang/Object;)V", at = @At("HEAD"))
    private void spaPacks$newModel(Object state, CallbackInfo ci) {
        SafeMode.run("posing a model", RigBend::end);
    }
}
