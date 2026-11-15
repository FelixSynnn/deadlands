package com.huaxiaosheng.spapacks.anim.play;

import com.huaxiaosheng.spapacks.anim.pose.Poses;
import com.huaxiaosheng.spapacks.anim.pose.Skeleton;
import com.huaxiaosheng.spapacks.animation.Clip;
import com.huaxiaosheng.spapacks.animation.ClipLibrary;
import com.huaxiaosheng.spapacks.animation.HipRule;
import com.huaxiaosheng.spapacks.animation.PartRetarget;
import com.huaxiaosheng.spapacks.animation.Pose;
import java.util.Arrays;
import org.joml.Quaternionf;

public final class BentBackend extends LibraryBackend {
    public static final String VANILLA_ARMS = "@vanilla_arms";
    public static final Skeleton SKELETON = Skeleton.builder(Clip.BONE_NAMES)
        .pair("right_arm", "left_arm")
        .pair("right_arm_lower", "left_arm_lower")
        .pair("right_leg", "left_leg")
        .pair("right_leg_lower", "left_leg_lower")
        .pair("right_item", "left_item")
        .region("head", "head")
        .region("arms", "right_arm", "right_arm_lower", "left_arm", "left_arm_lower", "right_item", "left_item")
        .region("right_arm", "right_arm", "right_arm_lower", "right_item")
        .region("left_arm", "left_arm", "left_arm_lower", "left_item")
        .region("arms_head", "head", "right_arm", "right_arm_lower", "left_arm", "left_arm_lower", "right_item", "left_item")
        .region("spine", "torso_mid", "torso_upper")
        .region("upper", "torso_mid", "torso_upper", "head", "right_arm", "right_arm_lower", "left_arm", "left_arm_lower", "right_item", "left_item")
        .region("legs", "right_leg", "right_leg_lower", "left_leg", "left_leg_lower")
        .region("lower", "body", "torso", "right_leg", "right_leg_lower", "left_leg", "left_leg_lower")
        .build();
    private final float[][] controls = Pose.create();
    private final PartRetarget parts = new PartRetarget(com.huaxiaosheng.spapacks.rig.Skeleton.CHAIN);
    private final Quaternionf body = new Quaternionf();
    private final Quaternionf chest = new Quaternionf();
    private final Quaternionf turn = new Quaternionf();
    private final Quaternionf q = new Quaternionf();
    private final HipRule hips = new HipRule();
    private static final int[] LEG_BONES = new int[]{7, 8, 9, 10};
    private static final int[] ARM_BONES = new int[]{3, 4, 5, 6};
    private float[][] vanilla;

    public BentBackend() {
        super(SKELETON);
    }

    @Override
    public String id() {
        return "bent";
    }

    @Override
    public Skeleton skeleton() {
        return SKELETON;
    }

    public void vanilla(float[][] parts) {
        this.vanilla = parts;
    }

    @Override
    PlayableClip clip(String name) {
        return ClipLibrary.get(name);
    }

    @Override
    public int keyed(String clip, int bone, boolean mirror) {
        if ("@vanilla_arms".equals(clip)) {
            int source = mirror ? SKELETON.mirror(bone) : bone;
            return (source < 3 || source > 6) && source < 13 ? 0 : 2;
        } else {
            return super.keyed(clip, bone, mirror);
        }
    }

    @Override
    public void rest(float[][] out) {
        Poses.reset(out);
    }

    @Override
    public void followHips(float[][] pose, float[][] below, float[][] clip, String clipName, boolean mirror, float weight, float lower) {
        if (!Arrays.equals(pose[0], below[0]) || !Arrays.equals(pose[1], below[1])) {
            this.hips.follow(pose, below, weight * (1.0F - lower));
            float take = weight * lower;
            if (!(take <= 0.0F)) {
                for (int b : LEG_BONES) {
                    int keyed = this.keyed(clipName, b, mirror);
                    float[] o = pose[b];
                    float[] d = clip[b];
                    if ((keyed & 1) != 0) {
                        for (int k = 0; k < 3; k++) {
                            o[k] += (d[k] - o[k]) * take;
                        }
                    }

                    if ((keyed & 2) != 0) {
                        Poses.rotation(o, this.q).slerp(Poses.rotation(d, this.turn), take).normalize();
                        Poses.setRotation(o, this.q);
                    }
                }
            }
        }
    }

    @Override
    public double hipTurn(float[][] below, float[][] pose) {
        return this.hips.heading(below, pose);
    }

    @Override
    public boolean overrideTranslation() {
        return true;
    }

    @Override
    public void sample(String clip, double time, boolean phased, boolean mirror, float[][] out) {
        if ("@vanilla_arms".equals(clip)) {
            this.vanillaArms(out);
        } else {
            this.layNamed(clip, time, phased, mirror, out);
        }
    }

    private void vanillaArms(float[][] out) {
        float[][] v = this.vanilla;
        if (v != null) {
            for (int i = 0; i < 13; i++) {
                System.arraycopy(out[i], 0, this.controls[i], 0, 7);
            }

            float[] b = v[0];
            PartRetarget.toRig(b[3], b[4], b[5], this.body).conjugate();
            this.parts.turn(this.controls, 12, this.chest);

            for (int p = 2; p <= 3; p++) {
                float[] a = v[p];
                this.turn.set(this.chest).mul(this.body).mul(PartRetarget.toRig(a[3], a[4], a[5], this.q));
                int control = PartRetarget.CONTROL[p];
                this.parts.orient(this.controls, control, this.turn);
                float[] lower = this.controls[control + 1];
                lower[3] = lower[4] = lower[5] = 0.0F;
                lower[6] = 1.0F;
            }

            for (int c : ARM_BONES) {
                System.arraycopy(this.controls[c], 3, out[c], 3, 4);
            }

            for (int item = 13; item <= 14; item++) {
                float[] o = out[item];
                o[0] = o[1] = o[2] = 0.0F;
                o[3] = o[4] = o[5] = 0.0F;
                o[6] = 1.0F;
            }
        }
    }
}
