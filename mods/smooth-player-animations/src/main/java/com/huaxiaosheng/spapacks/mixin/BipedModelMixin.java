package com.huaxiaosheng.spapacks.mixin;

import com.huaxiaosheng.spapacks.client.RigPlayerModel;
import com.huaxiaosheng.spapacks.client.SafeMode;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HumanoidModel.class, priority = 500)
public abstract class BipedModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("RETURN"))
    private void smoothPlayerAnimations$vanillaPose(HumanoidRenderState state, CallbackInfo ci) {
        try {
            if ((Object)this instanceof RigPlayerModel model) {
                model.captureVanilla();
            } else if (!SafeMode.off()) {
                RigPlayerModel.follow((HumanoidModel<?>)(Object)this, state);
            }
        } catch (Throwable e) {
            SafeMode.fail("posing a humanoid model", e);
        }
    }
}
