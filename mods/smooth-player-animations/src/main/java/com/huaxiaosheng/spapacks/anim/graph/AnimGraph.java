package com.huaxiaosheng.spapacks.anim.graph;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class AnimGraph {
    final int slotCount;
    final float[] variables;
    final Environment[] environments;
    final int defaultEnvironment;
    final String[] slots;
    final Action[][] actions;
    final int nodeCount;
    final int[] nodeSizes;
    final Procedural procedural;
    private final Map<String, List<String[]>> fallbacks;
    private final Map<String, List<String[]>> aliases;
    private final List<String> clipNames;
    final String[] externalNames;
    final int[] externalSlots;

    AnimGraph(
        int slotCount,
        float[] variables,
        Environment[] environments,
        int defaultEnvironment,
        String[] slots,
        Action[][] actions,
        int nodeCount,
        int[] nodeSizes,
        Procedural procedural,
        Map<String, List<String[]>> fallbacks,
        Map<String, List<String[]>> aliases,
        List<String> clipNames,
        String[] externalNames,
        int[] externalSlots
    ) {
        this.externalNames = externalNames;
        this.externalSlots = externalSlots;
        this.aliases = aliases;
        this.clipNames = clipNames;
        this.slotCount = slotCount;
        this.variables = variables;
        this.environments = environments;
        this.defaultEnvironment = defaultEnvironment;
        this.slots = slots;
        this.actions = actions;
        this.nodeCount = nodeCount;
        this.nodeSizes = nodeSizes;
        this.procedural = procedural;
        this.fallbacks = fallbacks;
    }

    public List<String> clipNames() {
        return this.clipNames;
    }

    public List<String> externalNames() {
        return List.of(this.externalNames);
    }

    public Procedural procedural() {
        return this.procedural;
    }

    public String resolve(String backend, String name, Predicate<String> exists) {
        String current = name;

        for (String[] rule : this.aliases.getOrDefault(backend, List.of())) {
            if (current != null && matches(rule[0], current)) {
                current = rule[1];
                break;
            }
        }

        int depth = 0;

        label37:
        while (depth < 8 && current != null) {
            if (!current.startsWith("@") && !exists.test(current)) {
                String next = null;
                Iterator var7 = this.fallbacks.getOrDefault(backend, List.of()).iterator();

                while (true) {
                    if (var7.hasNext()) {
                        String[] rule = (String[])var7.next();
                        if (!matches(rule[0], current)) {
                            continue;
                        }

                        next = rule[1];
                    }

                    current = next;
                    depth++;
                    continue label37;
                }
            }

            return current;
        }

        return null;
    }

    static boolean matches(String pattern, String name) {
        int star = pattern.indexOf(42);
        if (star < 0) {
            return pattern.equals(name);
        }

        String head = pattern.substring(0, star);
        String tail = pattern.substring(star + 1);
        return name.length() >= head.length() + tail.length() && name.startsWith(head) && name.endsWith(tail);
    }
}
