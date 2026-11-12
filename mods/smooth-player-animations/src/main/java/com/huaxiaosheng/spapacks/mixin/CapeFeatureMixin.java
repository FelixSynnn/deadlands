package com.huaxiaosheng.spapacks.mixin;

import com.huaxiaosheng.spapacks.client.BackFollow;
import com.huaxiaosheng.spapacks.client.CapeCloth;
import com.huaxiaosheng.spapacks.client.SafeMode;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CapeLayer.class)
abstract class CapeFeatureMixin {
    @Inject(
        method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = Shift.AFTER)
    )
    private void spaPacks$followBack(
        PoseStack matrices, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot, CallbackInfo ci
    ) {
        try {
            BackFollow.apply(matrices, true, state.isCrouching, ((RenderLayer)(Object)this).getParentModel());
            CapeCloth.layer(state, state.isCrouching, matrices);
        } catch (Throwable e) {
            SafeMode.fail("the cape layer", e);
        }
    }

    @Inject(
        method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"),
        require = 0
    )
    private void spaPacks$overArmor(
        PoseStack matrices, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot, CallbackInfo ci
    ) {
        try {
            CapeCloth.shifted(state);
        } catch (Throwable e) {
            SafeMode.fail("the cape layer", e);
        }
    }
}
