package com.huaxiaosheng.spapacks.anim.signal;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ExternalVars {
    private static final Pattern NAME = Pattern.compile("[a-z0-9_]+(\\.[a-z0-9_]+)*");
    private final Map<String, Float> values = new HashMap<>();
    private final Set<String> fired = new HashSet<>();
    private final Set<String> showing = new HashSet<>();

    public static String check(String name) {
        if (name != null && NAME.matcher(name).matches()) {
            return name;
        } else {
            throw new IllegalArgumentException("external name \"" + name + "\": lowercase words joined by dots, like combat.combo");
        }
    }

    public void set(String name, float value) {
        if (value == 0.0F) {
            this.values.remove(check(name));
        } else {
            this.values.put(check(name), value);
        }
    }

    public float get(String name) {
        if (this.showing.contains(name)) {
            return 1.0F;
        }

        Float v = this.values.get(name);
        return v == null ? 0.0F : v;
    }

    public void fire(String name) {
        this.fired.add(check(name));
    }

    public void clear(String prefix) {
        check(prefix);
        String dotted = prefix + ".";
        this.values.keySet().removeIf(n -> n.equals(prefix) || n.startsWith(dotted));
        this.fired.removeIf(n -> n.equals(prefix) || n.startsWith(dotted));
    }

    public void beginFrame() {
        this.showing.clear();
        this.showing.addAll(this.fired);
        this.fired.clear();
    }
}
