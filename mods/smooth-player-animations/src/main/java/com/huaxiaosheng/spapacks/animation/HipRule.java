package com.huaxiaosheng.spapacks.animation;

import com.huaxiaosheng.spapacks.anim.pose.Poses;
import com.huaxiaosheng.spapacks.rig.LiveRig;
import com.huaxiaosheng.spapacks.rig.Skeleton;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class HipRule {
    private static final float WAIST = 0.4F;
    private static final int[] LEGS = new int[]{7, 9};
    private static final int[] LEG_BONES = new int[]{7, 8, 9, 10};
    private final LiveRig rig = new LiveRig(Skeleton.CHAIN);
    private final Quaternionf frame = new Quaternionf();
    private final Quaternionf q = new Quaternionf();
    private final Quaternionf swing = new Quaternionf();
    private final Quaternionf twist = new Quaternionf();
    private final Quaternionf part = new Quaternionf();
    private final Quaternionf share = new Quaternionf();
    private final Vector3f origin = new Vector3f();
    private final Matrix4f pelvis = new Matrix4f();
    private final Matrix4f m = new Matrix4f();
    private final Vector3f[] hip = new Vector3f[]{new Vector3f(), new Vector3f()};
    private final float[][] held = Pose.create();

    public void follow(float[][] pose, float[][] below, float give) {
        this.height(pose, below, 0, give);
        this.height(pose, below, 1, give);
        double heading = this.heading(below, pose);
        this.rig.controlMatrix(pose, 1, this.pelvis).mul(this.rig.controlMatrix(below, 1, this.m).invert());

        for (int i = 0; i < LEGS.length; i++) {
            this.rig.controlMatrix(below, LEGS[i], this.m).getTranslation(this.hip[i]);
            this.pelvis.transformPosition(this.hip[i]);
        }

        for (int b : LEG_BONES) {
            System.arraycopy(below[b], 0, pose[b], 0, 7);
        }

        for (int i = 0; i < LEGS.length; i++) {
            this.rig.placeControl(pose, LEGS[i], this.hip[i]);
            this.rig.preserveControlRotation(below, pose, LEGS[i], 1.0F, heading);
            this.height(pose, below, LEGS[i], give);
        }
    }

    public double heading(float[][] below, float[][] pose) {
        double d = this.rig.controlHeading(pose, 1) - this.rig.controlHeading(below, 1);
        return Math.IEEEremainder(d, Math.PI * 2);
    }

    private void height(float[][] pose, float[][] below, int control, float amount) {
        if (!(amount <= 0.0F)) {
            System.arraycopy(pose[control], 0, this.held[control], 0, 7);
            this.rig.preserveControlHeight(below, pose, control);
            float[] v = pose[control];
            float[] h = this.held[control];

            for (int k = 0; k < 3; k++) {
                v[k] = h[k] + (v[k] - h[k]) * Math.min(1.0F, amount);
            }
        }
    }

    public void turn(float[][] pose, int control, Quaternionf turn) {
        this.rig.controlFrame(pose, control, this.frame, this.origin);
        float[] v = pose[control];
        this.q.set(this.frame).conjugate().mul(turn).mul(this.frame).mul(Poses.rotation(v, this.part)).normalize();
        Poses.setRotation(v, this.q);
    }

    public void twistSpine(float[][] pose, Quaternionf twist) {
        if (!(Math.abs(twist.w) > 0.9999999F)) {
            this.turn(pose, 11, this.share.identity().slerp(twist, 0.4F));
            this.turn(pose, 12, this.share.identity().slerp(twist, 0.6F));
        }
    }

    public void turnPelvis(float[][] pose, Quaternionf turn) {
        split(turn, this.swing, this.twist);
        this.twistSpine(pose, this.twist);
    }

    public static void split(Quaternionf q, Quaternionf swing, Quaternionf twist) {
        float len = (float)Math.sqrt(q.z * q.z + q.w * q.w);
        if (len < 1.0E-6F) {
            twist.identity();
        } else {
            twist.set(0.0F, 0.0F, q.z / len, q.w / len);
        }

        swing.set(twist).conjugate().premul(q).normalize();
    }
}
