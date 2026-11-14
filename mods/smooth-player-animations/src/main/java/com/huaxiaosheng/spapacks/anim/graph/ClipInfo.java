package com.huaxiaosheng.spapacks.anim.graph;

public interface ClipInfo {
    default String backendId() {
        return "";
    }

    String resolve(String var1);

    double seconds(String var1);

    boolean loops(String var1);

    default boolean holds(String resolved) {
        return false;
    }

    default double stride(String resolved) {
        return 0.0;
    }
}
