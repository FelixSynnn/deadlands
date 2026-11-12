package com.huaxiaosheng.spapacks.mixin;

import com.huaxiaosheng.spapacks.client.BackFollow;
import com.huaxiaosheng.spapacks.client.SafeMode;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = {"pw/lakuna/elytra_trinket/ElytraTrinketFeatureRenderer", "net/regexion/elytra_trinket_updated/ElytraTrinketFeatureRenderer"}, remap = false)
abstract class ElytraTrinketMixin {
    @Inject(method = {"render", "submit"}, at = @At("HEAD"), require = 0, remap = false)
    private void spaPacks$followBack(
        PoseStack matrices, SubmitNodeCollector collector, int light, HumanoidRenderState state, float yRot, float xRot, CallbackInfo ci
    ) {
        matrices.pushPose();

        try {
            BackFollow.apply(matrices, state instanceof AvatarRenderState, state.isCrouching, ((RenderLayer)(Object)this).getParentModel());
        } catch (Throwable e) {
            SafeMode.fail("an accessory elytra layer", e);
        }
    }

    @Inject(method = {"render", "submit"}, at = @At("RETURN"), require = 0, remap = false)
    private void spaPacks$followBackEnd(
        PoseStack matrices, SubmitNodeCollector collector, int light, HumanoidRenderState state, float yRot, float xRot, CallbackInfo ci
    ) {
        matrices.popPose();
    }
}
