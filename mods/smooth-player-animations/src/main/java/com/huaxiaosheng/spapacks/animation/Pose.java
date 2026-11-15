package com.huaxiaosheng.spapacks.animation;

import com.huaxiaosheng.spapacks.anim.pose.Poses;

public final class Pose {
    public static final int BODY = 0;
    public static final int TORSO = 1;
    public static final int HEAD = 2;
    public static final int RIGHT_ARM = 3;
    public static final int RIGHT_ARM_LOWER = 4;
    public static final int LEFT_ARM = 5;
    public static final int LEFT_ARM_LOWER = 6;
    public static final int RIGHT_LEG = 7;
    public static final int RIGHT_LEG_LOWER = 8;
    public static final int LEFT_LEG = 9;
    public static final int LEFT_LEG_LOWER = 10;
    public static final int TORSO_MID = 11;
    public static final int TORSO_UPPER = 12;
    public static final int CONTROLS = 13;
    public static final String[] CONTROL_NAMES = new String[]{
        "body",
        "torso",
        "head",
        "right_arm",
        "right_arm_lower",
        "left_arm",
        "left_arm_lower",
        "right_leg",
        "right_leg_lower",
        "left_leg",
        "left_leg_lower",
        "torso_mid",
        "torso_upper"
    };

    private Pose() {
    }

    public static float[][] create() {
        return Poses.create(13);
    }

    public static void reset(float[][] pose) {
        Poses.reset(pose);
    }

    public static void copy(float[][] from, float[][] to) {
        Poses.copy(from, to);
    }
}
