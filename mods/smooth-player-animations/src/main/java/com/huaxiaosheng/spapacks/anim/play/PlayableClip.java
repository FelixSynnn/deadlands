package com.huaxiaosheng.spapacks.anim.play;

public interface PlayableClip {
    double seconds();

    boolean loops();

    default boolean holds() {
        return false;
    }

    default double stride() {
        return 0.0;
    }

    default boolean additive() {
        return false;
    }

    int keyed(int var1);

    void lay(double var1, boolean var3, float[][] var4);
}
