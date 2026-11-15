package com.huaxiaosheng.spapacks.mixin;

import com.huaxiaosheng.spapacks.client.PunchyFaces;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "punchyfaces/client/face/FaceLayer", remap = false)
abstract class PunchyFaceMixin {
    @Inject(method = "render", at = @At("HEAD"), require = 0, remap = false)
    private void spaPacks$liftFace(CallbackInfo ci) {
        PunchyFaces.begin(((RenderLayer)(Object)this).getParentModel());
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0, remap = false)
    private void spaPacks$restoreFace(CallbackInfo ci) {
        PunchyFaces.end();
    }
}
