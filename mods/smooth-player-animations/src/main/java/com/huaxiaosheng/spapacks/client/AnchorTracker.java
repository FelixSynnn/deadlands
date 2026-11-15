package com.huaxiaosheng.spapacks.client;

import com.huaxiaosheng.spapacks.platform.Game;
import com.huaxiaosheng.spapacks.rig.Anchors;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class AnchorTracker {
    private static final long STALE = 250000000L;
    private static final Map<AbstractClientPlayer, AnchorTracker.Drawn> DRAWN = new WeakHashMap<>();

    private AnchorTracker() {
    }

    static void drawn(AbstractClientPlayer player, Matrix4f pose, float[][] palette, Quaternionf[] items, Vector3f[] offsets, boolean slim) {
        if (!ShadowPass.now()) {
            Minecraft mc = Minecraft.getInstance();
            Vec3 camera = Game.cameraPosition(mc);
            float pt = Game.partialTick();
            double ex = Mth.lerp(pt, player.xo, player.getX()) - camera.x;
            double ey = Mth.lerp(pt, player.yo, player.getY()) - camera.y;
            double ez = Mth.lerp(pt, player.zo, player.getZ()) - camera.z;
            pose = Game.unturnView(new Matrix4f(pose));
            Vector3f o = pose.getTranslation(new Vector3f());
            if (!((o.x - ex) * (o.x - ex) + (o.y - ey) * (o.y - ey) + (o.z - ez) * (o.z - ez) > 9.0)) {
                AnchorTracker.Drawn d = DRAWN.computeIfAbsent(player, p -> new AnchorTracker.Drawn());
                d.model.set(pose);
                d.cx = camera.x;
                d.cy = camera.y;
                d.cz = camera.z;
                if (d.palette == null || d.palette.length != palette.length) {
                    d.palette = new float[palette.length][12];
                }

                for (int i = 0; i < palette.length; i++) {
                    System.arraycopy(palette[i], 0, d.palette[i], 0, 12);
                }

                d.items[0].set(items[0]);
                d.items[1].set(items[1]);
                d.offsets[0].set(offsets[0]);
                d.offsets[1].set(offsets[1]);
                d.slim = slim;
                d.at = System.nanoTime();
            }
        }
    }

    public static boolean anchor(AbstractClientPlayer player, String id, double[] position, float[] rotation) {
        AnchorTracker.Drawn d = DRAWN.get(player);
        if (d != null && System.nanoTime() - d.at <= 250000000L) {
            Matrix4f m = new Matrix4f();
            if (!Anchors.model(id, d.palette, d.items, d.offsets, d.slim, m)) {
                return false;
            }

            m.mulLocal(d.model);
            position[0] = d.cx + m.m30();
            position[1] = d.cy + m.m31();
            position[2] = d.cz + m.m32();
            Matrix3f r = new Matrix3f();
            m.get3x3(r);
            float[] len = new float[]{
                (float)Math.sqrt(r.m00 * r.m00 + r.m01 * r.m01 + r.m02 * r.m02),
                (float)Math.sqrt(r.m10 * r.m10 + r.m11 * r.m11 + r.m12 * r.m12),
                (float)Math.sqrt(r.m20 * r.m20 + r.m21 * r.m21 + r.m22 * r.m22)
            };
            float[][] col = new float[][]{{r.m00, r.m01, r.m02}, {r.m10, r.m11, r.m12}, {r.m20, r.m21, r.m22}};

            for (int c = 0; c < 3; c++) {
                float l = len[c] < 1.0E-6F ? 1.0F : len[c];

                for (int row = 0; row < 3; row++) {
                    rotation[row * 3 + c] = col[c][row] / l;
                }
            }

            return true;
        } else {
            return false;
        }
    }

    private static final class Drawn {
        final Matrix4f model = new Matrix4f();
        double cx;
        double cy;
        double cz;
        float[][] palette;
        final Quaternionf[] items = new Quaternionf[]{new Quaternionf(), new Quaternionf()};
        final Vector3f[] offsets = new Vector3f[]{new Vector3f(), new Vector3f()};
        boolean slim;
        long at;
    }
}
