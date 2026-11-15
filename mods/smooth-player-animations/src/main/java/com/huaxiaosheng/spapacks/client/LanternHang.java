package com.huaxiaosheng.spapacks.client;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class LanternHang {
    private static final Vector3f[] RING = new Vector3f[2];
    private static final Vector3f[] DOWN = new Vector3f[2];
    private static final Vector3f[] GRIP = new Vector3f[]{new Vector3f(-0.0625F, 0.5350421F, -0.04205085F), new Vector3f(0.0625F, 0.5350421F, -0.04205085F)};
    private static final Vector3f ring = new Vector3f();
    private static final Vector3f hang = new Vector3f();
    private static final Vector3f target = new Vector3f();
    private static final Quaternionf turn = new Quaternionf();
    private static final Matrix4f about = new Matrix4f();

    private LanternHang() {
    }

    static void hang(Matrix4f hand, boolean right, float pitch, float roll) {
        int side = right ? 0 : 1;
        Vector3f grip = GRIP[side];
        Vector3f at = RING[side];
        hand.translate(grip.x - at.x, grip.y - at.y, grip.z - at.z);
        hand.transformPosition(at, ring);
        hand.transformDirection(DOWN[side], hang).normalize();
        float cp = (float)Math.cos(pitch);
        target.set(cp * (float)Math.sin(roll), cp * (float)Math.cos(roll), (float)Math.sin(pitch));
        turn.rotationTo(hang, target);
        about.translation(ring).rotate(turn).translate(-ring.x, -ring.y, -ring.z);
        hand.mulLocal(about);
    }

    static {
        for (int side = 0; side < 2; side++) {
            Matrix4f m = new Matrix4f()
                .rotateX((float)Math.toRadians(-90.0))
                .rotateY((float)Math.toRadians(180.0))
                .translate((side == 0 ? 1.0F : -1.0F) / 16.0F, 0.125F, -0.625F)
                .translate(0.0F, 0.15625F, 0.0F)
                .rotate(new Quaternionf().rotationXYZ((float)Math.toRadians(75.0), (float)Math.toRadians(45.0), 0.0F))
                .scale(0.55F)
                .translate(-0.5F, -0.5F, -0.5F);
            RING[side] = m.transformPosition(0.5F, 0.6875F, 0.5F, new Vector3f());
            Vector3f bottom = m.transformPosition(0.5F, 0.0F, 0.5F, new Vector3f());
            DOWN[side] = bottom.sub(RING[side]).normalize();
        }
    }
}
