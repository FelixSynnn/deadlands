package com.huaxiaosheng.spapacks.animation;

import java.util.Map;

public final class ClipLibrary {
    private static final ClipStore<Clip> CLIPS = new ClipStore<>();

    private ClipLibrary() {
    }

    public static void set(Map<String, Clip> loaded) {
        CLIPS.set(loaded);
    }

    public static Clip get(String id) {
        return CLIPS.get(id);
    }

    public static int size() {
        return CLIPS.size();
    }
}
