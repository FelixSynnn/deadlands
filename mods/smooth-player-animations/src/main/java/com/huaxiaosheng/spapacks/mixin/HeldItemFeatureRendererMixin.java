package com.huaxiaosheng.spapacks.mixin;

import com.huaxiaosheng.spapacks.client.ItemDissolve;
import com.huaxiaosheng.spapacks.client.PlayerAnimations;
import com.huaxiaosheng.spapacks.client.RigPlayerModel;
import com.huaxiaosheng.spapacks.client.SafeMode;
import com.huaxiaosheng.spapacks.client.VanillaTransforms;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
abstract class HeldItemFeatureRendererMixin {
    private RigPlayerModel spaPacks$rig() {
        return ((RenderLayer)(Object)this).getParentModel() instanceof RigPlayerModel rig ? rig : null;
    }

    private static float spaPacks$reveal(AbstractClientPlayer player, HumanoidArm arm) {
        return player != null && arm == player.getMainArm() ? PlayerAnimations.itemReveal(player) : 1.0F;
    }

    @Inject(
        method = "submitArmWithItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/ArmedModel;translateToHand(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            shift = Shift.AFTER
        )
    )
    private void spaPacks$itemOffset(CallbackInfo ci, @Local(argsOnly = true) HumanoidArm arm, @Local(argsOnly = true) PoseStack matrices) {
        try {
            RigPlayerModel rig = this.spaPacks$rig();
            if (rig != null) {
                VanillaTransforms.itemOffset(matrices, rig.item(arm));
            }
        } catch (Throwable e) {
            SafeMode.fail("the held item's transform", e);
        }
    }

    @Inject(
        method = "submitArmWithItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"
        )
    )
    private void spaPacks$itemTurn(CallbackInfo ci, @Local(argsOnly = true) HumanoidArm arm, @Local(argsOnly = true) PoseStack matrices) {
        try {
            RigPlayerModel rig = this.spaPacks$rig();
            if (rig != null) {
                VanillaTransforms.itemTurn(matrices, rig.item(arm));
            }
        } catch (Throwable e) {
            SafeMode.fail("the held item's transform", e);
        }
    }

    @WrapOperation(
        method = "submitArmWithItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"
        )
    )
    private void spaPacks$dissolve(
        ItemStackRenderState item,
        PoseStack matrices,
        SubmitNodeCollector collector,
        int light,
        int overlay,
        int outline,
        Operation<Void> original,
        @Local(argsOnly = true) ArmedEntityRenderState state,
        @Local(argsOnly = true) HumanoidArm arm
    ) {
        float reveal;
        try {
            reveal = spaPacks$reveal(RigPlayerModel.playerOf(state), arm);
        } catch (Throwable e) {
            SafeMode.fail("the held item's draw-in", e);
            reveal = 1.0F;
        }

        if (reveal >= 1.0F || SafeMode.off()) {
            original.call(new Object[]{item, matrices, collector, light, overlay, outline});
        } else if (reveal > 0.0F) {
            try {
                ItemDissolve.submit(collector, matrices, reveal, standIn -> original.call(new Object[]{item, matrices, standIn, light, overlay, outline}));
            } catch (Throwable e) {
                SafeMode.fail("the held item's draw-in", e);
            }
        }
    }
}
