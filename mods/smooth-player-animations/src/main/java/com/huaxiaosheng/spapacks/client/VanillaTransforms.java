package com.huaxiaosheng.spapacks.client;

import com.huaxiaosheng.spapacks.anim.pose.Poses;
import com.huaxiaosheng.spapacks.platform.Game;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class VanillaTransforms {
    private static final Quaternionf Q = new Quaternionf();
    private static final Matrix3f M3 = new Matrix3f();
    private static final Vector3f ANGLES = new Vector3f();
    private static final Matrix4f BODY = new Matrix4f();
    private static final Matrix4f FLIP = new Matrix4f().scale(-1.0F, -1.0F, 1.0F).scale(0.9375F).translate(0.0F, -1.501F, 0.0F);
    private static final Matrix4f UNFLIP = new Matrix4f(FLIP).invert();
    private static final Matrix4f X = new Matrix4f();
    private static final Quaternionf PART_TURN = new Quaternionf();

    private VanillaTransforms() {
    }

    private static boolean identity(float[] b) {
        return b[0] == 0.0F && b[1] == 0.0F && b[2] == 0.0F && Math.abs(b[6]) >= 0.999999F && b[7] == 1.0F && b[8] == 1.0F && b[9] == 1.0F;
    }

    private static Vector3f angles(float[] b) {
        return Poses.eulerZYX(b, Q, M3, ANGLES);
    }

    public static void applyRoot(PoseStack matrices, float[] b) {
        if (b != null && !identity(b)) {
            Vector3f a = angles(b);
            BODY.identity()
                .scale(b[7], b[8], b[9])
                .translate(-b[0] / 16.0F, b[1] / 16.0F + 0.75F, b[2] / 16.0F)
                .rotateZYX(a.z, -a.y, -a.x)
                .translate(0.0F, -0.75F, 0.0F);
            X.set(UNFLIP).mul(BODY).mul(FLIP);
            Game.multiply(matrices, X);
        }
    }

    public static void applyPart(PoseStack matrices, float[] v) {
        if (v != null
            && (v[0] != 0.0F || v[1] != 0.0F || v[2] != 0.0F || v[3] != 0.0F || v[4] != 0.0F || v[5] != 0.0F || v[6] != 1.0F || v[7] != 1.0F || v[8] != 1.0F)) {
            matrices.translate(v[3] / 16.0F, v[4] / 16.0F, v[5] / 16.0F);
            Game.rotate(matrices, PART_TURN.rotationZYX(v[2], v[1], v[0]));
            matrices.scale(v[6], v[7], v[8]);
        }
    }

    public static void itemOffset(PoseStack matrices, float[] b) {
        if (b != null && (b[0] != 0.0F || b[1] != 0.0F || b[2] != 0.0F)) {
            matrices.translate(b[0] / 16.0F, -b[1] / 16.0F, b[2] / 16.0F);
        }
    }

    public static void itemTurn(PoseStack matrices, float[] b) {
        if (b != null && !identity(b)) {
            Vector3f a = angles(b);
            if (a.y != 0.0F) {
                Game.rotate(matrices, Axis.ZP.rotation(-a.y));
            }

            if (a.z != 0.0F) {
                Game.rotate(matrices, Axis.YP.rotation(-a.z));
            }

            if (a.x != 0.0F) {
                Game.rotate(matrices, Axis.XP.rotation(-a.x));
            }

            matrices.scale(b[7], b[8], b[9]);
        }
    }
}
