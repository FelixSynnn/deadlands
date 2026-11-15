package com.huaxiaosheng.spapacks.mixin;

import com.huaxiaosheng.spapacks.client.CapeCloth;
import com.huaxiaosheng.spapacks.client.SafeMode;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerCapeModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerCapeModel.class)
abstract class PlayerCapeModelMixin {
    @Shadow
    @Final
    private ModelPart cape;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void spaPacks$cloth(AvatarRenderState state, CallbackInfo ci) {
        try {
            CapeCloth.posed(this.cape, ((HumanoidModel)(Object)this).body, state);
        } catch (Throwable e) {
            SafeMode.fail("posing the cape", e);
        }
    }
}
