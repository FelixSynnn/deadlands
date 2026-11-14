package com.huaxiaosheng.spapacks.mixin;

import com.huaxiaosheng.spapacks.client.RigArmorModel;
import com.huaxiaosheng.spapacks.client.RigPlayerModel;
import com.huaxiaosheng.spapacks.client.SafeMode;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class PlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayer, AvatarRenderState, PlayerModel> {
    protected PlayerRendererMixin(Context ctx, PlayerModel model, float shadow) {
        super(ctx, model, shadow);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void smoothPlayerAnimations$installModel(Context ctx, boolean slim, CallbackInfo ci) {
        try {
            this.spaPacks$install(ctx, slim);
        } catch (Throwable e) {
            SafeMode.fail("installing the player model", e);
        }
    }

    private void spaPacks$install(Context ctx, boolean slim) {
        RigPlayerModel rig = new RigPlayerModel(ctx.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
        ArmorModelSet<ModelLayerLocation> layers = slim ? ModelLayers.PLAYER_SLIM_ARMOR : ModelLayers.PLAYER_ARMOR;
        HumanoidArmorLayer<AvatarRenderState, PlayerModel, RigArmorModel> armor = new HumanoidArmorLayer<>(
            this,
            new ArmorModelSet<>(
                new RigArmorModel(ctx.bakeLayer(layers.head()), 1.0F, RigArmorModel.HEAD),
                new RigArmorModel(ctx.bakeLayer(layers.chest()), 1.0F, RigArmorModel.CHEST),
                new RigArmorModel(ctx.bakeLayer(layers.legs()), 0.5F, RigArmorModel.LEGS),
                new RigArmorModel(ctx.bakeLayer(layers.feet()), 1.0F, RigArmorModel.FEET)
            ),
            ctx.getEquipmentRenderer()
        );
        int at = -1;

        for (int i = 0; i < this.layers.size(); i++) {
            if (this.layers.get(i) instanceof HumanoidArmorLayer) {
                at = i;
                break;
            }
        }

        this.model = rig;
        if (at < 0) {
            this.addLayer(armor);
        } else {
            this.layers.set(at, armor);
        }
    }

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
        at = @At("TAIL")
    )
    private void spaPacks$extracted(Avatar avatar, AvatarRenderState state, float tickDelta, CallbackInfo ci) {
        if (avatar instanceof AbstractClientPlayer player) {
            try {
                RigPlayerModel.extracted(state, player);
            } catch (Throwable e) {
                SafeMode.fail("reading the player's state", e);
            }
        }
    }
}
