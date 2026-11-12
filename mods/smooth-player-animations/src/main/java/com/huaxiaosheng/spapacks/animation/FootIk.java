package com.huaxiaosheng.spapacks.animation;

import com.huaxiaosheng.spapacks.anim.pose.Poses;
import com.huaxiaosheng.spapacks.rig.LiveRig;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class FootIk {
    private static final float SCALE = 0.9375F;
    public static final float REACH_DOWN = 0.55F;
    public static final float REACH_UP = 0.55F;
    private static final double FOOT_HALFLIFE = 0.05;
    private static final double WEIGHT_HALFLIFE = 0.08;
    private static final float MOST_SWING = (float)Math.toRadians(130.0);
    private static final int[][] LEGS = new int[][]{{7, 8}, {9, 10}};
    private final LiveRig rig;
    private final float thigh;
    private final float shin;
    private final float[] offset = new float[2];
    private float weight;
    private float hipDrop;
    private final Vector3f[] soles = new Vector3f[]{new Vector3f(), new Vector3f()};
    private final Vector3f hip = new Vector3f();
    private final Vector3f knee = new Vector3f();
    private final Vector3f foot = new Vector3f();
    private final Vector3f target = new Vector3f();
    private final Vector3f now = new Vector3f();
    private final Vector3f axis = new Vector3f();
    private final Vector3f cross = new Vector3f();
    private final Vector3f shinDir = new Vector3f();
    private final Matrix4f matrix = new Matrix4f();
    private final Quaternionf frame = new Quaternionf();
    private final Quaternionf turn = new Quaternionf();
    private final Quaternionf local = new Quaternionf();
    private final Quaternionf inverse = new Quaternionf();
    private final LimbIk limb;
    private static final Vector3f FORWARD = new Vector3f(0.0F, 1.0F, 0.0F);
    private final Quaternionf shinWorld = new Quaternionf();
    private final Vector3f origin = new Vector3f();

    public FootIk(LiveRig rig) {
        this.rig = rig;
        float[][] rest = Pose.create();
        Vector3f a = rig.controlMatrix(rest, 7, new Matrix4f()).getTranslation(new Vector3f());
        Vector3f b = rig.controlMatrix(rest, 8, new Matrix4f()).getTranslation(new Vector3f());
        this.thigh = a.distance(b);
        this.shin = b.z;
        this.limb = new LimbIk(rig, this.thigh, this.shin);
    }

    public float hipDrop() {
        return this.hipDrop;
    }

    public float weight() {
        return this.weight;
    }

    public void apply(float[][] pose, double dt, boolean grounded, float bodyYaw, FootIk.Ground ground) {
        double ease = dt <= 0.0 ? 0.0 : 1.0 - Math.pow(0.5, dt / 0.08);
        this.weight = this.weight + (float)(((grounded ? 1.0 : 0.0) - this.weight) * ease);
        this.hipDrop = 0.0F;
        double footEase = dt <= 0.0 ? 0.0 : 1.0 - Math.pow(0.5, dt / 0.05);
        double yaw = Math.toRadians(bodyYaw);
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);

        for (int i = 0; i < 2; i++) {
            this.rig.controlPoint(pose, LEGS[i][1], this.shin, this.soles[i]);
            float wanted = 0.0F;
            if (grounded) {
                Vector3f s = this.soles[i];
                float g = ground.below(0.9375 * (s.x * rx + s.y * fx), 0.9375 * (s.x * rz + s.y * fz));
                if (!Float.isNaN(g)) {
                    wanted = Math.max(-0.55F, Math.min(0.55F, g));
                }
            }

            this.offset[i] = this.offset[i] + (float)((wanted - this.offset[i]) * footEase);
        }

        if (!(this.weight < 0.001F) && (!(Math.abs(this.offset[0]) < 1.0E-4F) || !(Math.abs(this.offset[1]) < 1.0E-4F))) {
            float drop = Math.min(0.0F, Math.min(this.offset[0], this.offset[1])) * this.weight / 0.9375F;
            this.hipDrop = drop * 0.9375F;
            if (drop < 0.0F) {
                this.rig.controlMatrix(pose, 0, this.matrix).getTranslation(this.origin);
                this.origin.z += drop;
                this.rig.placeControl(pose, 0, this.origin);
            }

            for (int i = 0; i < 2; i++) {
                float floor = this.offset[i] * this.weight / 0.9375F;
                float rise = floor - drop;
                if (rise > 1.0E-5F) {
                    this.lift(pose, LEGS[i][0], LEGS[i][1], rise);
                }

                this.rig.controlPoint(pose, LEGS[i][1], this.shin, this.foot);
                this.target.set(this.foot.x, this.foot.y, this.soles[i].z + floor);
                this.reach(pose, LEGS[i][0], LEGS[i][1]);
            }
        }
    }

    private void lift(float[][] pose, int upper, int lower, float rise) {
        this.rig.controlMatrix(pose, upper, this.matrix).getTranslation(this.hip);
        this.rig.controlMatrix(pose, lower, this.matrix).getTranslation(this.knee);
        this.rig.controlPoint(pose, lower, this.shin, this.foot);
        Vector3f t = this.now.set(this.knee).sub(this.hip);
        this.shinDir.set(this.foot).sub(this.knee);
        this.rig.controlFrame(pose, upper, this.frame, this.origin);
        Poses.rotation(pose[upper], this.local);
        this.frame.mul(this.local, this.turn).transform(this.axis.set(1.0F, 0.0F, 0.0F)).normalize();
        float at = this.axis.dot(t);
        float c = this.axis.z * at;
        float a = t.z - c;
        float b = this.axis.cross(t, this.cross).z;
        float m = (float)Math.sqrt(a * a + b * b);
        if (!(m < 1.0E-6F)) {
            float psi = (float)Math.atan2(b, a);
            float v = Math.min(1.0F, (rise + a) / m);
            float spread = (float)Math.acos(Math.max(-1.0F, v));
            float d;
            if (b >= -1.0E-6F) {
                d = psi - spread;
            } else {
                d = Math.max(psi + spread, Math.min(0.0F, signedAngle(t, this.shinDir, this.axis)));
            }

            d = Math.min(d, MOST_SWING);
            if (!(Math.abs(d) < 1.0E-6F)) {
                this.rig.controlFrame(pose, lower, this.frame, this.origin);
                Poses.rotation(pose[lower], this.local);
                this.frame.mul(this.local, this.shinWorld);
                Poses.rotation(pose[upper], this.local);
                Poses.setRotation(pose[upper], this.local.rotateX(d).normalize());
                this.rig.controlFrame(pose, lower, this.frame, this.origin);
                Poses.setRotation(pose[lower], this.frame.conjugate(this.inverse).mul(this.shinWorld, this.local).normalize());
            }
        }
    }

    private static float signedAngle(Vector3f from, Vector3f to, Vector3f axis) {
        float fa = from.dot(axis);
        float ta = to.dot(axis);
        float fx = from.x - axis.x * fa;
        float fy = from.y - axis.y * fa;
        float fz = from.z - axis.z * fa;
        float tx = to.x - axis.x * ta;
        float ty = to.y - axis.y * ta;
        float tz = to.z - axis.z * ta;
        float cx = fy * tz - fz * ty;
        float cy = fz * tx - fx * tz;
        float cz = fx * ty - fy * tx;
        float sin = cx * axis.x + cy * axis.y + cz * axis.z;
        float cos = fx * tx + fy * ty + fz * tz;
        return (float)Math.atan2(sin, cos);
    }

    private void reach(float[][] pose, int upper, int lower) {
        this.limb.reach(pose, upper, lower, this.target, true, FORWARD);
    }

    public interface Ground {
        float below(double var1, double var3);
    }
}
