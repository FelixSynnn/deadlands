package com.huaxiaosheng.spapacks.animation;

import com.huaxiaosheng.spapacks.anim.graph.Procedural;
import com.huaxiaosheng.spapacks.anim.pose.Poses;
import com.huaxiaosheng.spapacks.rig.LiveRig;
import com.huaxiaosheng.spapacks.rig.Skeleton;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class Look {
    private static final int[] SPINE = new int[]{1, 11, 12};
    private static final double[] SPINE_SHARE = new double[]{0.0, 0.55, 0.45};
    private final LiveRig rig = new LiveRig(Skeleton.CHAIN);
    private final Quaternionf frame = new Quaternionf();
    private final Quaternionf turn = new Quaternionf();
    private final Quaternionf local = new Quaternionf();
    private final Quaternionf inverse = new Quaternionf();
    private final Vector3f origin = new Vector3f();
    private final HipRule hips = new HipRule();
    private final Quaternionf pelvisTurn = new Quaternionf();

    public void lean(float[][] pose, double forward, double bank) {
        if (!(Math.abs(forward) < 0.001) || !(Math.abs(bank) < 0.001)) {
            for (int i = 1; i < SPINE.length; i++) {
                this.rotate(pose, SPINE[i], 0.0, forward * SPINE_SHARE[i], bank * SPINE_SHARE[i]);
            }
        }
    }

    public void limbs(float[][] pose, float[] armBack, float[] armLeft, float[] legBack, float[] legLeft) {
        this.rotate(pose, 3, 0.0, armBack[0], armLeft[0]);
        this.rotate(pose, 5, 0.0, armBack[1], armLeft[1]);
        this.rotate(pose, 7, 0.0, legBack[0], legLeft[0]);
        this.rotate(pose, 9, 0.0, legBack[1], legLeft[1]);
    }

    public void apply(float[][] pose, double yawDegrees, double pitchDegrees, Procedural settings) {
        double yaw = Math.max(-settings.maxYaw, Math.min(settings.maxYaw, yawDegrees));
        double pitch = Math.max(-settings.maxPitch, Math.min(settings.maxPitch, pitchDegrees));
        double spine = yaw * settings.chestShare;
        this.hips.turnPelvis(pose, angles(spine * settings.spineSplit[0], 0.0, 0.0, this.pelvisTurn));

        for (int i = 1; i < SPINE.length; i++) {
            this.rotate(pose, SPINE[i], spine * settings.spineSplit[i], 0.0, 0.0);
        }

        this.rotate(pose, 2, yaw - spine, pitch, 0.0);
    }

    private static Quaternionf angles(double yawRight, double pitchDown, double rollLeft, Quaternionf out) {
        return out.rotationZ((float)Math.toRadians(-yawRight)).rotateX((float)Math.toRadians(-pitchDown)).rotateY((float)Math.toRadians(-rollLeft));
    }

    private void rotate(float[][] pose, int control, double yawRight, double pitchDown, double rollLeft) {
        if (yawRight != 0.0 || pitchDown != 0.0 || rollLeft != 0.0) {
            this.rig.controlFrame(pose, control, this.frame, this.origin);
            this.turn.rotationZ((float)Math.toRadians(-yawRight)).rotateX((float)Math.toRadians(-pitchDown)).rotateY((float)Math.toRadians(-rollLeft));
            float[] v = pose[control];
            Poses.rotation(v, this.local);
            this.turn.premul(this.inverse.set(this.frame).conjugate()).mul(this.frame).mul(this.local).normalize();
            Poses.setRotation(v, this.turn);
        }
    }
}
