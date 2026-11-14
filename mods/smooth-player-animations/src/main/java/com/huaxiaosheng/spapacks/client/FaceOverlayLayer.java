package com.huaxiaosheng.spapacks.client;

import com.huaxiaosheng.spapacks.platform.Game;
import com.huaxiaosheng.spapacks.platform.Platform;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

final class FaceOverlayLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final int EMISSIVE_LIGHT = 15728880;

    private FaceOverlayLayer(RenderLayerParent<AvatarRenderState, PlayerModel> context) {
        super(context);
    }

    public void submit(PoseStack matrices, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        FaceOverlayLayer.Face face = face(this.getParentModel(), RigPlayerModel.playerOf(state), Game.partialTick(), state.yRot, state.xRot);
        if (face != null) {
            matrices.pushPose();

            try {
                this.getParentModel().head.translateAndRotate(matrices);
                collector.submitCustomGeometry(
                    matrices, RenderTypes.entityTranslucent(face.base), (entry, vertices) -> emitLater(vertices, entry, face.verts, light)
                );
                if (face.emissive != null) {
                    collector.submitCustomGeometry(
                        matrices, RenderTypes.entityTranslucent(face.emissive), (entry, vertices) -> emitLater(vertices, entry, face.verts, 15728880)
                    );
                }
            } catch (Throwable e) {
                SafeMode.fail("the face overlay", e);
            } finally {
                matrices.popPose();
            }
        }
    }

    private static void emitLater(VertexConsumer vertices, Pose entry, float[] verts, int light) {
        try {
            FaceOverlay.emit(vertices, entry, verts, light);
        } catch (Throwable e) {
            SafeMode.fail("the face overlay", e);
        }
    }

    static void install() {
        Platform.onPlayerLayer(FaceOverlayLayer::new);
    }

    private static FaceOverlayLayer.Face face(HumanoidModel<?> model, AbstractClientPlayer player, float tickDelta, float headYaw, float headPitch) {
        try {
            return faceOf(model, player, tickDelta, headYaw, headPitch);
        } catch (Throwable e) {
            SafeMode.fail("the face overlay", e);
            return null;
        }
    }

    private static FaceOverlayLayer.Face faceOf(HumanoidModel<?> model, AbstractClientPlayer player, float tickDelta, float headYaw, float headPitch) {
        if (player != null
            && model instanceof RigPlayerModel rig
            && rig.drawsFace()
            && !player.isInvisible()
            && model.head.visible
            && !FiguraCompat.hidesVanillaHead(player)) {
            float[] verts = FaceOverlay.bake(player, tickDelta, headYaw, headPitch);
            if (verts == null) {
                return null;
            }

            EtfFaceCompat.Skin skin = EtfFaceCompat.resolve(player, Game.skinTexture(player));
            return new FaceOverlayLayer.Face(verts, skin.base(), skin.emissive());
        } else {
            return null;
        }
    }

    private record Face(float[] verts, Identifier base, Identifier emissive) {
    }
}
