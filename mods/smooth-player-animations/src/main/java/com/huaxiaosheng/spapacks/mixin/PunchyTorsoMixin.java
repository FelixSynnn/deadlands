package com.huaxiaosheng.spapacks.mixin;

import com.huaxiaosheng.spapacks.client.PunchyMoves;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "punchymoves/client/render/LegacyTorsoAttachments", remap = false)
abstract class PunchyTorsoMixin {
    @Inject(method = "body", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void spaPacks$elytra(PoseStack matrices, ModelPart body, Runnable draw, CallbackInfo ci) {
        if (PunchyMoves.backIsOurs()) {
            ci.cancel();
            draw.run();
        }
    }

    @Inject(method = "cape", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void spaPacks$cape(PoseStack matrices, ModelPart body, ModelPart cape, boolean crouching, boolean chest, Runnable draw, CallbackInfo ci) {
        if (PunchyMoves.backIsOurs()) {
            ci.cancel();
            draw.run();
        }
    }
}
