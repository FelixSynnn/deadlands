package com.huaxiaosheng.spapacks.animation;

import java.util.Map;

public final class ClipStore<T> {
    public static final String NAMESPACE = "spa_packs";
    private volatile Map<String, T> clips = Map.of();

    public void set(Map<String, T> loaded) {
        this.clips = Map.copyOf(loaded);
    }

    public T get(String id) {
        return id == null ? null : this.clips.get(id.indexOf(58) < 0 ? "spa_packs:" + id : id);
    }

    public int size() {
        return this.clips.size();
    }
}
