package com.huaxiaosheng.spapacks.anim.pose;

import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class Inertializer {
    private static final float MAX_TURN = 9.0F;
    private static final float MAX_SHIFT = 2.5F;
    private final float[][] from;
    private final float[][] previous;
    private final float[][][] curve;
    private double previousDt;
    private double elapsed;
    private boolean pending;
    private boolean active;
    private final Quaternionf q = new Quaternionf();
    private final Quaternionf p = new Quaternionf();
    private final Vector3f v = new Vector3f();

    public Inertializer(int bones) {
        this.from = Poses.create(bones);
        this.previous = Poses.create(bones);
        this.curve = new float[bones][6][7];
    }

    public boolean active() {
        return this.active;
    }

    public void reset() {
        this.active = false;
        this.pending = false;
    }

    public void begin(float[][] displayed, float[][] previous, double previousDt) {
        Poses.copy(displayed, this.from);
        Poses.copy(previous, this.previous);
        this.previousDt = previousDt;
        this.pending = true;
        this.active = true;
    }

    public void sample(double dt, double duration, float[][] target, float[][] out) {
        if (!this.active) {
            Poses.copy(target, out);
        } else {
            if (this.pending) {
                this.pending = false;
                this.elapsed = 0.0;
                this.start(Math.max(0.001, duration), target);
            }

            this.elapsed += dt;
            if (this.elapsed >= duration) {
                this.active = false;
                Poses.copy(target, out);
            } else {
                float t = (float)this.elapsed;

                for (int b = 0; b < target.length; b++) {
                    float[][] c = this.curve[b];
                    this.v.set(value(c[0], t), value(c[1], t), value(c[2], t));
                    float angle = this.v.length();
                    if (angle > 1.0E-6F) {
                        this.q.rotationAxis(angle, this.v.x / angle, this.v.y / angle, this.v.z / angle);
                    } else {
                        this.q.identity();
                    }

                    Poses.rotation(target[b], this.p);
                    this.q.mul(this.p).normalize();
                    out[b][0] = target[b][0] + value(c[3], t);
                    out[b][1] = target[b][1] + value(c[4], t);
                    out[b][2] = target[b][2] + value(c[5], t);
                    Poses.setRotation(out[b], this.q);
                    if (out[b].length >= 10 && target[b].length >= 10) {
                        out[b][7] = target[b][7];
                        out[b][8] = target[b][8];
                        out[b][9] = target[b][9];
                    }
                }
            }
        }
    }

    private void start(double duration, float[][] target) {
        float inverseDt = this.previousDt > 1.0E-4 ? (float)(1.0 / this.previousDt) : 0.0F;

        for (int b = 0; b < target.length; b++) {
            float[] f = this.from[b];
            float[] pr = this.previous[b];
            Poses.rotation(target[b], this.p).conjugate();
            Poses.rotation(f, this.q).mul(this.p);
            rotationVector(this.q, this.v);
            float ox = this.v.x;
            float oy = this.v.y;
            float oz = this.v.z;
            Poses.rotation(pr, this.p).conjugate();
            Poses.rotation(f, this.q).mul(this.p);
            rotationVector(this.q, this.v).mul(inverseDt);
            if (this.v.length() > 9.0F) {
                this.v.normalize(9.0F);
            }

            float[][] c = this.curve[b];
            fit(c[0], ox, this.v.x, duration);
            fit(c[1], oy, this.v.y, duration);
            fit(c[2], oz, this.v.z, duration);
            this.v.set(f[0] - pr[0], f[1] - pr[1], f[2] - pr[2]).mul(inverseDt);
            if (this.v.length() > 2.5F) {
                this.v.normalize(2.5F);
            }

            fit(c[3], f[0] - target[b][0], this.v.x, duration);
            fit(c[4], f[1] - target[b][1], this.v.y, duration);
            fit(c[5], f[2] - target[b][2], this.v.z, duration);
        }
    }

    private static void fit(float[] c, float x0, float v0, double duration) {
        double t1 = duration;
        if (x0 * v0 < 0.0F) {
            t1 = Math.min(t1, -5.0 * x0 / v0);
        }

        t1 = Math.max(t1, 0.001);
        double a0 = (-8.0 * v0 * t1 - 20.0 * x0) / (t1 * t1);
        c[0] = (float)(-(a0 * t1 * t1 + 6.0 * v0 * t1 + 12.0 * x0) / (2.0 * Math.pow(t1, 5.0)));
        c[1] = (float)((3.0 * a0 * t1 * t1 + 16.0 * v0 * t1 + 30.0 * x0) / (2.0 * Math.pow(t1, 4.0)));
        c[2] = (float)(-(3.0 * a0 * t1 * t1 + 12.0 * v0 * t1 + 20.0 * x0) / (2.0 * Math.pow(t1, 3.0)));
        c[3] = (float)(a0 / 2.0);
        c[4] = v0;
        c[5] = x0;
        c[6] = (float)t1;
    }

    private static float value(float[] c, float t) {
        return t >= c[6] ? 0.0F : ((((c[0] * t + c[1]) * t + c[2]) * t + c[3]) * t + c[4]) * t + c[5];
    }

    private static Vector3f rotationVector(Quaternionf q, Vector3f out) {
        if (q.w < 0.0F) {
            q.set(-q.x, -q.y, -q.z, -q.w);
        }

        double s = Math.sqrt(q.x * q.x + q.y * q.y + q.z * q.z);
        if (s < 1.0E-7) {
            return out.set(q.x * 2.0F, q.y * 2.0F, q.z * 2.0F);
        }

        float angle = (float)(2.0 * Math.atan2(s, q.w));
        float k = (float)(angle / s);
        return out.set(q.x * k, q.y * k, q.z * k);
    }
}
