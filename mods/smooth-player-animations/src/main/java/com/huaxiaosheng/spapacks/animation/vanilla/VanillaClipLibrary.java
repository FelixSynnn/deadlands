package com.huaxiaosheng.spapacks.animation.vanilla;

import com.huaxiaosheng.spapacks.animation.ClipStore;
import java.util.Map;

public final class VanillaClipLibrary {
    private static final ClipStore<VanillaClip> CLIPS = new ClipStore<>();

    private VanillaClipLibrary() {
    }

    public static void set(Map<String, VanillaClip> loaded) {
        CLIPS.set(loaded);
    }

    public static VanillaClip get(String id) {
        return CLIPS.get(id);
    }

    public static int size() {
        return CLIPS.size();
    }
}
