package com.huaxiaosheng.spapacks.animation;

import com.huaxiaosheng.spapacks.anim.pose.Poses;
import com.huaxiaosheng.spapacks.rig.LiveRig;
import com.huaxiaosheng.spapacks.rig.Skeleton;
import com.huaxiaosheng.spapacks.util.MathUtil;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class PartRetarget {
    public static final int BODY = 0;
    public static final int HEAD = 1;
    public static final int RIGHT_ARM = 2;
    public static final int LEFT_ARM = 3;
    public static final int RIGHT_LEG = 4;
    public static final int LEFT_LEG = 5;
    public static final int PARTS = 6;
    public static final float[][] PIVOT = new float[][]{
        {0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}, {-5.0F, 2.0F, 0.0F}, {5.0F, 2.0F, 0.0F}, {-1.9F, 12.0F, 0.0F}, {1.9F, 12.0F, 0.0F}
    };
    public static final int[] CONTROL = new int[]{1, 2, 3, 5, 7, 9};
    public static final int[] SPINE = new int[]{1, 11, 12};
    public static final float[] SPINE_SHARE = new float[]{0.35F, 0.7F, 1.0F};
    private static final float MAX_OFFSET = 48.0F;
    private final LiveRig rig;
    private final Quaternionf[] rest = new Quaternionf[13];
    private final Quaternionf frame = new Quaternionf();
    private final Quaternionf q = new Quaternionf();
    private final Quaternionf current = new Quaternionf();
    private final Vector3f origin = new Vector3f();
    private final Vector3f d = new Vector3f();

    public PartRetarget() {
        this(Skeleton.ALEX);
    }

    public PartRetarget(Skeleton skeleton) {
        this.rig = new LiveRig(skeleton);
        float[][] identity = Pose.create();

        for (int c = 0; c < 13; c++) {
            this.rest[c] = new Quaternionf();
            this.rig.controlFrame(identity, c, this.rest[c], this.origin);
        }
    }

    public void frame(float[][] pose, int control, Quaternionf rotation, Vector3f origin) {
        this.rig.controlFrame(pose, control, rotation, origin);
    }

    public void orient(float[][] pose, int control, Quaternionf turn) {
        this.orient(pose, control, turn, 1.0F);
    }

    public void orient(float[][] pose, int control, Quaternionf turn, float weight) {
        this.rig.controlFrame(pose, control, this.frame, this.origin);
        this.frame.conjugate().mul(turn, this.q).mul(this.rest[control]).normalize();
        float[] c = pose[control];
        if (weight < 1.0F) {
            Poses.rotation(c, this.current).slerp(this.q, weight);
            this.q.set(this.current);
        }

        Poses.setRotation(c, this.q);
    }

    public Quaternionf turn(float[][] pose, int control, Quaternionf out) {
        this.rig.controlFrame(pose, control, this.frame, this.origin);
        float[] c = pose[control];
        return Poses.rotation(c, out).premul(this.frame).mul(this.current.set(this.rest[control]).conjugate()).normalize();
    }

    public void translate(float[][] pose, int control, float x, float y, float z) {
        this.rig.controlFrame(pose, control, this.frame, this.origin);
        this.d.set(-limit(x) / 16.0F, -limit(z) / 16.0F, -limit(y) / 16.0F);
        this.frame.conjugate().transform(this.d);
        float[] c = pose[control];
        c[0] += this.d.x;
        c[1] += this.d.y;
        c[2] += this.d.z;
    }

    public static Quaternionf toRig(float rx, float ry, float rz, Quaternionf out) {
        out.rotationZYX(rz, ry, rx);
        return out.set(-out.x, -out.z, -out.y, out.w);
    }

    public static Quaternionf toModel(float rx, float ry, float rz, Quaternionf out) {
        return out.rotationZYX(rz, ry, rx);
    }

    private static float limit(float pixels) {
        return MathUtil.clamp(pixels, -48.0F, 48.0F);
    }
}
