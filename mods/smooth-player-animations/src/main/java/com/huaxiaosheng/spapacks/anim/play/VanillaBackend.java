package com.huaxiaosheng.spapacks.anim.play;

import com.huaxiaosheng.spapacks.anim.pose.Poses;
import com.huaxiaosheng.spapacks.anim.pose.Skeleton;
import com.huaxiaosheng.spapacks.animation.vanilla.VanillaClip;
import com.huaxiaosheng.spapacks.animation.vanilla.VanillaClipLibrary;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class VanillaBackend extends LibraryBackend {
    public static final String CEM = "@cem";
    public static final String VANILLA = "@vanilla";
    public static final String VANILLA_ARMS = "@vanilla_arms";
    public static final int RIGHT_ARM = 3;
    public static final int LEFT_ARM = 4;
    private static final float AIM_PITCH_MAX = 1.13F;
    private static final float AIM_YAW_MAX = 0.87F;
    private static final float SUPPORT_YAW_GAIN = 0.35F;
    public static final int BODY = 0;
    public static final int TORSO = 1;
    public static final int RIGHT_ITEM = 7;
    public static final int LEFT_ITEM = 8;
    public static final int[] PART_BONE = new int[]{1, 2, 3, 4, 5, 6};
    public static final Skeleton SKELETON = Skeleton.builder(VanillaClip.BONES)
        .scaled()
        .pair("right_arm", "left_arm")
        .pair("right_leg", "left_leg")
        .pair("right_item", "left_item")
        .region("head", "head")
        .region("arms", "right_arm", "left_arm", "right_item", "left_item")
        .region("right_arm", "right_arm", "right_item")
        .region("left_arm", "left_arm", "left_item")
        .region("spine", "torso")
        .region("upper", "body", "torso", "head", "right_arm", "left_arm", "right_leg", "left_leg", "right_item", "left_item")
        .region("legs", "right_leg", "left_leg")
        .region("lower", "body", "right_leg", "left_leg")
        .build();
    private final float[][] vanilla = SKELETON.newPose();
    private final float[][] cem = SKELETON.newPose();
    private final Quaternionf q = new Quaternionf();
    private final Matrix3f matrix = new Matrix3f();
    private final Vector3f angles = new Vector3f();
    private boolean cemBase;

    public VanillaBackend() {
        super(SKELETON);
    }

    @Override
    public String id() {
        return "vanilla";
    }

    @Override
    public Skeleton skeleton() {
        return SKELETON;
    }

    public void vanilla(float[][] parts, float[][] defaults) {
        Poses.reset(this.vanilla);

        for (int p = 0; p < PART_BONE.length; p++) {
            float[] v = parts[p];
            float[] o = this.vanilla[PART_BONE[p]];
            o[0] = v[0] - defaults[p][0];
            o[1] = -(v[1] - defaults[p][1]);
            o[2] = v[2] - defaults[p][2];
            this.rotation(v[3], v[4], v[5], o);
        }

        Poses.copy(this.vanilla, this.cem);
    }

    public void cem(float[][] values, float[][] defaults) {
        Poses.reset(this.cem);

        for (int p = 0; p < PART_BONE.length; p++) {
            float[] v = values[p];
            float[] o = this.cem[PART_BONE[p]];
            o[0] = v[3] - defaults[p][0];
            o[1] = -(v[4] - defaults[p][1]);
            o[2] = v[5] - defaults[p][2];
            this.rotation(v[0], v[1], v[2], o);
            o[7] = v[6];
            o[8] = v[7];
            o[9] = v[8];
        }
    }

    public void cemBase(boolean on) {
        this.cemBase = on;
    }

    private void rotation(float pitch, float yaw, float roll, float[] o) {
        this.q.rotationZYX(roll, yaw, pitch);
        Poses.setRotation(o, this.q);
    }

    @Override
    public void aim(float[][] pose, float weight, boolean mirror, float[] mask, float lookYaw, float lookPitch) {
        float pitch = Math.max(-1.13F, Math.min(1.13F, (float)Math.toRadians(lookPitch))) * weight;
        float yaw = Math.max(-0.87F, Math.min(0.87F, (float)Math.toRadians(lookYaw))) * weight;
        float right = mask == null ? 1.0F : mask[3];
        float left = mask == null ? 1.0F : mask[4];
        if (!mirror) {
            this.addAngles(pose[3], pitch * right, yaw * right, 0.0F);
            this.addAngles(pose[4], yaw * 0.35F * left, -pitch * left, 0.0F);
        } else {
            this.addAngles(pose[3], -yaw * 0.35F * right, pitch * right, 0.0F);
            this.addAngles(pose[4], pitch * left, yaw * left, 0.0F);
        }
    }

    public void addAngles(float[] b, float dx, float dy, float dz) {
        Poses.eulerZYX(b, this.q, this.matrix, this.angles);
        this.rotation(this.angles.x + dx, this.angles.y + dy, this.angles.z + dz, b);
    }

    @Override
    PlayableClip clip(String name) {
        return VanillaClipLibrary.get(name);
    }

    @Override
    public int keyed(String clip, int bone, boolean mirror) {
        if (!"@cem".equals(clip) && !"@vanilla".equals(clip)) {
            if (!"@vanilla_arms".equals(clip)) {
                return super.keyed(clip, bone, mirror);
            } else {
                return bone != 3 && bone != 4 ? 0 : 2;
            }
        } else {
            return bone >= 1 && bone < 7 ? 7 : 0;
        }
    }

    @Override
    public void rest(float[][] out) {
        Poses.copy(this.cemBase ? this.cem : this.vanilla, out);
    }

    @Override
    public void sample(String clip, double time, boolean phased, boolean mirror, float[][] out) {
        if (!"@cem".equals(clip) && !"@vanilla".equals(clip)) {
            if ("@vanilla_arms".equals(clip)) {
                System.arraycopy(this.vanilla[3], 3, out[3], 3, 4);
                System.arraycopy(this.vanilla[4], 3, out[4], 3, 4);
            } else {
                this.layNamed(clip, time, phased, mirror, out);
            }
        } else {
            float[][] from = "@cem".equals(clip) ? this.cem : this.vanilla;

            for (int b : PART_BONE) {
                System.arraycopy(from[b], 0, out[b], 0, 10);
            }
        }
    }
}
