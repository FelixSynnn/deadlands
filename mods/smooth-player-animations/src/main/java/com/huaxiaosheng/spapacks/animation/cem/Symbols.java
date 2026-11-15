package com.huaxiaosheng.spapacks.animation.cem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Symbols {
    private final Map<String, Integer> slots = new HashMap<>();
    private final List<String> names = new ArrayList<>();
    private boolean frozen;

    public int slot(String name) {
        Integer slot = this.slots.get(name);
        if (slot == null) {
            if (this.frozen) {
                throw new IllegalStateException("Symbol table is frozen; unknown name " + name);
            }

            slot = this.slots.size();
            this.slots.put(name, slot);
            this.names.add(name);
        }

        return slot;
    }

    public int find(String name) {
        Integer slot = this.slots.get(name);
        return slot == null ? -1 : slot;
    }

    public String nameOf(int slot) {
        return this.names.get(slot);
    }

    public int size() {
        return this.slots.size();
    }

    public void freeze() {
        this.frozen = true;
    }
}
