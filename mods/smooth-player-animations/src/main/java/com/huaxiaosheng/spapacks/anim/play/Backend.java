package com.huaxiaosheng.spapacks.anim.play;

import com.huaxiaosheng.spapacks.anim.pose.Skeleton;

public interface Backend {
    int TRANSLATION = 1;
    int ROTATION = 2;
    int SCALE = 4;

    String id();

    Skeleton skeleton();

    boolean has(String var1);

    double seconds(String var1);

    boolean loops(String var1);

    default boolean holds(String clip) {
        return false;
    }

    default double stride(String clip) {
        return 0.0;
    }

    default boolean additive(String clip) {
        return false;
    }

    default int keyed(String clip, int bone, boolean mirror) {
        return 7;
    }

    default void aim(float[][] pose, float weight, boolean mirror, float[] mask, float lookYaw, float lookPitch) {
    }

    default void followHips(float[][] pose, float[][] below, float[][] clip, String clipName, boolean mirror, float weight, float lower) {
    }

    default double hipTurn(float[][] below, float[][] pose) {
        return 0.0;
    }

    default boolean overrideTranslation() {
        return false;
    }

    void rest(float[][] var1);

    void sample(String var1, double var2, boolean var4, boolean var5, float[][] var6);
}
