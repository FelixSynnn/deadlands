package com.huaxiaosheng.spapacks.animation.external;

import com.huaxiaosheng.spapacks.anim.pose.Poses;
import com.huaxiaosheng.spapacks.animation.PartRetarget;
import com.huaxiaosheng.spapacks.animation.Pose;
import com.huaxiaosheng.spapacks.util.FrameClock;
import com.huaxiaosheng.spapacks.util.MathUtil;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class ExternalBlend {
    private static final double FADE = 0.15;
    private static final double MAX_STEP = 0.25;
    private static final float[][] REST = Pose.create();
    private static final int[] LOWER = new int[]{-1, -1, 4, 6, 8, 10};
    private static final float BODY_LENGTH = 12.0F;
    private static final float[] SPINE_SHARE = new float[]{0.0F, 0.55F, 1.0F};
    private final PartRetarget parts = new PartRetarget();
    private final float[] shown = new float[6];
    private final float[][] value = new float[6][6];
    private final float[][] vanilla = new float[6][6];
    private final float[][] bend = new float[6][2];
    private final float[][] out = Pose.create();
    private final Quaternionf bodyValue = new Quaternionf();
    private final Quaternionf bodyVanilla = new Quaternionf();
    private final Quaternionf bodyFrame = new Quaternionf();
    private final Quaternionf parent = new Quaternionf();
    private final Quaternionf turn = new Quaternionf();
    private final Quaternionf q = new Quaternionf();
    private final Quaternionf waist = new Quaternionf();
    private final Quaternionf[] legTurn = new Quaternionf[]{new Quaternionf(), new Quaternionf()};
    private final Vector3f a = new Vector3f();
    private final Vector3f b = new Vector3f();
    private final FrameClock clock = new FrameClock();
    private float exclusive;
    private float restBase;
    private final float[][] base = Pose.create();

    public boolean active() {
        if (!(this.exclusive > 0.0F) && !(this.restBase > 0.0F)) {
            for (float w : this.shown) {
                if (w > 0.0F) {
                    return true;
                }
            }

            return false;
        } else {
            return true;
        }
    }

    public float[][] apply(double time, ExternalPose in, float[][] pose) {
        return this.apply(time, in, pose, null, false);
    }

    public float[][] apply(double time, ExternalPose in, float[][] pose, float[] vanillaParts, boolean restBase) {
        float rate = (float)(this.clock.step(time, 0.25) / 0.15);
        boolean whole = in != null && in.exclusive;
        this.exclusive = MathUtil.approach(this.exclusive, whole ? 1.0F : 0.0F, rate);
        this.restBase = MathUtil.approach(this.restBase, in != null && restBase ? 1.0F : 0.0F, rate);
        boolean underWhole = in != null && this.exclusive > 0.0F;
        float standing = Math.max(this.exclusive, this.restBase);

        for (int p = 0; p < 6; p++) {
            float target = in == null ? 0.0F : in.weight[p];
            float off = in != null && vanillaParts != null ? vanillaParts[p] : 0.0F;
            if (target > 0.0F) {
                System.arraycopy(in.value[p], 0, this.value[p], 0, 6);
                this.bend[p][0] = in.bend[p][0];
                this.bend[p][1] = in.bend[p][1];
            } else if (underWhole) {
                System.arraycopy(in.vanilla[p], 0, this.value[p], 0, 6);
                this.bend[p][0] = 0.0F;
                this.bend[p][1] = 0.0F;
            } else if (off > 0.0F) {
                System.arraycopy(in.model[p], 0, this.value[p], 0, 6);
                this.bend[p][0] = 0.0F;
                this.bend[p][1] = 0.0F;
            }

            if (target > 0.0F || underWhole || off > 0.0F) {
                for (int i = 0; i < 6; i++) {
                    this.vanilla[p][i] = in.vanilla[p][i] + (in.standing[p][i] - in.vanilla[p][i]) * standing;
                }
            }

            if (whole) {
                target = 1.0F;
            }

            target = Math.max(target, off);
            this.shown[p] = MathUtil.approach(this.shown[p], target, rate);
        }

        if (!this.active()) {
            return pose;
        }

        if (this.exclusive > 0.0F) {
            Poses.blend(pose, REST, this.exclusive, this.base);
            pose = this.base;
        }

        Pose.copy(pose, this.out);
        this.body();
        this.attached(1, 12, 1.0F);
        this.attached(2, 12, 1.0F);
        this.attached(3, 12, 1.0F);
        this.attached(4, 1, SPINE_SHARE[0]);
        this.attached(5, 1, SPINE_SHARE[0]);
        return this.out;
    }

    private void body() {
        int body = 0;
        float[] v = this.value[body];
        float[] n = this.vanilla[body];
        float w = this.shown[body];
        PartRetarget.toRig(n[3], n[4], n[5], this.bodyVanilla);
        PartRetarget.toRig(v[3], v[4], v[5], this.bodyValue);
        this.bodyFrame.set(this.bodyVanilla).slerp(this.bodyValue, w);
        if (!(w <= 0.0F)) {
            PartRetarget.toModel(v[3], v[4], v[5], this.q).transform(this.a.set(0.0F, 12.0F, 0.0F)).add(v[0], v[1], v[2]);
            PartRetarget.toModel(n[3], n[4], n[5], this.q).transform(this.b.set(0.0F, 12.0F, 0.0F)).add(n[0], n[1], n[2]);
            this.a.sub(this.b).mul(w);
            this.parts.translate(this.out, 0, this.a.x, this.a.y, this.a.z);
            this.parts.turn(this.out, 7, this.legTurn[0]);
            this.parts.turn(this.out, 9, this.legTurn[1]);
            waistBend(this.bend[body][0], this.bend[body][1], this.waist);

            for (int i = 0; i < PartRetarget.SPINE.length; i++) {
                this.turn.identity().slerp(this.bodyValue, SPINE_SHARE[i]);
                if (i > 0) {
                    this.turn.mul(this.q.identity().slerp(this.waist, i * 0.5F));
                }

                this.parts.orient(this.out, PartRetarget.SPINE[i], this.turn, w);
            }

            this.parts.orient(this.out, 7, this.legTurn[0], w);
            this.parts.orient(this.out, 9, this.legTurn[1], w);
        }
    }

    private void attached(int p, int anchor, float bodyShare) {
        float w = this.shown[p];
        if (!(w <= 0.0F)) {
            float[] v = this.value[p];
            float[] n = this.vanilla[p];
            int control = PartRetarget.CONTROL[p];
            this.parts.turn(this.out, anchor, this.parent);
            this.q.identity().slerp(this.bodyFrame, bodyShare).conjugate();
            this.turn.set(this.parent).mul(this.q).mul(PartRetarget.toRig(v[3], v[4], v[5], this.q));
            this.parts.orient(this.out, control, this.turn, w);
            float[] bv = this.value[0];
            float[] bn = this.vanilla[0];
            float bw = this.shown[0];
            this.parts
                .translate(
                    this.out,
                    control,
                    (v[0] - n[0] - (bv[0] - bn[0]) * bw) * w,
                    (v[1] - n[1] - (bv[1] - bn[1]) * bw) * w,
                    (v[2] - n[2] - (bv[2] - bn[2]) * bw) * w
                );
            int lower = LOWER[p];
            if (lower >= 0) {
                limbBend(this.bend[p][0], this.bend[p][1], this.q);
                this.parts.orient(this.out, lower, this.turn.mul(this.q), w);
            }
        }
    }

    static Quaternionf limbBend(float axis, float value, Quaternionf out) {
        return modelAxisAngle((float)Math.cos(axis), 0.0F, (float)Math.sin(axis), value, out);
    }

    static Quaternionf waistBend(float axis, float value, Quaternionf out) {
        return modelAxisAngle((float)Math.cos(axis), 0.0F, -((float)Math.sin(axis)), value, out);
    }

    private static Quaternionf modelAxisAngle(float x, float y, float z, float angle, Quaternionf out) {
        if (Math.abs(angle) < 1.0E-4F) {
            return out.identity();
        }

        out.rotationAxis(angle, x, y, z);
        return out.set(-out.x, -out.z, -out.y, out.w);
    }
}
