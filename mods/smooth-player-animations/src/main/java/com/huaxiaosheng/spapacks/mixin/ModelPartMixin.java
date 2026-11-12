package com.huaxiaosheng.spapacks.mixin;

import com.huaxiaosheng.spapacks.client.RigBend;
import com.huaxiaosheng.spapacks.client.RigRoots;
import com.huaxiaosheng.spapacks.client.SafeMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModelPart.class, priority = 500)
abstract class ModelPartMixin implements RigRoots.Holder {
    @Unique
    private RigRoots.Drawn spaPacks$drawn;

    @Override
    public RigRoots.Drawn spaPacks$drawn() {
        return this.spaPacks$drawn;
    }

    @Override
    public void spaPacks$setDrawn(RigRoots.Drawn model) {
        this.spaPacks$drawn = model;
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", at = @At("HEAD"), cancellable = true)
    private void spaPacks$bend(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color, CallbackInfo ci) {
        if (!SafeMode.off()) {
            try {
                ModelPart part = (ModelPart)(Object)this;
                if (RigRoots.draw(part, matrices, vertices, light, overlay, color) || RigBend.render(part, matrices, vertices, light, overlay, color)) {
                    ci.cancel();
                }
            } catch (Throwable e) {
                SafeMode.fail("drawing a model part", e);
            }
        }
    }
}
