package com.huaxiaosheng.spapacks.client;

import com.huaxiaosheng.spapacks.platform.Game;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;

public final class BackFollow {
    private static final Matrix4f BODY = new Matrix4f();
    private static final Matrix4f EXPECTED = new Matrix4f();
    private static final PoseStack PART = new PoseStack();

    private BackFollow() {
    }

    public static void apply(PoseStack matrices, LivingEntity entity, Object model) {
        apply(matrices, entity instanceof Player, entity.isCrouching(), model);
    }

    public static void apply(PoseStack matrices, boolean player, boolean crouching, Object model) {
        if (player && model instanceof HumanoidModel<?> biped) {
            if (!(model instanceof RigPlayerModel rig && rig.back(BODY))) {
                PART.setIdentity();
                biped.body.translateAndRotate(PART);
                BODY.set(PART.last().pose());
            }

            EXPECTED.identity();
            if (crouching) {
                EXPECTED.translate(0.0F, 0.2F, 0.0F).rotateX(0.5F);
            }

            Game.multiply(matrices, BODY.mul(EXPECTED.invert()));
        }
    }
}
