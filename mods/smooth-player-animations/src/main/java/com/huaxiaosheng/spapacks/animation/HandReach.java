package com.huaxiaosheng.spapacks.animation;

import com.huaxiaosheng.spapacks.anim.pose.Poses;
import com.huaxiaosheng.spapacks.rig.LiveRig;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class HandReach {
    private static final float SCALE = 0.9375F;
    private static final double IN = 0.12;
    private static final double HOLD = 0.16;
    private static final double OUT = 0.3;
    private static final int[][] ARMS = new int[][]{{3, 4}, {5, 6}};
    private static final float[][] ELBOWS = new float[][]{{0.55F, -0.35F, -0.75F}, {-0.55F, -0.35F, -0.75F}};
    private final LimbIk limb;
    private final double[] age = new double[]{-1.0, -1.0};
    private final Vector3f[] targets = new Vector3f[]{new Vector3f(), new Vector3f()};
    private final Vector3f spot = new Vector3f();
    private final Vector3f elbow = new Vector3f();
    private final float[] clipUpper = new float[7];
    private final float[] clipLower = new float[7];
    private static final float LEAN = (float)Math.toRadians(22.0);
    private static final float LEAN_FROM = 0.55F;
    private static final float LEAN_SPAN = 0.6F;
    private static final int[] SPINE = new int[]{11, 12};
    private final LiveRig rig;
    private final Matrix4f matrix = new Matrix4f();
    private final Vector3f shoulder = new Vector3f();
    private final Vector3f chest = new Vector3f();
    private final Vector3f lean = new Vector3f();
    private final Vector3f up = new Vector3f();
    private final Vector3f tilted = new Vector3f();
    private final Vector3f[] spots = new Vector3f[]{new Vector3f(), new Vector3f()};
    private final float[] weights = new float[2];

    public HandReach(LiveRig rig) {
        this.rig = rig;
        float[][] rest = Pose.create();
        Vector3f a = rig.controlMatrix(rest, 3, new Matrix4f()).getTranslation(new Vector3f());
        Vector3f b = rig.controlMatrix(rest, 4, new Matrix4f()).getTranslation(new Vector3f());
        float upper = a.distance(b);
        this.limb = new LimbIk(rig, upper, upper);
    }

    public void start(int arm) {
        double a = this.age[arm];
        this.age[arm] = a < 0.0 ? 0.0 : 0.12 * risen(weight(a));
    }

    public void release(int arm) {
        double a = this.age[arm];
        if (a >= 0.0 && a < 0.28) {
            this.age[arm] = 0.28 + 0.3 * risen(1.0F - weight(a));
        }
    }

    public void target(int arm, float dx, float dy, float dz) {
        this.targets[arm].set(dx, dy, dz);
    }

    public boolean reaching(int arm) {
        return this.age[arm] >= 0.0;
    }

    public float weight(int arm) {
        double a = this.age[arm];
        return a < 0.0 ? 0.0F : weight(a);
    }

    public void apply(float[][] pose, double dt, float bodyYaw) {
        double yaw = Math.toRadians(bodyYaw);
        float fx = (float)(-Math.sin(yaw));
        float fz = (float)Math.cos(yaw);
        float rx = (float)(-Math.cos(yaw));
        float rz = (float)(-Math.sin(yaw));
        boolean any = false;

        for (int i = 0; i < 2; i++) {
            this.weights[i] = 0.0F;
            if (!(this.age[i] < 0.0)) {
                this.weights[i] = weight(this.age[i]);
                this.age[i] = this.age[i] + Math.max(0.0, dt);
                if (this.age[i] >= 0.5800000000000001) {
                    this.age[i] = -1.0;
                }

                Vector3f t = this.targets[i];
                this.spots[i].set((t.x * rx + t.z * rz) / 0.9375F, (t.x * fx + t.z * fz) / 0.9375F, t.y / 0.9375F);
                any |= this.weights[i] > 0.0F;
            }
        }

        if (any) {
            this.rig.controlMatrix(pose, 12, this.matrix).getTranslation(this.chest);
            this.lean.zero();

            for (int i = 0; i < 2; i++) {
                if (this.weights[i] > 0.0F) {
                    this.rig.controlMatrix(pose, ARMS[i][0], this.matrix).getTranslation(this.shoulder);
                    float far = Math.max(0.0F, Math.min(1.0F, (this.shoulder.distance(this.spots[i]) - 0.55F) / 0.6F));
                    this.spot.set(this.spots[i]).sub(this.chest).setComponent(2, 0.0F);
                    if (this.spot.lengthSquared() > 1.0E-8F) {
                        this.lean.fma(this.weights[i] * far, this.spot.normalize());
                    }
                }
            }

            float angle = Math.min(1.0F, this.lean.length()) * LEAN;
            if (angle > 1.0E-4F) {
                this.lean.normalize();

                for (int control : SPINE) {
                    this.up.set(0.0F, 0.0F, 1.0F);
                    this.tilted.set(0.0F, 0.0F, 1.0F).rotateAxis(angle * 0.5F, -this.lean.y, this.lean.x, 0.0F);
                    this.limb.turn(pose, control, this.up, this.tilted);
                }
            }

            for (int i = 0; i < 2; i++) {
                float w = this.weights[i];
                if (!(w <= 0.0F)) {
                    int upper = ARMS[i][0];
                    int lower = ARMS[i][1];
                    System.arraycopy(pose[upper], 0, this.clipUpper, 0, 7);
                    System.arraycopy(pose[lower], 0, this.clipLower, 0, 7);
                    float[] e = ELBOWS[i];
                    this.limb.hinge(pose, upper, lower, this.spots[i], 1.0F, this.elbow.set(e[0], e[1], e[2]));
                    if (w < 1.0F) {
                        Poses.blendBone(this.clipUpper, pose[upper], w, pose[upper]);
                        Poses.blendBone(this.clipLower, pose[lower], w, pose[lower]);
                    }
                }
            }
        }
    }

    private static float weight(double age) {
        if (age < 0.12) {
            return smooth(age / 0.12);
        } else {
            return age < 0.28 ? 1.0F : 1.0F - smooth((age - 0.12 - 0.16) / 0.3);
        }
    }

    private static float smooth(double x) {
        double k = Math.max(0.0, Math.min(1.0, x));
        return (float)(k * k * (3.0 - 2.0 * k));
    }

    private static double risen(float w) {
        double v = Math.max(0.0, Math.min(1.0, w));
        return 0.5 - Math.sin(Math.asin(1.0 - 2.0 * v) / 3.0);
    }
}
